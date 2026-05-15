package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

public class ListMuteCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public ListMuteCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("mute")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        if (!sender.hasPermission("lengbanlist.listmute")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }
        Utils.sendMessage(sender, "§7--§bLengbanlist 禁言名单§7--");
        for (MuteEntry entry : plugin.getMuteManager().getMuteList()) {
            Utils.sendMessage(sender, "§9§o被禁言者: " + entry.getTarget() + " §6处理人: " + entry.getStaff() + " §d禁言时间: " + TimeUtils.timestampToReadable(entry.getTime()) + " §l§n禁言原因: " + entry.getReason());
        }
        return true;
    }
}
