package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

public class UnbanCommand extends Command implements CommandExecutor {
    private final Lengbanlist plugin;

    public UnbanCommand(Lengbanlist plugin) {
        super("unban");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String s, String[] args) {
        // 检查权限
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!sender.isOp() || !(player.hasPermission("lengbanlist.unban"))) {
                Utils.sendMessage(sender, "§c你没有权限使用此命令。");
                return false;
            }
        }

        // 检查参数长度
        if (args.length < 1) {
            Utils.sendMessage(sender, "§c用法错误: /unban <玩家名/IP>");
            return false;
        }

        // 判断是解封玩家还是解封 IP
        if (args[0].contains(".")) {
            // 解封 IP
            if (Lengbanlist.getInstance().banManager.isIpBanned(args[0])) {
                Lengbanlist.getInstance().banManager.unbanIp(args[0]);
                Utils.sendMessage(sender, "§l§a成功解封 IP: " + args[0]);
            } else {
                Utils.sendMessage(sender, "§cIP " + args[0] + " 未被封禁");
            }
        } else {
            // 解封玩家
            if (Lengbanlist.getInstance().banManager.isPlayerBanned(args[0])) {
                Lengbanlist.getInstance().banManager.unbanPlayer(args[0]);
                Utils.sendMessage(sender, "§l§a成功解封玩家: " + args[0]);
            } else {
                Utils.sendMessage(sender, "§c玩家 " + args[0] + " 未被封禁");
            }
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }
}