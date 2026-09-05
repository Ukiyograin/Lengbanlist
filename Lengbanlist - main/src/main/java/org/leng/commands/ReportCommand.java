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
            Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /report <玩家名> <原因> 或 /report accept/close <举报编号>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "accept":
                if (!player.hasPermission("lengbanlist.admin")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限处理举报。");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /report accept <举报编号>");
                    return true;
                }
                handleAccept(player, args[1]);
                break;
            case "close":
                if (!player.hasPermission("lengbanlist.admin")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限处理举报。");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /report close <举报编号>");
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
                    Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /report <玩家名> <原因>");
                    return true;
                }
                String target = args[0];
                String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                handleReportSubmit(player, target, reason);
                break;
        }
        return true;
    }

    private String formatCooldown(int seconds) {
        if (seconds <= 0) return "";
        if (seconds % 3600 == 0) return (seconds / 3600) + "小时";
        if (seconds % 60 == 0) return (seconds / 60) + "分钟";
        return seconds + "秒";
    }

    private void handleAccept(Player player, String reportId) {
        ReportEntry report = plugin.getReportManager().getReport(reportId);
        if (report == null) {
            Utils.sendMessage(player, plugin.prefix() + "§c未找到举报编号: " + reportId);
            return;
        }

        report = report.withStatus("受理中");
        plugin.getReportManager().updateReport(report);
        plugin.getAuditManager().log("受理举报", player.getName(), report.getTarget(), "编号: " + report.getId() + " - " + report.getReason());

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

        report = report.withStatus("已关闭");
        plugin.getReportManager().updateReport(report);
        plugin.getAuditManager().log("关闭举报", player.getName(), report.getTarget(), "编号: " + report.getId() + " - " + report.getReason());

        Player reporter = Bukkit.getPlayer(report.getReporter());
        if (reporter != null) {
            Utils.sendMessage(reporter, plugin.prefix() + "§e你的举报(编号：" + report.getId() + ")已被管理员关闭。");
        }
        Utils.sendMessage(player, plugin.prefix() + "§a你已关闭举报: " + report.getId());
    }

    private void handleAck(Player player, String reportId) {
        ReportEntry report = plugin.getReportManager().getReport(reportId);
        if (report == null) return;
        if (!report.getReporter().equalsIgnoreCase(player.getName())) {
            Utils.sendMessage(player, plugin.prefix() + "§c你只能确认自己提交的举报。");
            return;
        }
        report = report.withStatus("已读");
        plugin.getReportManager().updateReport(report);
        plugin.getAuditManager().log("已读举报", player.getName(), report.getTarget(), "编号: " + reportId);
        Utils.sendMessage(player, plugin.prefix() + "§a已标记举报 " + reportId + " 为已读。");
    }

    private void handleReportSubmit(Player reporter, String target, String reason) {
        int cooldownSeconds = plugin.getConfig().getInt("report-cooldown-seconds", 86400);
        long cooldownMillis = Math.max(0, (long) cooldownSeconds * 1000);
        long cutoff = System.currentTimeMillis() - cooldownMillis;
        java.util.List<ReportEntry> recentReports = plugin.getReportManager().getReportsByReporterAndTarget(reporter.getName(), target);
        for (ReportEntry r : recentReports) {
            if (r.getTimestamp() > cutoff && !"已关闭".equals(r.getStatus())) {
                Utils.sendMessage(reporter, plugin.prefix() + "§c你在" + formatCooldown(cooldownSeconds) + "内已举报过该玩家，且该举报尚未处理完毕，请耐心等待！");
                return;
            }
        }

        String reportId = UUID.randomUUID().toString();
        ReportEntry report = new ReportEntry(target, reporter.getName(), reason, reportId, System.currentTimeMillis(), "未处理");
        plugin.getReportManager().addReport(report);
        plugin.getAuditManager().log("提交举报", reporter.getName(), target, reason);
        Utils.sendMessage(reporter, plugin.prefix() + "§a举报已提交: " + target + " - " + reason + "，举报编号：" + reportId);


        for (org.bukkit.entity.Player op : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (op.isOp()) {
                org.bukkit.entity.Player targetPlayer = org.bukkit.Bukkit.getPlayer(target);
                String targetLoc = "";
                net.md_5.bungee.api.chat.BaseComponent targetComponent;
                if (targetPlayer != null) {
                    targetLoc = " §7(世界: " + targetPlayer.getWorld().getName() + " X:" + (int)targetPlayer.getLocation().getX() + " Y:" + (int)targetPlayer.getLocation().getY() + " Z:" + (int)targetPlayer.getLocation().getZ() + ")";
                    targetComponent = org.leng.utils.Utils.clickableText("§c" + target, "/lban tp " + target);
                } else {
                    targetComponent = new net.md_5.bungee.api.chat.TextComponent("§7" + target);
                }

                net.md_5.bungee.api.chat.BaseComponent reporterComponent;
                if (org.bukkit.Bukkit.getPlayer(reporter.getName()) != null) {
                    reporterComponent = org.leng.utils.Utils.clickableText("§e" + reporter.getName(), "/lban tp " + reporter.getName());
                } else {
                    reporterComponent = new net.md_5.bungee.api.chat.TextComponent("§7" + reporter.getName());
                }

                op.spigot().sendMessage(
                    new net.md_5.bungee.api.chat.TextComponent(plugin.prefix() + "§e新举报！编号：§f" + reportId + " §e被举报人："),
                    targetComponent,
                    new net.md_5.bungee.api.chat.TextComponent(" §e举报人："),
                    reporterComponent,
                    new net.md_5.bungee.api.chat.TextComponent(" §e原因：§f" + reason + targetLoc)
                );
            }
        }
    }
}
