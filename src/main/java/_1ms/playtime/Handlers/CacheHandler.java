package _1ms.playtime.Handlers;

import _1ms.playtime.Main;
import com.velocitypowered.api.proxy.Player;
import lombok.Getter;

import java.util.*;

public class CacheHandler {
    private final Main main;
    private final ConfigHandler configHandler;
    @Getter
    private long cacheGenTime;

    public CacheHandler(Main main, ConfigHandler configHandler) {
        this.main = main;
        this.configHandler = configHandler;
    }
    public void buildCache() {
        Iterator<Object> iterator = configHandler.getConfigIterator("Player-Data", true);
        if(iterator == null)
            return;
        long start = System.currentTimeMillis();
        main.getLogger().info("Building Cache...");
        final HashMap<String, Long> TempCache = new HashMap<>();
        while (iterator.hasNext()) {
            String Pname = iterator.next().toString();
            long Ptime = configHandler.getPtFromConfig(Pname);
            TempCache.put(Pname, Ptime);
            iterator.remove();
        }

        for(int i = 0; i < configHandler.getTOPLIST_LIMIT(); i++) {
            Optional<Map.Entry<String, Long>> member = TempCache.entrySet().stream().max(Map.Entry.comparingByValue());
            if(member.isEmpty())
                break;
            Map.Entry<String, Long> Entry = member.get();
            main.playtimeCache.put(Entry.getKey(), Entry.getValue());
            TempCache.remove(Entry.getKey());
        }
        main.getLogger().info("The cache has been built, took: {} ms", cacheGenTime = (System.currentTimeMillis() - start) + configHandler.getGenTime());
    }

    public HashMap<String, Long> generateTempCache() {
        return new HashMap<>(main.playtimeCache);
    }

    public void upd2(String keyToRemove) {
        Iterator<Object> iterator = configHandler.getConfigIterator("Player-Data", true);
        if(iterator == null)
            return;
        final HashMap<String, Long> TempCache = new HashMap<>();
        while (iterator.hasNext()) {
            String Pname = iterator.next().toString();
            if(Objects.equals(Pname, keyToRemove))
                continue;
            long Ptime = configHandler.getPtFromConfig(Pname);
            TempCache.put(Pname, Ptime);
            iterator.remove();
        }
        main.playtimeCache.remove(keyToRemove);

        Optional<Map.Entry<String, Long>> nextLargest = TempCache.entrySet().stream()
                .filter(entry -> !main.playtimeCache.containsKey(entry.getKey()))
                .max(Map.Entry.comparingByValue());

        nextLargest.ifPresent(entry -> main.playtimeCache.put(entry.getKey(), entry.getValue()));
    }

    public void updateCache() {
        HashMap<String, Long> TempCache = generateTempCache();
        for (int i = 0; i < main.playtimeCache.size() - configHandler.getTOPLIST_LIMIT(); i++) {
            Optional<Map.Entry<String, Long>> member = TempCache.entrySet().stream().min(Map.Entry.comparingByValue());
            member.ifPresent(Entry -> {
                Optional<Player> player = main.getProxy().getPlayer(Entry.getKey());
                if(player.isEmpty()) {
                    main.playtimeCache.remove(Entry.getKey());
                }
                TempCache.remove(Entry.getKey());
            });
        }
    }
}
