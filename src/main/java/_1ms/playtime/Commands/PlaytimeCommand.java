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
        String[] args = invocation.arguments();
        CommandSource sender = invocation.source();
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
                    if(player1.getGameProfile().getName().equalsIgnoreCase(args[0])) {
                        SendYourPlaytime(player1);
                        return;
                    }
                if(configHandler.isVIEW_OTHERS_TIME() && !sender.hasPermission("vpt.getotherstime")) {
                    sender.sendMessage(configHandler.getNO_PERMISSION());
                    return;
                }
                long PlayTime = main.playtimeCache.containsKey(args[0]) ? main.GetPlayTime(args[0]) : configHandler.getPtFromConfig(args[0]);
                if (PlayTime == 0) {
                    sender.sendMessage(configHandler.getNO_PLAYER());
                } else {
                    String message = configHandler.getOTHER_PLAYTIME()
                            .replace("%hours%", String.valueOf(main.calculatePlayTime(PlayTime, 'h')))
                            .replace("%minutes%", String.valueOf(main.calculatePlayTime(PlayTime, 'm')))
                            .replace("%seconds%", String.valueOf(main.calculatePlayTime(PlayTime, 's')))
                            .replace("%player%", args[0]);
                    sender.sendMessage(configHandler.decideNonComponent(message));
                }
            }
            default -> sender.sendMessage(configHandler.getINVALID_ARGS());
        }
    }
    public void SendYourPlaytime(Player player) {
        if (configHandler.isVIEW_OWN_TIME() && !player.hasPermission("vpt.getowntime")) {
            player.sendMessage(configHandler.getNO_PERMISSION());
            return;
        }
        long PlayTime = main.GetPlayTime(player.getGameProfile().getName());
        String messageBegin = configHandler.getYOUR_PLAYTIME()
                .replace("%hours%", String.valueOf(main.calculatePlayTime(PlayTime, 'h')))
                .replace("%minutes%", String.valueOf(main.calculatePlayTime(PlayTime, 'm')))
                .replace("%seconds%", String.valueOf(main.calculatePlayTime(PlayTime, 's')));
        player.sendMessage(configHandler.decideNonComponent(messageBegin));
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        List<String> tabargs = new ArrayList<>();
        String[] args = invocation.arguments();
        CommandSource sender = invocation.source();
        if(configHandler.isVIEW_OTHERS_TIME() && !sender.hasPermission("vpt.getotherstime")) {
            return CompletableFuture.completedFuture(tabargs);
        }
        try {
            for (Player player : main.getProxy().getAllPlayers()) {
                if (!player.equals(sender) && player.getGameProfile().getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    tabargs.add(player.getGameProfile().getName());
                }
            }
        } catch (Exception ignored) {
            for (Player player : main.getProxy().getAllPlayers()) {
                if (!player.equals(sender)) {
                    tabargs.add(player.getGameProfile().getName());
                }
            }
        }
        return CompletableFuture.completedFuture(tabargs);
    }
}
