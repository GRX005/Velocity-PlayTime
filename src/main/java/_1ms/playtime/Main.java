package _1ms.playtime;

import _1ms.BuildConstants;
import _1ms.playtime.Commands.*;
import _1ms.playtime.Handlers.*;
import _1ms.playtime.Listeners.PlaytimeEvents;
import _1ms.playtime.Listeners.RequestHandler;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Getter;
import org.bstats.charts.SimplePie;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "velocityplaytime",
        name = "VelocityPlaytime",
        version = BuildConstants.VERSION,
        authors = "_1ms",
        description = "Playtime logger for velocity"
)
@SuppressWarnings("unused")
public class Main {
    public ConfigHandler configHandler;
    public PlaytimeCommand playtimeCommand;
    public CacheHandler cacheHandler;
    public PlaytimeEvents playtimeEvents;
    public PlaytimeTopCommand playtimeTopCommand;
    public RequestHandler requestHandler;
    public DataConverter dataConverter;
    public ConfigReload configReload;
    public UpdateHandler updateHandler;
    public PlaytimeReset playtimeReset;
    public PlaytimeResetAll playtimeResetAll;
    public MySQLHandler mySQLHandler;
    public final MinecraftChannelIdentifier MCI = MinecraftChannelIdentifier.from("velocity:playtime");

    public void InitInstance() {
        configHandler = new ConfigHandler(this);
        mySQLHandler = new MySQLHandler(configHandler);
        playtimeCommand = new PlaytimeCommand(this, configHandler);
        cacheHandler = new CacheHandler(this, configHandler);
        playtimeEvents = new PlaytimeEvents(this, configHandler);
        playtimeTopCommand = new PlaytimeTopCommand(this, cacheHandler, configHandler);
        requestHandler = new RequestHandler(this, playtimeTopCommand, configHandler);
        dataConverter = new DataConverter(this, configHandler);
        configReload = new ConfigReload(configHandler);
        updateHandler = new UpdateHandler(this);
        playtimeReset = new PlaytimeReset(this, configHandler, cacheHandler);
        playtimeResetAll = new PlaytimeResetAll(this, configHandler);
    }

    public final HashMap<String, Long> playtimeCache = new HashMap<>();

    @Getter
    private final Logger logger;

    @Getter
    private final ProxyServer proxy;

    private final Metrics.Factory metricsFactory;

    @Inject
    public Main(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) throws ClassNotFoundException, SQLException {
        this.proxy = proxy;
        this.logger = logger;
        this.metricsFactory = metricsFactory;
        InitInstance();

        configHandler.initConfig(dataDirectory);
        if(configHandler.isDATABASE())
            loadDB();
    }

    public void loadDB() {
        if(mySQLHandler.conn != null)
            mySQLHandler.closeConnection();
        else
            new org.mariadb.jdbc.Driver();
        logger.info("Connecting to the database...");
        mySQLHandler.openConnection();
        try {
            if(mySQLHandler.conn.isValid(2000))
                logger.info("Successfully connected to the database.");
            else
                logger.error("Failed connecting to the database.");
        } catch (SQLException e) {
            throw new RuntimeException("Failed connecting to the database", e);
        }
    }

