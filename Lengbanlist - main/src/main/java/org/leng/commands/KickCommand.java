package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.utils.Utils;
import java.util.Arrays;

public class KickCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public KickCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("kick")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        // 检查权限
        if (!sender.hasPermission("lengbanlist.kick")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }

        // 检查参数
        if (args.length < 2) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法: /kick <玩家> <原因>");
            return true;
        }

        // 获取目标玩家
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Utils.sendMessage(sender, plugin.prefix() + "§c玩家 " + args[0] + " 不在线或不存在。");
            return true;
        }

        // 获取原因
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Model model = plugin.getModelManager().getCurrentModel();

        // 执行踢出
        target.kickPlayer(model.getKickMessage(reason));
        Utils.sendMessage(sender, plugin.prefix() + model.onKickSuccess(target.getName(), reason));

        return true;
    }
}