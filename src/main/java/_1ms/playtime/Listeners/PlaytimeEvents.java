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
        return EventTask.async(() -> { //Note it is async.
            String playerName = e.getPlayer().getUsername();
            if(!main.playtimeCache.containsKey(playerName)) {//Only put if not cached already.
                long playtime = main.getSavedPt(playerName);
                if (playtime == -1) {//Here, first time joined.
                    main.playtimeCache.put(playerName, 0L);
                    return;
                }
                main.playtimeCache.put(playerName, playtime); //LOAD pt from before.
            }
        });
    }

    @Subscribe
    public EventTask onLeave(DisconnectEvent e) {
        return EventTask.async(() -> {
            final String playerName = e.getPlayer().getUsername();
            long playerTime;
            try {//Ret if null, bugfix for when the player leaves too quickly.
                playerTime = main.playtimeCache.get(playerName);
            } catch (Exception ex) {
                return;
            }
            main.savePt(playerName, playerTime);
            if(!configHandler.isUSE_CACHE()) //Rem if caching isnt used, otherwise updateCache task clears it when needed
                main.playtimeCache.remove(playerName);
        });
    }

    @Subscribe
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
