package _1ms.playtime;

import _1ms.BuildConstants;
import _1ms.playtime.Commands.*;
import _1ms.playtime.Handlers.CacheHandler;
import _1ms.playtime.Handlers.ConfigHandler;
import _1ms.playtime.Handlers.DataConverter;
import _1ms.playtime.Handlers.UpdateHandler;
import _1ms.playtime.Listeners.PlaytimeEvents;
import _1ms.playtime.Listeners.RequestHandler;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import lombok.Getter;
import org.bstats.charts.SimplePie;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.HashMap;
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
    public final MinecraftChannelIdentifier MCI = MinecraftChannelIdentifier.from("velocity:playtime");

    public void InitInstance() {
        configHandler = new ConfigHandler(this);
        playtimeCommand = new PlaytimeCommand(this, configHandler);
        cacheHandler = new CacheHandler(this, configHandler);
        playtimeEvents = new PlaytimeEvents(this, configHandler);
        playtimeTopCommand = new PlaytimeTopCommand(this, cacheHandler, configHandler);
        requestHandler = new RequestHandler(this, playtimeTopCommand);
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
    public Main(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.proxy = proxy;
        this.logger = logger;
        this.metricsFactory = metricsFactory;
        InitInstance();

        configHandler.initConfig(dataDirectory);
        configHandler.makeNonChanging();
        configHandler.makeConfigCache();
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
            proxy.getScheduler().buildTask(this, () -> cacheHandler.updateCache()).repeat(configHandler.getCACHE_UPDATE_INTERVAL(), TimeUnit.MILLISECONDS).schedule();
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

        logger.info("Velocity PlayTime Loaded.");
    }

    public long GetPlayTime(String playerName) {
        return playtimeCache.getOrDefault(playerName, 0L);
    }

    public long calculatePlayTime(long rawValue, char v) {
        switch (v) {
            case 'h' -> {
                return rawValue / 3600000;
            }
            case 'm' -> {
                return (rawValue % 3600000) / 60000;
            }
            case 's' -> {
                return ((rawValue % 3600000) % 60000) / 1000;
            }
        }
        logger.error("Error while Calculating Playtime.");
        return 0;
    }

}
