package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.Arrays; 

public class BanCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public BanCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("ban")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        // 权限检查
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!sender.isOp() && !player.hasPermission("lengbanlist.ban")) {
                Utils.sendMessage(sender, "§c你没有权限使用此命令。");
                return false;
            }
        }

        // 参数检查
        if (args.length < 3) {
            sendUsage(sender);
            return false;
        }

        String target = args[0];
        String timeArg = args[1];
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        // 检查是否已封禁
        if (plugin.getBanManager().isPlayerBanned(target)) {
            Utils.sendMessage(sender, "§c玩家 " + target + " 已经被封禁");
            return false;
        }

        long banDuration;
        boolean isAuto = false;

        if (timeArg.equalsIgnoreCase("auto")) {
            isAuto = true;
            banDuration = calculateAutoBanTime(target);
            // 仅对auto封禁确保最小1天
            banDuration = Math.max(banDuration, TimeUtils.daysToMillis(1));
        } else {
            banDuration = TimeUtils.parseDurationToMillis(timeArg);
            if (banDuration <= 0) {
                showTimeFormatError(sender);
                return false;
            }
            // 玩家手动设置的封禁时间不做限制
        }

        long banEndTime = System.currentTimeMillis() + banDuration;
        
        BanEntry entry = new BanEntry(
            target,
            sender.getName(),
            banEndTime,
            reason,
            isAuto
        );
        
        plugin.getBanManager().banPlayer(entry);
        sendBanResult(sender, target, banDuration, isAuto);
        return true;
    }

    private long calculateAutoBanTime(String playerName) {
        int warnCount = Math.max(0, plugin.getWarnManager().getActiveWarnings(playerName).size());
        
        // 自动封禁阶梯式时长
        switch (warnCount) {
            case 0:  return TimeUtils.daysToMillis(1);  // 无警告记录也封1天
            case 1:  return TimeUtils.daysToMillis(3);
            case 2:  return TimeUtils.daysToMillis(7);
            case 3:  return TimeUtils.daysToMillis(14);
            case 4:  return TimeUtils.daysToMillis(30);
            default: return Long.MAX_VALUE; // 超过4次永久封禁
        }
    }

    private void sendBanResult(CommandSender sender, String player, long durationMillis, boolean isAuto) {
        String durationStr;
        if (durationMillis == Long.MAX_VALUE) {
            durationStr = "永久";
        } else {
            long days = durationMillis / TimeUtils.daysToMillis(1);
            durationStr = days + "天";
        }

        String message = String.format("§a成功封禁 玩家: %s，时长: %s%s",
            player,
            durationStr,
            isAuto ? " §6<auto>" : "");

        Utils.sendMessage(sender, message);
    }

    private void sendUsage(CommandSender sender) {
        Utils.sendMessage(sender, "§c用法错误: /ban <玩家> <时间/auto> <原因>");
        Utils.sendMessage(sender, "§c时间单位: s(秒), m(分), h(时), d(天), w(周), M(月), y(年)");
        Utils.sendMessage(sender, "§c使用 auto 自动计算封禁时间（基于警告次数）");
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
        Utils.sendMessage(sender, "§c - forever: 永久封禁");
        Utils.sendMessage(sender, "§c - auto: 自动计算封禁时间");
    }
}