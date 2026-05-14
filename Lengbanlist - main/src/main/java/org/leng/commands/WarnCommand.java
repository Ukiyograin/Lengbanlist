package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.WarnManager;
import org.leng.object.WarnEntry;
import org.leng.utils.Utils;

import java.util.List;
import java.util.Arrays;

public class WarnCommand extends Command implements CommandExecutor {
    private final Lengbanlist plugin;

    public WarnCommand(Lengbanlist plugin) {
        super("warn");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String s, String[] args) {
        // 检查权限
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!sender.isOp() && !player.hasPermission("lengbanlist.warn")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return false;
            }
        }

        // 检查参数长度
        if (args.length < 2) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法错误: /lban warn <玩家名/IP> <原因>");
            return false;
        }

        String target = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        WarnManager warnManager = plugin.getWarnManager();

        // 检查是否是 IP
        boolean isIp = target.contains(".");

        // 检查是否是 IP 地址
        if (isIp) {
            if (!plugin.getBanManager().isValidIp(target)) {
                Utils.sendMessage(sender, plugin.prefix() + "§c无效的IP地址");
                return false;
            }
            // IP警告逻辑
            warnManager.warnPlayer(target, sender.getName(), reason);
            Utils.sendMessage(sender, plugin.prefix() + "§a已警告IP: " + target);
            return true;
        }

        // 玩家警告逻辑 - 允许超过3次警告，警告将在1天后自动过期
        warnManager.warnPlayer(target, sender.getName(), reason);
        Utils.sendMessage(sender, plugin.prefix() + "§a已警告玩家 " + target + ": " + reason);

        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }
}