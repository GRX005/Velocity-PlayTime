package _1ms.playtime.Commands;

import _1ms.playtime.Handlers.ConfigHandler;
import _1ms.playtime.Main;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public class PlaytimeTopCommand implements SimpleCommand {
    private final Main main;
    private final ConfigHandler configHandler;

    public PlaytimeTopCommand(Main main, ConfigHandler configHandler) {
        this.main = main;
        this.configHandler = configHandler;
    }

    @Override
    public void execute(Invocation invocation) { //do cooldown
        CommandSource sender = invocation.source();
        if (configHandler.isVIEW_TOPLIST() && !invocation.source().hasPermission("vpt.gettoplist")) {
            sender.sendMessage(configHandler.getNO_PERMISSION());
            return;
        }
        if(invocation.arguments().length > 0) {
            sender.sendMessage(configHandler.getINVALID_ARGS());
            return;
        }
        if(main.checkSpam(true, sender))
            return;
        main.doSort(invocation);
    }

    public HashMap<String, Long> getInRuntime() {
        Iterator<Object> iterator = main.getIterator();
        final HashMap<String, Long> TempCache = new HashMap<>();
        if(iterator != null) {
            while (iterator.hasNext()) {
                String Pname = (String) iterator.next();
                Optional<Player> player = main.getProxy().getPlayer(Pname);
                if (player.isEmpty()) {
                    long Ptime = main.getSavedPt(Pname);
                    TempCache.put(Pname, Ptime);
                }
                iterator.remove();
            }
            while (TempCache.size() > configHandler.getTOPLIST_LIMIT()) {
                Optional<Map.Entry<String, Long>> member = TempCache.entrySet().stream().min(Map.Entry.comparingByValue());
                if (member.isEmpty())
                    break;
                Map.Entry<String, Long> Entry = member.get();
                TempCache.remove(Entry.getKey());
            }
        }

        main.playtimeCache.forEach((String, Long) -> {
            Optional<Player> player = main.getProxy().getPlayer(String);
            player.ifPresent(player1 -> {
                Optional<Map.Entry<String, Long>> ad = TempCache.entrySet().stream().min(Map.Entry.comparingByValue());
                ad.ifPresentOrElse(Entry -> {
                    if(TempCache.size() >= configHandler.getTOPLIST_LIMIT()) {
                        if (Entry.getValue() < Long) {
                            TempCache.put(player1.getUsername(), Long);
                            TempCache.remove(Entry.getKey());
                        }
                    }else
                        TempCache.put(player1.getUsername(), Long);
                }, () -> TempCache.put(player1.getUsername(), Long));
            });
        });
        return TempCache;
    }
}
