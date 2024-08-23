package _1ms.playtime.Commands;

import _1ms.playtime.Handlers.ConfigHandler;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;

public class ConfigReload implements SimpleCommand {
    private final ConfigHandler configHandler;

    public ConfigReload(ConfigHandler configHandler) {
        this.configHandler = configHandler;
    }


    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        if(!sender.hasPermission("vpt.reload")) {
            sender.sendMessage(configHandler.getNO_PERMISSION());
            return;
        }

        if(invocation.arguments().length > 0) {
            sender.sendMessage(configHandler.getINVALID_ARGS());
            return;
        }
        configHandler.reloadConfig();
        sender.sendMessage(configHandler.getCONFIG_RELOAD());
    }

}
