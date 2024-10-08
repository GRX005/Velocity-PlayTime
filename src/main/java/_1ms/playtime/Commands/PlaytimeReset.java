package _1ms.playtime.Commands;

import _1ms.playtime.Handlers.CacheHandler;
import _1ms.playtime.Handlers.ConfigHandler;
import _1ms.playtime.Main;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlaytimeReset implements SimpleCommand {
    private final Main main;
    private final ConfigHandler configHandler;
    private final CacheHandler cacheHandler;

    public PlaytimeReset(Main main, ConfigHandler configHandler, CacheHandler cacheHandler) {
        this.main = main;
        this.configHandler = configHandler;
        this.cacheHandler = cacheHandler;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();

        if(!sender.hasPermission("vpt.ptreset")) {
            sender.sendMessage(configHandler.getNO_PERMISSION());
            return;
        }

        switch (args.length) {
            case 0 -> sender.sendMessage(configHandler.getPTRESET_HELP());
            case 1 -> {
                if(main.playtimeCache.containsKey(args[0])) {
                    cacheHandler.upd2(args[0]); //No idea what I'm doing here
                    resetPT(args[0], sender);
                    return;
                }
                if(main.getSavedPt(args[0]) == 0) {
                    sender.sendMessage(configHandler.getNO_PLAYER());
                    return;
                }
                resetPT(args[0], sender);
            }
            default -> sender.sendMessage(configHandler.getINVALID_ARGS());
        }
    }

    private void resetPT(String player, CommandSource sender) {
        main.savePt(player, 0L);
        String message = configHandler.getPTRESET().replace("%player%", player);
        sender.sendMessage(configHandler.decideNonComponent(message));
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        CommandSource sender = invocation.source();
        if(!sender.hasPermission("vpt.ptreset"))
            return CompletableFuture.completedFuture(new ArrayList<>());
        return CompletableFuture.completedFuture(main.calcTab(sender, invocation.arguments()));
    }
}
