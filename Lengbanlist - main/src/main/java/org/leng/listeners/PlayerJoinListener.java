package org.leng.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.leng.Lengbanlist;
import org.leng.manager.ReportManager;
import org.leng.object.ReportEntry;
import org.leng.utils.SaveIP;

import java.util.List;
import java.util.stream.Collectors;

public class PlayerJoinListener implements Listener {
    private final Lengbanlist plugin;

    public PlayerJoinListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SaveIP.saveIP(player); // 保存玩家的 IP
        if (plugin.getBanManager().isPlayerBanned(player.getName())) {
            plugin.getBanManager().checkBanOnJoin(player); // 检查玩家是否被封禁
        }

        // 检查玩家是否有已被处理（非"未处理"状态）的举报消息
        ReportManager reportManager = plugin.getReportManager();
        List<ReportEntry> reports = reportManager.getPendingReports().stream()
                .filter(report -> report.getReporter().equals(player.getName()))
                .filter(report -> !"未处理".equals(report.getStatus()))
                .collect(Collectors.toList());

        if (!reports.isEmpty()) {
            player.sendMessage("——————————");
            player.sendMessage(plugin.prefix() + "你的举报已被处理。");
            player.spigot().sendMessage(
                org.leng.utils.Utils.clickableText("§a【我已阅读】", "/report ack " + reports.get(0).getId())
            );
            player.sendMessage("——————————");
        }
    }
}