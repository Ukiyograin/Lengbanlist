package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.Arrays;

public class BanIpCommand extends Command implements CommandExecutor {
    private final Lengbanlist plugin;

    public BanIpCommand(Lengbanlist plugin) {
        super("ban-ip");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String s, String[] args) {
        if (!plugin.isFeatureEnabled("ban-ip")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        // 检查权限
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!sender.isOp() && !player.hasPermission("lengbanlist.banip")) {
                Utils.sendMessage(sender, "§c你没有权限使用此命令。");
                return false;
            }
        }

        // 检查参数长度
        if (args.length < 3) {
            Utils.sendMessage(sender, "§c用法错误: /ban-ip <IP> <时间/auto> <原因>");
            Utils.sendMessage(sender, "§c时间单位: s(秒), m(分), h(时), d(天), w(周), M(月), y(年)");
            Utils.sendMessage(sender, "§c使用 auto 自动计算封禁时间");
            return false;
        }

        // 检查 IP 有效性
        if (!isValidIp(args[0])) {
            Utils.sendMessage(sender, "§c无效的IP地址或不允许封禁此IP");
            return false;
        }

        // 检查 IP 是否已经被封禁
        if (plugin.getBanManager().isIpBanned(args[0])) {
            Utils.sendMessage(sender, "§cIP " + args[0] + " 已经被封禁");
            return false;
        }

        // 解析封禁时间
        boolean isAuto = args[1].equalsIgnoreCase("auto");
        long banDuration;
        
        if (isAuto) {
            banDuration = calculateAutoBanTime(args[0]);
        } else {
            banDuration = TimeUtils.parseDurationToMillis(args[1]);
            if (banDuration <= 0) {
                showTimeFormatError(sender);
                return false;
            }
        }

        long banEndTime = System.currentTimeMillis() + banDuration;
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        // 执行封禁
        plugin.getBanManager().banIp(
            new org.leng.object.BanIpEntry(args[0], sender.getName(), banEndTime, reason, isAuto)
        );

        // 发送结果消息
        sendBanResult(sender, args[0], banDuration, isAuto);
        return true;
    }

    private boolean isValidIp(String ip) {
        if (ip.equalsIgnoreCase("127.0.0.1")) return false;
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private long calculateAutoBanTime(String ip) {
        // 根据IP的警告次数计算封禁时间
        int warnCount = plugin.getWarnManager().getActiveWarnings(ip).size();
        
        // 自动封禁阶梯式时长
        switch (warnCount) {
            case 0:  return TimeUtils.daysToMillis(1);
            case 1:  return TimeUtils.daysToMillis(3);
            case 2:  return TimeUtils.daysToMillis(7);
            case 3:  return TimeUtils.daysToMillis(14);
            case 4:  return TimeUtils.daysToMillis(30);
            default: return Long.MAX_VALUE;
        }
    }

    private void sendBanResult(CommandSender sender, String ip, long durationMillis, boolean isAuto) {
        String durationStr;
        if (durationMillis == Long.MAX_VALUE) {
            durationStr = "永久";
        } else {
            durationStr = TimeUtils.formatDuration(durationMillis);
        }

        String message = String.format("§a成功封禁 IP: %s，时长: %s%s",
            ip,
            durationStr,
            isAuto ? " §6<auto>" : "");

        Utils.sendMessage(sender, message);
    }

    private void showTimeFormatError(CommandSender sender) {
        Utils.sendMessage(sender, "§c时间格式错误，请使用以下格式:");
        Utils.sendMessage(sender, "§c - 10s: 秒 (10 秒)");
        Utils.sendMessage(sender, "§c - 5m: 分钟 (5 分钟)");
        Utils.sendMessage(sender, "§c - 2h: 小时 (2 小时)");
        Utils.sendMessage(sender, "§c - 7d: 天 (7 天)");
        Utils.sendMessage(sender, "§c - 1w: 周 (1 周，等于 7 天)");
        Utils.sendMessage(sender, "§c - 1M: 月 (1 月，按 30 天计算)");
        Utils.sendMessage(sender, "§c - 1y: 年 (1 年，按 365 天计算)");
        Utils.sendMessage(sender, "§c - auto: 自动计算封禁时间");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }
}