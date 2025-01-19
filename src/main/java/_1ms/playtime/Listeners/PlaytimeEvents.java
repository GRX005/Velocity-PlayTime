package _1ms.playtime.Listeners;

import _1ms.playtime.Handlers.ConfigHandler;
import _1ms.playtime.Main;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;

@SuppressWarnings("unused")
public class PlaytimeEvents {
    private final Main main;
    private final ConfigHandler configHandler;

    public PlaytimeEvents(Main main, ConfigHandler configHandler) {
        this.main = main;
        this.configHandler = configHandler;
    }
    @Subscribe
    public EventTask onConnect(PostLoginEvent e) {
        return EventTask.async(() -> {
            String playerName = e.getPlayer().getUsername();
            if(!main.playtimeCache.containsKey(playerName)) {
                long playtime = main.getSavedPt(playerName);
                if (playtime == -1) {
                    main.playtimeCache.put(playerName, 0L);
                    return;
                }
                main.playtimeCache.put(playerName, playtime);
            }
        });
    }

    @Subscribe
    public EventTask onLeave(DisconnectEvent e) {
        return EventTask.async(() -> {
            final String playerName = e.getPlayer().getUsername();
            final long playerTime = main.getPlayTime(playerName);
            main.savePt(playerName, playerTime);
            if(!configHandler.isUSE_CACHE())
                main.playtimeCache.remove(playerName);
        });
    }

    @Subscribe
    @SuppressWarnings("UnstableApiUsage")
    public EventTask onTabComplete(PlayerAvailableCommandsEvent e) {
        return EventTask.async(() -> {
            Player player = e.getPlayer();
            if(configHandler.isVIEW_OWN_TIME() && configHandler.isVIEW_OTHERS_TIME() && !player.hasPermission("vpt.getowntime") && !player.hasPermission("vpt.getotherstime")) {
                e.getRootNode().removeChildByName("playtime");
                e.getRootNode().removeChildByName("pt");
            }
            if(configHandler.isVIEW_TOPLIST() && !player.hasPermission("vpt.gettoplist")) {
                e.getRootNode().removeChildByName("playtimetop");
                e.getRootNode().removeChildByName("pttop");
                e.getRootNode().removeChildByName("ptt");
            }
            if(!player.hasPermission("vpt.reload")) {
                e.getRootNode().removeChildByName("playtimereload");
                e.getRootNode().removeChildByName("ptrl");
                e.getRootNode().removeChildByName("ptreload");
            }
            if(!player.hasPermission("vpt.ptreset")) {
                e.getRootNode().removeChildByName("playtimereset");
                e.getRootNode().removeChildByName("ptr");
                e.getRootNode().removeChildByName("ptreset");
            }
            if(!player.hasPermission("vpt.ptresetall")) {
                e.getRootNode().removeChildByName("playtimeresetall");
                e.getRootNode().removeChildByName("ptra");
                e.getRootNode().removeChildByName("ptresetall");
            }
        });
    }

    //Cachee, unnecessary to check after every player leave + unstable probably
//        HashMap<String, Long> TempCache = cacheHandler.generateTempCache();
//        for (int i = 0; i < velocityPlaytime.getProxy().getAllPlayers().size() + 1; i++) {
//            Optional<Map.Entry<String, Long>> member = TempCache.entrySet().stream().min(Map.Entry.comparingByValue());
//            if (member.isEmpty())
//                break;
//            Map.Entry<String, Long> Entry = member.get();
//            if (PlayTime < Entry.getValue()) {
//                velocityPlaytime.playtimeTopCache.remove(playerName);
//                velocityPlaytime.getDataConfig().update();
//                velocityPlaytime.getDataConfig().save();
//                break;
//            }
//        }
}
