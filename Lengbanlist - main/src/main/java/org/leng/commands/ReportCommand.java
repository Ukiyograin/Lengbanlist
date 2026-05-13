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
        Utils.sendMessage(player, plugin.prefix() + "§a你已关闭举报: " + report.getId());
    }

    private void handleReportSubmit(Player reporter, String target, String reason) {
        String reportId = UUID.randomUUID().toString();
        ReportEntry report = new ReportEntry(target, reporter.getName(), reason, reportId, System.currentTimeMillis(), "未处理");
        plugin.getReportManager().addReport(report);
        Utils.sendMessage(reporter, plugin.prefix() + "§a举报已提交: " + target + " - " + reason + "，举报编号：" + reportId);
    }
}
