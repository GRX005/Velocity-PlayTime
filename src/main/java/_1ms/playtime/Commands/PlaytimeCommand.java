package _1ms.playtime.Commands;

import _1ms.playtime.Handlers.ConfigHandler;
import _1ms.playtime.Main;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlaytimeCommand implements SimpleCommand {
    private final Main main;
    private final ConfigHandler configHandler;

    public PlaytimeCommand(Main main, ConfigHandler configHandler) {
        this.main = main;
        this.configHandler = configHandler;
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
                if(sender instanceof Player player1)
                    if(player1.getUsername().equalsIgnoreCase(args[0])) {
                        SendYourPlaytime(player1);
                        return;
                    }
                if(configHandler.isVIEW_OTHERS_TIME() && !sender.hasPermission("vpt.getotherstime")) {
                    sender.sendMessage(configHandler.getNO_PERMISSION());
                    return;
                }
                long PlayTime = main.playtimeCache.containsKey(args[0]) ? main.GetPlayTime(args[0]) : main.getSavedPt(args[0]);
                if (PlayTime == -1)
                    sender.sendMessage(configHandler.getNO_PLAYER());
                else
                    sender.sendMessage(configHandler.decideNonComponent(configHandler.repL(configHandler.getOTHER_PLAYTIME(), PlayTime).replace("%player%", args[0]).replace("%place%", String.valueOf(main.getPlace(args[0])))));
            }
            default -> sender.sendMessage(configHandler.getINVALID_ARGS());
        }
    }
    public void SendYourPlaytime(Player player) {
        if (configHandler.isVIEW_OWN_TIME() && !player.hasPermission("vpt.getowntime")) {
            player.sendMessage(configHandler.getNO_PERMISSION());
            return;
        }
        final long PlayTime = main.GetPlayTime(player.getUsername());
        player.sendMessage(configHandler.decideNonComponent(configHandler.repL(configHandler.getYOUR_PLAYTIME(), PlayTime).replace("%place%", String.valueOf(main.getPlace(player.getUsername())))));
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        CommandSource sender = invocation.source();
        if(configHandler.isVIEW_OTHERS_TIME() && !sender.hasPermission("vpt.getotherstime"))
            return CompletableFuture.completedFuture(new ArrayList<>());
        return CompletableFuture.completedFuture(main.calcTab(sender, invocation.arguments()));
    }
}
