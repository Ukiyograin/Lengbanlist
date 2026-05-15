package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.utils.Utils;

public class UnmuteCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public UnmuteCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("mute")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        if (!sender.hasPermission("lengbanlist.mute")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }
        if (args.length < 1) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法: /" + label + " <玩家名>");
            return true;
        }
        plugin.getMuteManager().unmutePlayer(args[0]);
        Utils.sendMessage(sender, ModelManager.getInstance().getCurrentModel().removeMute(args[0]));
        return true;
    }
}
