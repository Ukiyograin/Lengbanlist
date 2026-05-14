package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.object.ReportEntry;
import org.leng.utils.Utils;

import java.util.UUID;

public class ReportCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public ReportCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("report")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        if (!(sender instanceof Player)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c此命令只能由玩家执行。");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 1) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法错误: /report <玩家名> <原因> 或 /report accept/close <举报编号>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "accept":
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c用法错误: /report accept <举报编号>");
                    return true;
                }
                handleAccept(player, args[1]);
                break;
            case "close":
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c用法错误: /report close <举报编号>");
                    return true;
                }
                handleClose(player, args[1]);
                break;
            case "ack":
                if (args.length < 2) {
                    return true;
                }
                handleAck(player, args[1]);
                break;
            default:
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c用法错误: /report <玩家名> <原因>");
                    return true;
                }
                String target = args[0];
                String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                handleReportSubmit(player, target, reason);
                break;
        }
        return true;
    }

    private void handleAccept(Player player, String reportId) {
        ReportEntry report = plugin.getReportManager().getReport(reportId);
        if (report == null) {
            Utils.sendMessage(player, plugin.prefix() + "§c未找到举报编号: " + reportId);
            return;
        }

        report.setStatus("受理中");
        plugin.getReportManager().updateReport(report);

        Player reporter = Bukkit.getPlayer(report.getReporter());
        if (reporter != null) {
            Utils.sendMessage(reporter, plugin.prefix() + "§a你的举报已被受理，受理人：" + player.getName() + "，举报编号：" + report.getId() + "，将尽快处理。");
        }
        Utils.sendMessage(player, plugin.prefix() + "§a你已受理举报：" + report.getId());
    }

    private void handleClose(Player player, String reportId) {
        ReportEntry report = plugin.getReportManager().getReport(reportId);
        if (report == null) {
            Utils.sendMessage(player, plugin.prefix() + "§c未找到举报编号: " + reportId);
            return;
        }

        report.setStatus("已关闭");
        plugin.getReportManager().updateReport(report);

        Player reporter = Bukkit.getPlayer(report.getReporter());
        if (reporter != null) {
            Utils.sendMessage(reporter, plugin.prefix() + "§e你的举报(编号：" + report.getId() + ")已被管理员关闭。");
        }
        Utils.sendMessage(player, plugin.prefix() + "§a你已关闭举报: " + report.getId());
    }

    private void handleAck(Player player, String reportId) {
        ReportEntry report = plugin.getReportManager().getReport(reportId);
        if (report == null) return;
        report.setStatus("已读");
        plugin.getReportManager().updateReport(report);
        Utils.sendMessage(player, plugin.prefix() + "§a已标记举报 " + reportId + " 为已读。");
    }

    private void handleReportSubmit(Player reporter, String target, String reason) {
        // 检查24小时内是否已举报过同一目标且案件未结束
        long oneDayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        java.util.List<ReportEntry> recentReports = plugin.getReportManager().getReportsByReporterAndTarget(reporter.getName(), target);
        for (ReportEntry r : recentReports) {
            if (r.getTimestamp() > oneDayAgo && !"已关闭".equals(r.getStatus())) {
                Utils.sendMessage(reporter, plugin.prefix() + "§c你在24小时内已举报过该玩家，且该举报尚未处理完毕，请耐心等待！");
                return;
            }
        }

        String reportId = UUID.randomUUID().toString().substring(0, 8);
        ReportEntry report = new ReportEntry(target, reporter.getName(), reason, reportId, System.currentTimeMillis(), "未处理");
        plugin.getReportManager().addReport(report);
        Utils.sendMessage(reporter, plugin.prefix() + "§a举报已提交: " + target + " - " + reason + "，举报编号：" + reportId);

        // 通知在线OP
        for (org.bukkit.entity.Player op : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (op.isOp()) {
                org.bukkit.entity.Player targetPlayer = org.bukkit.Bukkit.getPlayer(target);
                String targetLoc = "";
                if (targetPlayer != null) {
                    targetLoc = " §7(世界: " + targetPlayer.getWorld().getName() + " X:" + (int)targetPlayer.getLocation().getX() + " Y:" + (int)targetPlayer.getLocation().getY() + " Z:" + (int)targetPlayer.getLocation().getZ() + ")";
                }
                op.spigot().sendMessage(
                    new net.md_5.bungee.api.chat.TextComponent(plugin.prefix() + "§e新举报！编号：§f" + reportId + " §e被举报人："),
                    org.leng.utils.Utils.clickableText("§c" + target, "/lban tp " + target),
                    new net.md_5.bungee.api.chat.TextComponent(" §e举报人："),
                    org.leng.utils.Utils.clickableText("§a" + reporter.getName(), "/lban tp " + reporter.getName()),
                    new net.md_5.bungee.api.chat.TextComponent(" §e原因：§f" + reason + targetLoc)
                );
            }
        }
    }
}
