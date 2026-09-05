package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.EscalationManager.EscalationResult;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.manager.BanManager;
import org.leng.manager.BanMutationFeedback;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class SetBanCommand implements CommandExecutor, TabCompleter {

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


        if (!(sender instanceof Player) || !sender.isOp()) {
            if (!sender.hasPermission("lengbanlist.setban")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return true;
            }
        }


        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }


        String target = args[0];
        String timeArg = args[1];
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        BanManager banManager = plugin.getBanManager();


        boolean isIp = banManager.isValidIpOrCidr(target);


        if (!isIp && !plugin.getImmunityManager().canPunish(sender, target)) {
            Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(target));
            return true;
        }

        if (!isIp && !banManager.isPlayerBanned(target) && !banManager.isIpBanned(target)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c目标 " + target + " 未被封禁，无法设置封禁时间。");
            return true;
        }


        long banDuration;
        boolean isAuto = false;
        EscalationResult escalationResult = null;

        if (timeArg.equalsIgnoreCase("forever")) {
            banDuration = Long.MAX_VALUE;
        } else if (timeArg.equalsIgnoreCase("auto")) {
            isAuto = true;
            escalationResult = plugin.getEscalationManager().resolveBan(target);
            banDuration = escalationResult.durationMillis;
        } else {
            banDuration = TimeUtils.parseDurationToMillis(timeArg);
            if (banDuration <= 0) {
                showTimeFormatError(sender);
                return true;
            }
        }


        BanManager.BanMutationResult result;
        if (isIp) {

            BanIpEntry existingBanIp = banManager.getBanIpEntry(target);
            if (existingBanIp == null) {
                Utils.sendMessage(sender, plugin.prefix() + "§cIP " + target + " 未被封禁，无法设置封禁时间。");
                return true;
            }
            BanIpEntry updatedIp = existingBanIp.withEndTime(TimeUtils.calculateEndTime(banDuration))
                    .withReason(reason)
                    .withAuto(isAuto);
            result = banManager.tryUpdateIpBan(updatedIp);
        } else {

            BanEntry existingBan = banManager.getBanEntry(target);
            if (existingBan == null) {
                Utils.sendMessage(sender, plugin.prefix() + "§c玩家 " + target + " 未被封禁，无法设置封禁时间。");
                return true;
            }
            BanEntry updatedBan = existingBan.withEndTime(TimeUtils.calculateEndTime(banDuration))
                    .withReason(reason)
                    .withAuto(isAuto);
            result = banManager.tryUpdateBan(updatedBan);
        }

        if (!result.isApplied()) {
            BanMutationFeedback.sendFailure(sender, result, target, isIp);
            return true;
        }


        String durationStr;
        if (banDuration == Long.MAX_VALUE) {
            durationStr = "永久";
        } else {
            durationStr = TimeUtils.formatDuration(banDuration);
        }

        if (escalationResult != null && escalationResult.offenseCount > 0) {
            Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().onEscalatedBan(
                    target, escalationResult.offenseCount, TimeUtils.formatDuration(banDuration)));
        }
        Utils.sendMessage(sender, plugin.prefix() + "§a成功更新目标 " + target + " 的封禁时间，新的封禁时长为: §e" + durationStr + "§a，理由: §e" + reason);
        plugin.getAuditManager().log("设置封禁时间", Utils.getSenderName(sender), target, durationStr + " - " + reason);

        return true;
    }

    private void sendUsage(CommandSender sender) {
        Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /setban <玩家名/IP> <时间/forever/auto> <理由>");
        Utils.sendMessage(sender, plugin.prefix() + "§c时间单位喵: s(秒), m(分钟), h(小时), d(天), w(周), M(月), y(年)");
        Utils.sendMessage(sender, plugin.prefix() + "§c使用 'forever' 表示永久封禁，使用 'auto' 自动计算封禁时间喵（基于警告次数）");
    }

    private void showTimeFormatError(CommandSender sender) {
        Utils.sendMessage(sender, plugin.prefix() + "§c时间格式错误喵，请使用以下格式:");
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (BanEntry e : plugin.getBanManager().getBanList()) {
                if (e.getTarget().toLowerCase().startsWith(prefix)) completions.add(e.getTarget());
            }
            for (BanIpEntry e : plugin.getBanManager().getBanIpList()) {
                if (e.getIp().toLowerCase().startsWith(prefix)) completions.add(e.getIp());
            }
        }
        return completions;
    }

}
