package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

public class AllowMsgCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public AllowMsgCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("chat-filter")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        // 检查是否为OP
        if (!(sender instanceof Player) || !sender.isOp()) {
            sender.sendMessage(plugin.prefix() + "§c只有管理员可以使用此命令。");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(plugin.prefix() + "§c用法错误: /allowmsg <玩家名>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.prefix() + "§c玩家 " + args[0] + " 不在线或不存在。");
            return true;
        }

        // 允许发送消息
        target.sendMessage(plugin.prefix() + "§a你的消息已被管理员允许发送。");
        sender.sendMessage(plugin.prefix() + "§a已允许玩家 " + target.getName() + " 发送消息。");
        return true;
    }
}