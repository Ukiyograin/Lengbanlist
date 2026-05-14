package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.ReportManager;
import org.leng.object.ReportEntry;
import org.leng.utils.Utils;

import java.util.List;

public class AdminReportCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public AdminReportCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lengbanlist.admin")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }

        if (!(sender instanceof Player)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c此命令只能由玩家执行。");
            return true;
        }

        Player player = (Player) sender;
        showAdminReportUI(player);
        return true;
    }

    private void showAdminReportUI(Player player) {
        ReportManager reportManager = plugin.getReportManager();

        int pendingReports = reportManager.getPendingReportCount();
        int onlineAdmins = (int) Bukkit.getOnlinePlayers().stream().filter(p -> p.isOp()).count();

        String adminUI = "§7————————————————\n" +
                "§bLengbanlist Report Admin\n" +
                "§e当前待处理举报数：§c" + pendingReports + "\n" +
                "§e当前在线管理员：§c" + onlineAdmins + "\n" +
                "§7————————————————\n";

        player.sendMessage(adminUI);

        List<ReportEntry> reports = reportManager.getPendingReports();
        if (reports.isEmpty()) {
            player.sendMessage("§a暂无待处理的举报！");
            return;
        }

        for (ReportEntry report : reports) {
            Player targetPlayer = Bukkit.getPlayer(report.getTarget());
            Player reporterPlayer = Bukkit.getPlayer(report.getReporter());

            String targetLoc = "";
            if (targetPlayer != null) {
                targetLoc = " §7(世界:" + targetPlayer.getWorld().getName() + " X:" + (int)targetPlayer.getLocation().getX() + " Y:" + (int)targetPlayer.getLocation().getY() + " Z:" + (int)targetPlayer.getLocation().getZ() + ")";
            }
            String reporterLoc = "";
            if (reporterPlayer != null) {
                reporterLoc = " §7(世界:" + reporterPlayer.getWorld().getName() + " X:" + (int)reporterPlayer.getLocation().getX() + " Y:" + (int)reporterPlayer.getLocation().getY() + " Z:" + (int)reporterPlayer.getLocation().getZ() + ")";
            }

            String status = report.getStatus() == null ? "" : "§a【当前状态：" + report.getStatus() + "】";
            player.sendMessage("§7————————————————");
            player.sendMessage("§e举报编号：§f" + report.getId() + " " + status);
            player.sendMessage("§e举报原因：§f" + report.getReason());

            player.spigot().sendMessage(
                new net.md_5.bungee.api.chat.TextComponent("§e被举报人："),
                Utils.clickableText("§c" + report.getTarget() + targetLoc, "/lban tp " + report.getTarget()),
                new net.md_5.bungee.api.chat.TextComponent(" §e举报人："),
                Utils.clickableText("§a" + report.getReporter() + reporterLoc, "/lban tp " + report.getReporter())
            );

            player.spigot().sendMessage(
                Utils.clickableText("§a【点击受理】", "/report accept " + report.getId()),
                new net.md_5.bungee.api.chat.TextComponent(" "),
                Utils.clickableText("§b【点击关闭】", "/report close " + report.getId()),
                new net.md_5.bungee.api.chat.TextComponent(" "),
                Utils.clickableText("§c【点击封禁】", "/lban add " + report.getTarget() + " ")
            );
            player.sendMessage("§7————————————————");
        }
    }
}