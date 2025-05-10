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
