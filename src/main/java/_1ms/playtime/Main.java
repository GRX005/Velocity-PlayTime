/*      This file is part of the Velocity Playtime project.
        Copyright (C) 2024-2025 _1ms

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <https://www.gnu.org/licenses/>. */

package _1ms.playtime;

import _1ms.BuildConstants;
import _1ms.playtime.Commands.ConfigReload;
import _1ms.playtime.Commands.PlaytimeCommand;
import _1ms.playtime.Commands.PlaytimeResetAll;
import _1ms.playtime.Commands.PlaytimeTopCommand;
import _1ms.playtime.Handlers.CacheHandler;
import _1ms.playtime.Handlers.ConfigHandler;
import _1ms.playtime.Handlers.MySQLHandler;
import _1ms.playtime.Handlers.UpdateHandler;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
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
    public ConfigReload configReload;
    public UpdateHandler updateHandler;
    public PlaytimeResetAll playtimeResetAll;
    public MySQLHandler mySQLHandler;
    public final MinecraftChannelIdentifier MCI = MinecraftChannelIdentifier.from("velocity:playtime");

    private final HashMap<String, SpamData> spamH = new HashMap<>();
    public record SpamData(boolean isTop, long time){}

    public void InitInstances() {
        configHandler = new ConfigHandler(this);
        mySQLHandler = new MySQLHandler(configHandler, this);
        cacheHandler = new CacheHandler(this, configHandler);
        playtimeCommand = new PlaytimeCommand(this, configHandler, cacheHandler);
        playtimeEvents = new PlaytimeEvents(this, configHandler);
        playtimeTopCommand = new PlaytimeTopCommand(this, configHandler);
        requestHandler = new RequestHandler(this, playtimeTopCommand, configHandler);
        configReload = new ConfigReload(configHandler);
        updateHandler = new UpdateHandler(this);
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
        InitInstances();

        configHandler.initConfig(dataDirectory);
    }

    public boolean loadDB() {
        if(mySQLHandler.conn != null) //For reload
            mySQLHandler.closeConnection();
        else
            new org.mariadb.jdbc.Driver(); //IF first time, init driver
        logger.info("Connecting to the database...");
        if(!mySQLHandler.openConnection())
            return false;
        try { //Verify conn if it didn't fail.
            if(mySQLHandler.conn.isValid(2)) //Test conn, wait 2s
                logger.info("Successfully connected to the database.");
            else {
                logger.error("Error while verifying the database connection.");
                return false;
            }
        } catch (SQLException e) {
            logger.error("Failed connecting to the database after initial connection succeeded: {}", e.getMessage());
            return false;
        }
        return true;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
//        if(updateHandler.checkForUpdates())
//            return;
        if(configHandler.isDATABASE() && !loadDB()) {//Check for db here and connect, return if it failed to conn.
            logger.error("The plugin couldn't load.");
            return;
        }
        if(configHandler.isCHECK_FOR_UPDATES())//Using a virtual thread is likely better than velocity's scheduler for a little upd check?
            Thread.ofVirtual().start(updateHandler::checkForUpdates);

        proxy.getChannelRegistrar().register(MCI);
//Convert old data cfgs from ms to sec. If isDataFileUpToDate has been set to true, then the plugin already ran, so there is pt, otherwise it's already been converted or never been ran.
        if(configHandler.isDataFileUpToDate()) {
            getLogger().info("Converting playtime from ms to seconds...");
            getIterator().forEachRemaining(c -> savePt(c, getSavedPt(c) / 1000));
            configHandler.modifyMainConfig("isDataFileUpToDate", false);
        }

        if(configHandler.isUSE_CACHE()) {
            cacheHandler.buildCache();
            proxy.getScheduler().buildTask(this, () -> {
                cacheHandler.updateCache();
                spamH.entrySet().removeIf(entry -> (System.currentTimeMillis() - entry.getValue().time()) > configHandler.getSPAM_LIMIT() + 5000); //Clear hashmap of unneeded values
            }).repeat(configHandler.getCACHE_UPDATE_INTERVAL(), TimeUnit.MILLISECONDS).schedule();
        }

        if(configHandler.isBSTATS()) {
            final Metrics metrics = metricsFactory.make(this, 22432);

            metrics.addCustomChart(new SimplePie("uses_cache", () -> configHandler.isUSE_CACHE() ? "true" : "false"));
            metrics.addCustomChart(new SimplePie("perms_usage", () -> configHandler.getPermsUsageCount()));
            metrics.addCustomChart(new SimplePie("cachegen_time", () -> cacheHandler.getCacheGenTime() + " ms"));
            metrics.addCustomChart(new SimplePie("cacheupdate_interval", () -> String.valueOf(configHandler.getCACHE_UPDATE_INTERVAL())));
            metrics.addCustomChart(new SimplePie("autoupdater", () -> String.valueOf(configHandler.isBSTATS())));
            metrics.addCustomChart(new SimplePie("toplistLimit", () -> String.valueOf(configHandler.getTOPLIST_LIMIT())));
            metrics.addCustomChart(new SimplePie("cfgserializer", () -> String.valueOf(configHandler.isMinimessage())));
            metrics.addCustomChart(new SimplePie("data_method", () -> configHandler.isDATABASE() ? "database" : "ymlfile"));
            metrics.addCustomChart(new SimplePie("anti_spam", () -> String.valueOf(configHandler.getSPAM_LIMIT())));
            metrics.addCustomChart(new SimplePie("rewards", () -> String.valueOf(configHandler.getRewardsH().size())));
            metrics.addCustomChart(new SimplePie("preload", () -> String.valueOf(configHandler.isPRELOAD_PLACEHOLDERS())));
            metrics.addCustomChart(new SimplePie("offlinerewards", () -> String.valueOf(configHandler.isOFFLINES_SHOULD_GET_REWARDS())));
        }
//Register events
        proxy.getEventManager().register(this, playtimeEvents);
        proxy.getEventManager().register(this, requestHandler);
//Start counting PT
        proxy.getScheduler()
                .buildTask(this, this::countPT)
                .repeat(1L, TimeUnit.SECONDS)
                .schedule();
//Register cmds
        CommandManager commandManager = proxy.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder(configHandler.getPTN())
                .aliases(configHandler.getPTA())
                .plugin(this)
                .build();
        SimpleCommand simpleCommand = playtimeCommand;
        commandManager.register(commandMeta, simpleCommand);

        CommandMeta commandMeta2 = commandManager.metaBuilder(configHandler.getPTTN())
                .aliases(configHandler.getPTTA())
                .plugin(this)
                .build();
        SimpleCommand simpleCommand2 = playtimeTopCommand;
        commandManager.register(commandMeta2, simpleCommand2);

        CommandMeta commandMeta3 = commandManager.metaBuilder(configHandler.getPTRLN())
                .aliases(configHandler.getPTRLA())
                .plugin(this)
                .build();
        SimpleCommand simpleCommand3 = configReload;
        commandManager.register(commandMeta3, simpleCommand3);

        CommandMeta commandMeta4 = commandManager.metaBuilder(configHandler.getPTRAN())
                .aliases(configHandler.getPTRAA())
                .plugin(this)
                .build();
        SimpleCommand simpleCommand4 = playtimeResetAll;
        commandManager.register(commandMeta4, simpleCommand4);
//Send restart packet to PlaytimeLink.
        requestHandler.sendRS();

        logger.info("Velocity PlayTime Loaded.");
    }

    void countPT() {//Playtime counting happens here.
        proxy.getAllPlayers().forEach(p->{
            long pt;
            String pname = p.getUsername();
            try {
                pt = playtimeCache.get(pname);
            } catch (Exception e) {
                return;
            }
            for(String srv : configHandler.getExcludedSrvs()) { //Don't count if the player is on an excluded srv.
                final var Csrv = p.getCurrentServer();
                if (Csrv.isPresent() && Objects.equals(Csrv.get().getServerInfo().getName(), srv))
                    return;
            }
            playtimeCache.replace(pname, pt+1L);
            configHandler.getRewardsH().forEach((key, val) -> { //And rewards
                try {
                    if(Objects.equals(key, pt) && !proxy.getPlayer(pname).orElseThrow().hasPermission("vpt.rewards.exempt"))
                        proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), val.replace("%player%", pname));
                } catch (Exception ignored) {}
            });
        });
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
        if(target.length == 0) {
            for (Player player : proxy.getAllPlayers()) {
                if (!player.equals(sender))
                    tabargs.add(player.getUsername());
            }
            return tabargs;
        }
        if(target.length == 1) {
            for (Player player : proxy.getAllPlayers()) {
                if (!player.equals(sender) && player.getUsername().toLowerCase().startsWith(target[0].toLowerCase())) {
                    tabargs.add(player.getUsername());
                }
            }
        }
        return tabargs;
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

    public Iterator<String> getIterator() {
        return configHandler.isDATABASE() ? mySQLHandler.getIterator() : configHandler.getConfigIterator("Player-Data", true);
    }

    public boolean checkSpam(boolean isTop, CommandSource sender) {
        final long currT = System.currentTimeMillis();
        if(!(configHandler.getSPAM_LIMIT() < 1) && sender instanceof Player player && !player.hasPermission("vpt.spam")) {
            final String name = player.getUsername();
            if(spamH.containsKey(name) && spamH.get(name).isTop() == isTop) {
                final long diffT = currT - spamH.get(name).time();
                if(diffT < configHandler.getSPAM_LIMIT()) {
                    final String msg = configHandler.getNO_SPAM().replace("%seconds%", String.valueOf(((configHandler.getSPAM_LIMIT()-diffT)/1000)+1));
                    sender.sendMessage(configHandler.decideNonComponent(msg));
                    return true;
                }
            }
            spamH.put(name, new SpamData(isTop, currT));
        }
        return false;
    }

    public long calculatePlayTime(long rawValue, char v) {
        return switch (v) {
            case 'w' -> rawValue / 604800;
            case 'd' -> (rawValue % 604800) / 86400;
            case 'h' -> ((rawValue % 604800) % 86400) / 3600;
            case 'm' -> (((rawValue % 604000) % 86400) % 3600) / 60;
            case 's' -> ((((rawValue % 604800) % 86400) % 3600) % 60);
            default -> -1;
        };
    }

    public long calcTotalPT(long rawValue, char v) {
        return switch (v) {
            case 'd' -> rawValue / 86400;
            case 'h' -> rawValue / 3600;
            case 'm' -> rawValue / 60;
            case 's' -> rawValue;
            default -> -1;
        };
    }

    public int getPlace(String pName) {//Check if the cache contains it.
        LinkedHashMap<String, Long> tl;
        if(playtimeCache.containsKey(pName)) {
            tl = doSort(null);
            if ((long) tl.values().toArray()[tl.size() - 1] > playtimeCache.get(pName))
                tl = requestHandler.getFullTL();
        }
        else
            tl = requestHandler.getFullTL();
        int i = 0;
        for(String pl : tl.keySet()) {
            i++;
            if(pName.equals(pl))
                return i;
        }
        return -1;
    }

    public LinkedHashMap<String, Long> doSort(@Nullable SimpleCommand.Invocation invocation) {
        final HashMap<String, Long> TempCache = configHandler.isUSE_CACHE() ? cacheHandler.generateTempCache() : playtimeTopCommand.getInRuntime();
        final boolean isForPlaceholder = invocation == null;
        final LinkedHashMap<String, Long> placeholderH  = new LinkedHashMap<>();
        if(!isForPlaceholder)
            invocation.source().sendMessage(configHandler.getTOP_PLAYTIME_HEADER());
        int in = 0;
        for(int i = 0; i < configHandler.getTOPLIST_LIMIT(); i++) {
            in++;
            Optional<Map.Entry<String, Long>> member = TempCache != null ? TempCache.entrySet().stream().max(Map.Entry.comparingByValue()) : Optional.empty();
            if(member.isEmpty())
                break;
            Map.Entry<String, Long> Entry = member.get();
            long playTime = Entry.getValue();
            if(playTime == 0)
                continue;

            if(isForPlaceholder)
                placeholderH.put(Entry.getKey(), playTime);
            else
                invocation.source().sendMessage(configHandler.decideNonComponent(configHandler.repL(configHandler.getTOP_PLAYTIME_LIST(), playTime).replace("%player%", Entry.getKey()).replace("%place%", String.valueOf(in))));
            TempCache.remove(Entry.getKey());
        }
        if(!isForPlaceholder)
            invocation.source().sendMessage(configHandler.getTOP_PLAYTIME_FOOTER());
        return placeholderH;
    }

}
