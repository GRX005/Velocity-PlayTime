package _1ms.playtime.Commands;

import _1ms.playtime.Handlers.CacheHandler;
import _1ms.playtime.Handlers.ConfigHandler;
import _1ms.playtime.Main;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlaytimeTopCommand implements SimpleCommand {
    private final Main main;
    private final CacheHandler cacheHandler;
    private final ConfigHandler configHandler;
    public final HashMap<String, Long> spamH = new HashMap<>();

    public PlaytimeTopCommand(Main main, CacheHandler cacheHandler, ConfigHandler configHandler) {
        this.main = main;
        this.cacheHandler = cacheHandler;
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
        final long currT = System.currentTimeMillis();
        if(!(configHandler.getSPAM_LIMIT() < 1) && sender instanceof Player player && !player.hasPermission("vpt.spam")) {
            final String name = player.getUsername();
            if(spamH.containsKey(name)) {
                final long diffT = currT - spamH.get(name);
                if(diffT < configHandler.getSPAM_LIMIT()) {
                    final String msg = configHandler.getNO_SPAM().replace("%seconds%", String.valueOf(((configHandler.getSPAM_LIMIT()-diffT)/1000)+1));
                    sender.sendMessage(configHandler.decideNonComponent(msg));
                    return;
                }
            }
            spamH.put(name, currT);
        }
        doSort(invocation);
    }

    public LinkedHashMap<String, Long> doSort(@Nullable Invocation invocation) {
        final HashMap<String, Long> TempCache = configHandler.isUSE_CACHE() ? cacheHandler.generateTempCache() : getInRuntime();
        final boolean isForPlaceholder = invocation == null;
        final LinkedHashMap<String, Long> placeholderH  = new LinkedHashMap<>();
        if(!isForPlaceholder)
            invocation.source().sendMessage(configHandler.getTOP_PLAYTIME_HEADER());
        for(int i = 0; i < configHandler.getTOPLIST_LIMIT(); i++) {
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
                invocation.source().sendMessage(configHandler.decideNonComponent(configHandler.repL(configHandler.getTOP_PLAYTIME_LIST(), playTime).replace("%player%", Entry.getKey())));
            TempCache.remove(Entry.getKey());
        }
        if(!isForPlaceholder)
            invocation.source().sendMessage(configHandler.getTOP_PLAYTIME_FOOTER());
        return placeholderH;
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
                            TempCache.put(player1.getGameProfile().getName(), Long);
                            TempCache.remove(Entry.getKey());
                        }
                    }else
                        TempCache.put(player1.getGameProfile().getName(), Long);
                }, () -> TempCache.put(player1.getGameProfile().getName(), Long));
            });
        });
        return TempCache;
    }

}
