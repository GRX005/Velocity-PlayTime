package _1ms.playtime.Commands;

import _1ms.playtime.Handlers.ConfigHandler;
import _1ms.playtime.Main;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PlaytimeResetAll implements SimpleCommand {
    private final Main main;
    private final ConfigHandler configHandler;

    public PlaytimeResetAll(Main main, ConfigHandler configHandler) {
        this.main = main;
        this.configHandler = configHandler;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();

        if(!sender.hasPermission("vpt.ptresetall")) {
            sender.sendMessage(configHandler.getNO_PERMISSION());
            return;
        }

        switch (args.length) {
            case 0 -> sender.sendMessage(configHandler.getPTRESETALL_CONFIRM().clickEvent(ClickEvent.suggestCommand("/ptresetall confirm")));
            case 1 -> {
                if(!args[0].equalsIgnoreCase("confirm")) {
                    sender.sendMessage(configHandler.getINVALID_ARGS());
                    return;
                }
                main.removeAllPt();
                main.playtimeCache.keySet().removeIf(pname -> {
                    Optional<Player> player = main.getProxy().getPlayer(pname);
                    if (player.isPresent()) {
                        main.playtimeCache.replace(pname, 0L);
                        return false;
                    } else
                        return true;
                });
                sender.sendMessage(configHandler.getPTRESETALL());
            }
            default -> sender.sendMessage(configHandler.getINVALID_ARGS());
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        List<String> tabargs = new ArrayList<>();
        if(!invocation.source().hasPermission("vpt.ptresetall"))
            return CompletableFuture.completedFuture(tabargs);
        tabargs.add("confirm");
        return CompletableFuture.completedFuture(tabargs);
    }
}
