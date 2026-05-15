package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;

public class WarnMsgCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public WarnMsgCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("warn")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        // 检查是否为OP
        if (!(sender instanceof Player) || !sender.isOp()) {
            sender.sendMessage(plugin.prefix() + "§c只有管理员可以使用此命令。");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.prefix() + "§c用法错误: /warnmsg <玩家名>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.prefix() + "§c玩家 " + args[0] + " 不在线或不存在。");
            return true;
        }

        // 警告玩家
        plugin.getWarnManager().warnPlayer(target.getName(), sender.getName(), "消息违规");
        target.sendMessage(plugin.prefix() + "§c你因消息违规被警告一次。");
        sender.sendMessage(plugin.prefix() + "§a已警告玩家 " + target.getName() + "。");
        return true;
    }
}