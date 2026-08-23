package org.leng.fabric;

import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.utils.TimeUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FabricServerFeatures {
    private final FabricLengbanlist plugin;
    private final ConcurrentMap<String, Integer> badWordCount = new ConcurrentHashMap<>();

    public FabricServerFeatures(FabricLengbanlist plugin) {
        this.plugin = plugin;
    }

    public void onPlayerJoin(Object player, Object server) {
        String playerName = ReflectionSupport.playerName(player);
        String playerIp = ReflectionSupport.playerIp(player);
        if (playerIp != null) {
            plugin.getIpAssociationManager().recordLogin(playerName, playerIp);
        }

        if (plugin.isFeatureEnabled("ban") || plugin.isFeatureEnabled("ban-ip")) {
            String kickMessage = plugin.getBanManager().checkBanOnJoin(playerName, playerIp);
            if (kickMessage != null) {
                ReflectionSupport.kick(player, kickMessage);
                return;
            }
        }

        if (plugin.isFeatureEnabled("ip-association") && playerIp != null
                && plugin.getIpAssociationManager().hasSuspiciousLogin(playerName, playerIp)) {
            String message = plugin.prefix() + "§e玩家 §f" + playerName + " §e的 IP 曾由以下玩家使用: §f"
                    + String.join("§7, §f", plugin.getIpAssociationManager().getSuspiciousLoginDetails(playerName, playerIp));
            notifyOperators(server, "§7[§cIP关联§7] §f" + playerName + " §e的 IP 存在关联账号");
            ReflectionSupport.sendConsoleMessage(server, "§7[§cIP关联§7] " + message);
        }

        if (plugin.isFeatureEnabled("vpn-detection") && org.leng.manager.IpAssociationManager.isRealIp(playerIp)) {
            new Thread(() -> {
                if (plugin.getIpAssociationManager().isVpnIp(playerIp)) {
                    plugin.runSync(() -> handleVpnDetection(player, server, playerName, playerIp));
                }
            }, "Lengbanlist VPN Check").start();
        }

        if (plugin.isFeatureEnabled("report")) {
            List<ReportEntry> reports = plugin.getReportManager().getPendingReports();
            for (ReportEntry report : reports) {
                if (report.getReporter().equals(playerName) && !"未处理".equals(report.getStatus())) {
                    ReflectionSupport.sendMessage(player, plugin.prefix() + "§7——————————");
                    ReflectionSupport.sendMessage(player, plugin.prefix() + "§a你的举报已被处理。");
                    ReflectionSupport.sendMessage(player, plugin.prefix() + "§7——————————");
                    break;
                }
            }
        }
    }

    public boolean onChat(Object player, String message) {
        if (!plugin.isFeatureEnabled("chat-filter")) return false;

        String playerName = ReflectionSupport.playerName(player);
        if (plugin.getMuteManager().isPlayerMuted(playerName)) {
            ReflectionSupport.sendMessage(player, plugin.prefix() + "§c你不准说话喵！");
            return true;
        }

        List<String> badWords = plugin.getChatConfigStringList("bad-words");
        boolean containsBadWord = false;
        String filteredMessage = message;
        for (String badWord : badWords) {
            if (message.contains(badWord)) {
                containsBadWord = true;
                filteredMessage = filteredMessage.replace(badWord, replacement(badWord.length()));
            }
        }

        if (!containsBadWord) return false;

        int count = badWordCount.merge(playerName, 1, Integer::sum);
        if (count >= plugin.getChatConfigInt("mute-threshold", 3)) {
            plugin.getMuteManager().mutePlayer(new MuteEntry(playerName, "System", Long.MAX_VALUE, "多次使用违禁词"));
            ReflectionSupport.sendMessage(player, plugin.prefix() + "§c你因多次使用违禁词被自动禁言！");
            badWordCount.remove(playerName);
        }
        ReflectionSupport.sendMessage(player, plugin.prefix() + "§c警告：你的消息中包含违禁词，已被替换为「喵」。");
        plugin.getWarnManager().warnPlayer(playerName, playerName, "使用违禁词");
        return true;
    }

    private String replacement(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append("喵");
        }
        return builder.toString();
    }

    private void handleVpnDetection(Object player, Object server, String playerName, String ip) {
        String prefix = plugin.prefix();
        String action = plugin.getConfigString("vpn-detection.action", "warn");
        if ("ban".equalsIgnoreCase(action)) {
            String banDurationStr = plugin.getConfigString("vpn-detection.ban-duration", "7d");
            String banReason = plugin.getConfigString("vpn-detection.ban-reason", "使用代理/VPN 登录");
            long duration = TimeUtils.parseTime(banDurationStr);
            if (duration <= 0) duration = TimeUtils.daysToMillis(7);
            plugin.getBanManager().tryBanIp(new BanIpEntry(ip, "VPN-Detection", TimeUtils.calculateEndTime(duration), banReason, false));
            ReflectionSupport.kick(player, "§c检测到代理/VPN 连接\n\n§f" + banReason + "\n§e请联系管理员解决");
            notifyOperators(server, "§7[§cVPN检测§7] " + prefix + "§c" + playerName + " §e因使用代理/VPN 已被自动封禁");
            ReflectionSupport.sendConsoleMessage(server, "§7[§cVPN检测§7] " + playerName + " 因使用代理/VPN 已被自动封禁 (IP: " + ip + ")");
        } else if ("kick".equalsIgnoreCase(action)) {
            String kickMessage = plugin.getConfigString("vpn-detection.kick-message", "请关闭代理/VPN后重新加入");
            ReflectionSupport.kick(player, "§c检测到代理/VPN 连接\n\n§f" + kickMessage);
            notifyOperators(server, "§7[§cVPN检测§7] " + prefix + "§e" + playerName + " §e因使用代理/VPN 已被踢出");
            ReflectionSupport.sendConsoleMessage(server, "§7[§cVPN检测§7] " + playerName + " 因使用代理/VPN 被踢出 (IP: " + ip + ")");
        } else {
            notifyOperators(server, "§7[§cVPN检测§7] " + prefix + "§e" + playerName + " §c可能正在使用代理/VPN 登录");
            ReflectionSupport.sendConsoleMessage(server, "§7[§cVPN检测§7] " + playerName + " 可能正在使用代理/VPN (IP: " + ip + ")");
        }
    }

    public void onPlayerLeave(Object player) {
        badWordCount.remove(ReflectionSupport.playerName(player));
    }

    private void notifyOperators(Object server, String message) {
        for (Object player : ReflectionSupport.onlinePlayers(server)) {
            if (ReflectionSupport.hasPermission(player, 2)) {
                ReflectionSupport.sendMessage(player, message);
            }
        }
    }
}