    private void checkSpamH(HashMap<String, Long> spamH) {
        spamH.entrySet().removeIf(entry -> (System.currentTimeMillis() - entry.getValue()) > configHandler.getSPAM_LIMIT() + 5000);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
//        if(updateHandler.checkForUpdates())
//            return;
        if(configHandler.isCHECK_FOR_UPDATES())
            proxy.getScheduler().buildTask(this, () -> updateHandler.checkForUpdates()).schedule();

        proxy.getChannelRegistrar().register(MCI);

        if(!configHandler.isDataFileUpToDate())
            dataConverter.checkConfig();

        if(configHandler.isUSE_CACHE()) {
            cacheHandler.buildCache();
            proxy.getScheduler().buildTask(this, () -> {
                cacheHandler.updateCache();
                checkSpamH(playtimeTopCommand.spamH);
                checkSpamH(playtimeCommand.spamH);
            }).repeat(configHandler.getCACHE_UPDATE_INTERVAL(), TimeUnit.MILLISECONDS).schedule();
        }

        if(configHandler.isBSTATS()) {
            int pluginID = 22432;
            Metrics metrics = metricsFactory.make(this, pluginID);

            metrics.addCustomChart(new SimplePie("uses_cache", () -> configHandler.isUSE_CACHE() ? "true" : "false"));
            metrics.addCustomChart(new SimplePie("perms_usage", () -> configHandler.getPermsUsageCount()));
            metrics.addCustomChart(new SimplePie("cachegen_time", () -> cacheHandler.getCacheGenTime() + " ms"));
            metrics.addCustomChart(new SimplePie("cacheupdate_interval", () -> String.valueOf(configHandler.getCACHE_UPDATE_INTERVAL())));
            metrics.addCustomChart(new SimplePie("autoupdater", () -> String.valueOf(configHandler.isBSTATS())));
            metrics.addCustomChart(new SimplePie("toplistLimit", () -> String.valueOf(configHandler.getTOPLIST_LIMIT())));
            metrics.addCustomChart(new SimplePie("cfgserializer", () -> String.valueOf(configHandler.isMinimessage())));
            metrics.addCustomChart(new SimplePie("data_method", () -> configHandler.isDATABASE() ? "database" : "ymlfile"));
            metrics.addCustomChart(new SimplePie("anti_spam", () -> String.valueOf(configHandler.getSPAM_LIMIT())));
            metrics.addCustomChart(new SimplePie("rewards", () -> String.valueOf(configHandler.rewardsH.size())));
            metrics.addCustomChart(new SimplePie("preload", () -> String.valueOf(configHandler.isPRELOAD_PLACEHOLDERS())));
        }

        proxy.getEventManager().register(this, playtimeEvents);
        proxy.getEventManager().register(this, requestHandler);

        proxy.getScheduler()
                .buildTask(this, () -> {
                    for(Player player : proxy.getAllPlayers()) {
                        final String name = player.getGameProfile().getName();
                        final long playTime = GetPlayTime(name);
                        playtimeCache.put(name, playTime + 1000L);
                        configHandler.rewardsH.keySet().forEach(key -> {
                            if(key == playTime) {
                                proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), configHandler.rewardsH.get(key).replace("%player%", name));
                            }
                        });
                    }
                })
                .repeat(1L, TimeUnit.SECONDS)
                .schedule();

        CommandManager commandManager = proxy.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("playtime")
                .aliases("pt")
                .plugin(this)
                .build();
        SimpleCommand simpleCommand = playtimeCommand;
        commandManager.register(commandMeta, simpleCommand);

        CommandMeta commandMeta2 = commandManager.metaBuilder("playtimetop")
                .aliases("pttop", "ptt")
                .plugin(this)
                .build();
        SimpleCommand simpleCommand2 = playtimeTopCommand;
        commandManager.register(commandMeta2, simpleCommand2);

        CommandMeta commandMeta3 = commandManager.metaBuilder("playtimereload")
                .aliases("ptrl", "ptreload")
                .plugin(this)
                .build();
        SimpleCommand simpleCommand3 = configReload;
        commandManager.register(commandMeta3, simpleCommand3);

        CommandMeta commandMeta4 = commandManager.metaBuilder("playtimereset")
                .aliases( "ptr", "ptreset")
                .plugin(this)
                .build();
        SimpleCommand simpleCommand4 = playtimeReset;
        commandManager.register(commandMeta4, simpleCommand4);

        CommandMeta commandMeta5 = commandManager.metaBuilder("playtimeresetall")
                .aliases( "ptra", "ptresetall")
                .plugin(this)
                .build();
        SimpleCommand simpleCommand5 = playtimeResetAll;
        commandManager.register(commandMeta5, simpleCommand5);

        requestHandler.sendRS();

        logger.info("Velocity PlayTime Loaded.");
    }

    public CompletableFuture<Boolean> checkServerStatus(RegisteredServer server) {
        return server.ping()
                .thenApply(ping -> true)
                .exceptionally(ex -> false);
    }

    @Subscribe
    public void ProxyShutdownEvent(ProxyShutdownEvent event) {
        mySQLHandler.closeConnection();
        logger.info("Velocity PlayTime Unloaded.");
    }

    public List<String> calcTab(CommandSource sender, String[] target) {
        final List<String> tabargs = new ArrayList<>();
        try {
            for (Player player : proxy.getAllPlayers()) {
                if (!player.equals(sender) && player.getGameProfile().getName().toLowerCase().startsWith(target[0].toLowerCase())) {
                    tabargs.add(player.getGameProfile().getName());
                }
            }
        } catch (Exception ignored) {
            for (Player player : proxy.getAllPlayers()) {
                if (!player.equals(sender)) {
                    tabargs.add(player.getGameProfile().getName());
                }
            }
        }
        return tabargs;
    }

    public long GetPlayTime(String playerName) {
        return playtimeCache.getOrDefault(playerName, 0L);
    }

    public long getSavedPt(String name) {
        return configHandler.isDATABASE() ? mySQLHandler.readData(name) : configHandler.getPtFromConfig(name);
    }

    public void savePt(String name, long time) {
        if(configHandler.isDATABASE()) {
            mySQLHandler.saveData(name, time);
            return;
        }
        configHandler.savePtToConfig(name, time);
    }

    public void removeAllPt() {
        if(configHandler.isDATABASE()) {
            mySQLHandler.deleteAll();
            return;
        }
        configHandler.nullDataConfig();
    }

    public Iterator<Object> getIterator() {
        return configHandler.isDATABASE() ? mySQLHandler.getIterator() : configHandler.getConfigIterator("Player-Data", true);
    }

    public long calculatePlayTime(long rawValue, char v) {
        return switch (v) {
            case 'w' -> rawValue / 604800000;
            case 'd' -> (rawValue % 604800000L) / 86400000;
            case 'h' -> ((rawValue % 604800000L) % 86400000) / 3600000;
            case 'm' -> (((rawValue % 604800000L) % 86400000) % 3600000) / 60000;
            case 's' -> ((((rawValue % 604800000L) % 86400000) % 3600000) % 60000) / 1000;
            default -> -1;
        };
    }

}
