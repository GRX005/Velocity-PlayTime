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
