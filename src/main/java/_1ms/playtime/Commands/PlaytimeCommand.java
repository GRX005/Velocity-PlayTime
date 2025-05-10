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

package _1ms.playtime.Commands;

import _1ms.playtime.Handlers.CacheHandler;
import _1ms.playtime.Handlers.ConfigHandler;
import _1ms.playtime.Main;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlaytimeCommand implements SimpleCommand {
    private final Main main;
    private final ConfigHandler configHandler;
    private final CacheHandler cacheHandler;

    public PlaytimeCommand(Main main, ConfigHandler configHandler, CacheHandler cacheHandler) {
        this.main = main;
        this.configHandler = configHandler;
        this.cacheHandler = cacheHandler;
    }

    @Override
    public void execute(Invocation invocation) {
        final String[] args = invocation.arguments();
        final CommandSource sender = invocation.source();
        if(main.checkSpam(false, sender))
           return;
        switch (args.length) {
            case 0 -> {
                if(!(sender instanceof Player player)) {
                    sender.sendMessage(configHandler.getNO_CONSOLE_USE());
                    return;
                }
                SendYourPlaytime(player);
            }
            case 1 -> {
                if(sender instanceof Player player)
                    if(player.getUsername().equalsIgnoreCase(args[0])) {
                        SendYourPlaytime(player);
                        return;
                    }
                if(configHandler.isVIEW_OTHERS_TIME() && !sender.hasPermission("vpt.getotherstime")) {
                    sender.sendMessage(configHandler.getNO_PERMISSION());
                    return;
                }
                long PlayTime = main.playtimeCache.containsKey(args[0]) ? main.playtimeCache.get(args[0]) : main.getSavedPt(args[0]);
                if (PlayTime == -1)
                    sender.sendMessage(configHandler.getNO_PLAYER());
                else
                    sender.sendMessage(configHandler.decideNonComponent(configHandler.repL(configHandler.getOTHER_PLAYTIME(), PlayTime).replace("%player%", args[0]).replace("%place%", String.valueOf(main.getPlace(args[0])))));
            }
            case 3 -> {
                if(!sender.hasPermission("vpt.modify")) {
                    sender.sendMessage(configHandler.getNO_PERMISSION());
                    return;
                }
                long num;
                try {
                    num = Long.parseLong(args[2]);
                    if(num < 0)
                        throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage(configHandler.getINVALID_VALUE());
                    return;
                }
                switch (args[0]) {
                    case "add" -> {//pt add %player% %time%
                        long val;
                        long beforePt;
                        if(main.playtimeCache.containsKey(args[1])) {//In cache
                            beforePt = main.playtimeCache.get(args[1]);
                            val = beforePt+num;
                            main.playtimeCache.replace(args[1], val);
                            if(main.getProxy().getPlayer(args[1]).isEmpty()) //Save if player ain't on.
                                main.savePt(args[1], val);
                        } else {
                            beforePt = main.getSavedPt(args[1]);//Config if not
                            if(beforePt == -1) {
                                sender.sendMessage(configHandler.getNO_PLAYER());
                                return;
                            }
                            val = beforePt+num;
                            main.savePt(args[1], val);
                            var tempMap = cacheHandler.generateTempCache();
                            if((long)tempMap.values().toArray()[tempMap.size()-1] < val) { //Put in cache if last val in it smaller than the added PT, the clearer will kill it later.
                                main.playtimeCache.put(args[1], val);
                            }
                        }
                        checkRewards(args[1], beforePt, val);
                        sendModMsg(val, sender, args[1]);
                    }
                    case "sub" -> {
                        long val;
                        if(main.playtimeCache.containsKey(args[1])) {//In cache
                            val = main.playtimeCache.get(args[1])-num;
                            if(val < 0){
                                sender.sendMessage(configHandler.getINVALID_VALUE());
                                return;
                            }
                            handleCache(args[1], val);
                            if(main.getProxy().getPlayer(args[1]).isEmpty())
                                main.savePt(args[1], val);
                        } else {
                            final long pt = main.getSavedPt(args[1]);//Config if not
                            if(pt == -1) {
                                sender.sendMessage(configHandler.getNO_PLAYER());
                                return;
                            }
                            val = pt-num;
                            if(val < 0){
                                sender.sendMessage(configHandler.getINVALID_VALUE());
                                return;
                            }
                            main.savePt(args[1], val);
                        }
                        sendModMsg(val, sender, args[1]);
                    }
                    case "set" -> {
                        long beforePt;
                        if(main.playtimeCache.containsKey(args[1])) {//In cache
                            beforePt = main.playtimeCache.get(args[1]);
                            handleCache(args[1], num);
                            if(main.getProxy().getPlayer(args[1]).isEmpty())
                                main.savePt(args[1],num);
                        }
                        else {
                            beforePt = main.getSavedPt(args[1]);
                            if(beforePt == -1)
                                beforePt = 0;
                            main.savePt(args[1], num);
                        }
                        if(beforePt < num)
                            checkRewards(args[1], beforePt, num);
                        sendModMsg(num, sender, args[1]);
                    }
                }
            }
            default -> sender.sendMessage(configHandler.getINVALID_ARGS());
        }
    }

    private void checkRewards(String pname, long beforePt, long afterPt) {
        configHandler.getRewardsH().forEach((key, value) -> { //Check rewards on PT ADD
            try {
                if(beforePt < key && key <= afterPt && !main.getProxy().getPlayer(pname).orElseThrow().hasPermission("vpt.rewards.exempt")) {
                    delay();//We need to delay by 100ms, otherwise lp will delay them, but execute in the wrong order.
                    main.getProxy().getCommandManager().executeAsync(main.getProxy().getConsoleCommandSource(), value.replace("%player%", pname));
                }
            } catch (Exception ignored) {
                if(configHandler.isOFFLINES_SHOULD_GET_REWARDS()) {
                    delay();
                    main.getProxy().getCommandManager().executeAsync(main.getProxy().getConsoleCommandSource(), value.replace("%player%", pname));
                }
            }
        });
    }

    private void delay() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException("Error while delaying rewards cmd.", e);
        }
    }

    private void sendModMsg(long val, CommandSource sender, String target) {
        if(sender instanceof Player)//Only send if it isn't console so automation can be done without the msgs.
            sender.sendMessage(configHandler.decideNonComponent(configHandler.getPTSET().replace("%sec%", String.valueOf(val)).replace("%player%", target)));
    }

    public void handleCache(final String pname, final long val) {
        final LinkedHashMap<String, Long> tempMap = main.doSort(null);
        main.playtimeCache.replace(pname, val);
        if((long)tempMap.values().toArray()[tempMap.size()-1] > val)
            cacheHandler.upd2(pname);
    }
    public void SendYourPlaytime(Player player) {
        if (configHandler.isVIEW_OWN_TIME() && !player.hasPermission("vpt.getowntime")) {
            player.sendMessage(configHandler.getNO_PERMISSION());
            return;
        }
        final long PlayTime = main.playtimeCache.get(player.getUsername());
        player.sendMessage(configHandler.decideNonComponent(configHandler.repL(configHandler.getYOUR_PLAYTIME(), PlayTime).replace("%place%", String.valueOf(main.getPlace(player.getUsername())))));
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        final CommandSource sender = invocation.source();
        if(configHandler.isVIEW_OTHERS_TIME() && !sender.hasPermission("vpt.getotherstime"))
            return CompletableFuture.completedFuture(new ArrayList<>());
        final List<String> tabargs = new ArrayList<>();
        final String[] target = invocation.arguments();
        if(target.length == 0) {
            for (Player player : main.getProxy().getAllPlayers())
                if (!player.equals(sender))
                    tabargs.add(player.getUsername());
            return CompletableFuture.completedFuture(tabargs);
        }
        if (target.length == 1) {
            // First argument tab completion
            final String prefix = target[0].toLowerCase();
            if(sender.hasPermission("vpt.modify")){
                if ("add".startsWith(prefix)) tabargs.add("add");
                if ("sub".startsWith(prefix)) tabargs.add("sub");
                if ("set".startsWith(prefix)) tabargs.add("set");
            }
            for (Player player : main.getProxy().getAllPlayers())
                if (player.getUsername().toLowerCase().startsWith(prefix))
                    tabargs.add(player.getUsername());
        } else if (target.length == 2 && (target[0].equalsIgnoreCase("add") || target[0].equalsIgnoreCase("sub") || target[0].equalsIgnoreCase("set")) && sender.hasPermission("vpt.modify")) {
            // Second argument tab completion for specific first arguments
            for (Player player : main.getProxy().getAllPlayers())
                if (player.getUsername().toLowerCase().startsWith(target[1].toLowerCase()))
                    tabargs.add(player.getUsername());
        }
        return CompletableFuture.completedFuture(tabargs);
    }
}
