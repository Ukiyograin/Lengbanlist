package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.manager.BanManager;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.Arrays;

public class SetBanCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public SetBanCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("setban")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        // 检查权限
        if (!(sender instanceof Player) || !sender.isOp()) {
            if (!sender.hasPermission("lengbanlist.setban")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return true;
            }
        }

        // 检查参数长度
        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }

        // 获取目标（玩家名或 IP）
        String target = args[0];
        String timeArg = args[1];
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        BanManager banManager = plugin.getBanManager();

        // 检查目标是否是 IP
        boolean isIp = banManager.isValidIp(target);

        // 检查目标是否存在
        if (!isIp && !banManager.isPlayerBanned(target) && !banManager.isIpBanned(target)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c目标 " + target + " 未被封禁，无法设置封禁时间。");
            return true;
        }

        // 解析封禁时间
        long banDuration;
        boolean isAuto = false;

        if (timeArg.equalsIgnoreCase("forever")) {
            banDuration = Long.MAX_VALUE; // 永久封禁
        } else if (timeArg.equalsIgnoreCase("auto")) {
            isAuto = true;
            banDuration = calculateAutoBanTime(target);
            // 仅对auto封禁确保最小1天
            banDuration = Math.max(banDuration, TimeUtils.daysToMillis(1));
        } else {
            banDuration = TimeUtils.parseDurationToMillis(timeArg);
            if (banDuration <= 0) {
                showTimeFormatError(sender);
                return true;
            }
        }

        // 更新封禁信息
        if (isIp) {
            // 更新 IP 封禁
            BanIpEntry existingBanIp = banManager.getBanIpEntry(target);
            if (existingBanIp == null) {
                Utils.sendMessage(sender, plugin.prefix() + "§cIP " + target + " 未被封禁，无法设置封禁时间。");
                return true;
            }
            existingBanIp.setEndTime(banDuration == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + banDuration);
            existingBanIp.setReason(reason);
            existingBanIp.setAuto(isAuto);
            banManager.updateIpBan(existingBanIp);
        } else {
            // 更新玩家封禁
            BanEntry existingBan = banManager.getBanEntry(target);
            if (existingBan == null) {
                Utils.sendMessage(sender, plugin.prefix() + "§c玩家 " + target + " 未被封禁，无法设置封禁时间。");
                return true;
            }
            existingBan.setEndTime(banDuration == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + banDuration);
            existingBan.setReason(reason);
            existingBan.setAuto(isAuto);
            banManager.updateBan(existingBan);
        }

        // 发送结果消息
        String durationStr;
        if (banDuration == Long.MAX_VALUE) {
            durationStr = "永久";
        } else {
            durationStr = TimeUtils.formatDuration(banDuration);
        }

        Utils.sendMessage(sender, plugin.prefix() + "§a成功更新目标 " + target + " 的封禁时间，新的封禁时长为: §e" + durationStr + "§a，理由: §e" + reason);

        return true;
    }

    private long calculateAutoBanTime(String target) {
        int warnCount = Math.max(0, plugin.getWarnManager().getActiveWarnings(target).size());

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

    private void sendUsage(CommandSender sender) {
        Utils.sendMessage(sender, plugin.prefix() + "§c用法错误: /setban <玩家名/IP> <时间/forever/auto> <理由>");
        Utils.sendMessage(sender, plugin.prefix() + "§c时间单位: s(秒), m(分钟), h(小时), d(天), w(周), M(月), y(年)");
        Utils.sendMessage(sender, plugin.prefix() + "§c使用 'forever' 表示永久封禁，使用 'auto' 自动计算封禁时间（基于警告次数）");
    }

    private void showTimeFormatError(CommandSender sender) {
        Utils.sendMessage(sender, plugin.prefix() + "§c时间格式错误，请使用以下格式:");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 10s: 秒 (10 秒)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 5m: 分钟 (5 分钟)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 2h: 小时 (2 小时)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 7d: 天 (7 天)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 1w: 周 (1 周，等于 7 天)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 1M: 月 (1 月，按 30 天计算)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 1y: 年 (1 年，按 365 天计算)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - forever: 永久封禁");
        Utils.sendMessage(sender, plugin.prefix() + "§c - auto: 自动计算封禁时间");
    }
}
