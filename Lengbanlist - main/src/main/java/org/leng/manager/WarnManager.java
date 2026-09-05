package org.leng.manager;

import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.WarnEntry;
import org.leng.utils.IpMatcher;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class WarnManager {

    private final Lengbanlist plugin;
    private final DatabaseManager db;

    public WarnManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void warnPlayer(String player, String staff, String reason) {
        WarnEntry entry = new WarnEntry(player, staff, System.currentTimeMillis(), reason);
        db.upsertWarning(entry);
        plugin.getAuditManager().log("警告", staff, player, reason);
        org.bukkit.Bukkit.getPluginManager().callEvent(new org.leng.api.events.LengbanlistWarnEvent(entry));
        checkAutoBan(player);
        if (plugin.isFeatureEnabled("offline-warn") && !player.contains(".")) {
            Player targetPlayer = plugin.getServer().getPlayer(player);
            if (targetPlayer == null) {
                Player staffPlayer = plugin.getServer().getPlayer(staff);
                if (staffPlayer != null) {
                    Utils.sendMessage(staffPlayer, plugin.getModelManager().getCurrentModel().onWarnOffline(player, reason));
                }
            }
        }
    }

    public int countActiveWarnings(String target) {
        return db.getWarnings(target, true).size();
    }

    public List<WarnEntry> getAllWarnings(String target) {
        return db.getWarnings(target, false);
    }

    public List<String> getWarnedPlayers() {
        return db.getWarnedPlayers();
    }

    public List<WarnEntry> getActiveWarnings(String target) {
        long oneDayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        return db.getWarnings(target, true).stream()
                .filter(e -> e.getTime() > oneDayAgo)
                .collect(Collectors.toList());
    }

    public boolean unwarnPlayer(String target, int warnId, org.bukkit.command.CommandSender actor) {
        List<WarnEntry> playerWarnings = getAllWarnings(target);
        if (warnId > 0 && warnId <= playerWarnings.size()) {
            WarnEntry entry = playerWarnings.get(warnId - 1);
            if (!entry.isRevoked()) {
                entry = entry.revoke();
                db.updateWarningRevoked(entry.getId(), true);
                plugin.getAuditManager().log("取消警告", org.leng.utils.Utils.getSenderName(actor), target, "警告ID: " + entry.getId());
                org.bukkit.Bukkit.getPluginManager().callEvent(new org.leng.api.events.LengbanlistUnwarnEvent(target, entry.getId(), org.leng.utils.Utils.getSenderName(actor)));
                checkUnbanIfNecessary(target);
                return true;
            }
        }
        return false;
    }

    private void checkAutoBan(String player) {
        String normalized = IpMatcher.normalizeIpOrCidr(player);
        if (normalized != null) player = normalized;
        long now = System.currentTimeMillis();
        long timeWindow = 30L * 24 * 60 * 60 * 1000;
        List<WarnEntry> validWarnings = getAllWarnings(player).stream()
                .filter(e -> (now - e.getTime()) <= timeWindow)
                .collect(Collectors.toList());

        if (validWarnings.size() >= 3) {
            int triggerCount = Math.max(1, validWarnings.size() / 3);

            if (IpMatcher.isValidIpOrCidr(player)) {
                BanIpEntry existingIpBan = plugin.getBanManager().getBanIpEntry(player);
                if (existingIpBan != null && existingIpBan.getReason().contains("LBAC")) {
                    int prevTrigger = extractTriggerCount(existingIpBan.getReason());
                    if (triggerCount <= prevTrigger) return;
                }

                long banDuration = calculateBanDuration(triggerCount);
                String formattedDuration = TimeUtils.formatDuration(banDuration);
                BanIpEntry ipBanEntry = new BanIpEntry(
                        player,
                        "LBAC",
                        now + banDuration,
                        String.format("LBAC自动封禁（累计%d次警告，第%d次触发）", validWarnings.size(), triggerCount),
                        true
                );

                BanManager.BanMutationResult result = plugin.getBanManager().tryBanIp(ipBanEntry);
                if (result.isApplied()) {
                    String message = String.format("§6[LBAC] §e%s §c因30天内累计%d次警告被自动封禁§a%s §6<此封禁由系统决定>", player, validWarnings.size(), formattedDuration);
                    plugin.getServer().broadcastMessage(message);
                } else {
                    logAutomaticMutationFailure("封禁 IP", player, result);
                }
            } else {
                BanEntry existingBan = plugin.getBanManager().getBanEntry(player);
                if (existingBan != null && existingBan.getReason().contains("LBAC")) {
                    int prevTrigger = extractTriggerCount(existingBan.getReason());
                    if (triggerCount <= prevTrigger) return;
                }

                long banDuration = calculateBanDuration(triggerCount);
                String formattedDuration = TimeUtils.formatDuration(banDuration);
                BanEntry banEntry = new BanEntry(
                        player,
                        "LBAC",
                        now + banDuration,
                        String.format("LBAC自动封禁（累计%d次警告，第%d次触发）", validWarnings.size(), triggerCount),
                        true
                );

                BanManager.BanMutationResult result = plugin.getBanManager().tryBanPlayer(banEntry);
                if (result.isApplied()) {
                    String message = String.format("§6[LBAC] §e%s §c因30天内累计%d次警告被自动封禁§a%s §6<此封禁由系统决定>", player, validWarnings.size(), formattedDuration);
                    plugin.getServer().broadcastMessage(message);
                } else {
                    logAutomaticMutationFailure("封禁玩家", player, result);
                }
            }
        }
    }

    private static final Pattern TRIGGER_COUNT_PATTERN = Pattern.compile("第(\\d+)次触发");

    private int extractTriggerCount(String reason) {
        if (reason == null) return 0;
        Matcher m = TRIGGER_COUNT_PATTERN.matcher(reason);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public void checkUnbanIfNecessary(String player) {
        String normalized = IpMatcher.normalizeIpOrCidr(player);
        if (normalized != null) player = normalized;
        long now = System.currentTimeMillis();
        long timeWindow = 30L * 24 * 60 * 60 * 1000;
        List<WarnEntry> validWarnings = getAllWarnings(player).stream()
                .filter(e -> (now - e.getTime()) <= timeWindow)
                .collect(Collectors.toList());

        if (validWarnings.size() < 3) {
            if (IpMatcher.isValidIpOrCidr(player)) {
                if (plugin.getBanManager().isIpBanned(player)) {
                    BanManager.BanMutationResult result = plugin.getBanManager()
                            .tryUnbanIp(player, "LBAC", false);
                    if (result.isApplied()) {
                        String message = String.format("§6[LBAC] §e%s §a因警告次数减少至%d次，自动解封", player, validWarnings.size());
                        plugin.getServer().broadcastMessage(message);
                    } else {
                        logAutomaticMutationFailure("解封 IP", player, result);
                    }
                }
            } else if (plugin.getBanManager().isBanned(player, "LBAC")) {
                BanManager.BanMutationResult result = plugin.getBanManager()
                        .tryUnbanPlayer(player, "LBAC", false);
                if (result.isApplied()) {
                    String message = String.format("§6[LBAC] §e%s §a因警告次数减少至%d次，自动解封", player, validWarnings.size());
                    plugin.getServer().broadcastMessage(message);
                } else {
                    logAutomaticMutationFailure("解封玩家", player, result);
                }
            }
        }
    }

    private void logAutomaticMutationFailure(String action, String target,
                                             BanManager.BanMutationResult result) {
        if (result == BanManager.BanMutationResult.DATABASE_ERROR) {
            plugin.getLogger().warning("LBAC 自动" + action + "失败: target=" + target
                    + ", result=" + result);
        }
    }

    public long calculateBanDuration(int triggerCount) {
        switch (triggerCount) {
            case 1:
                return TimeUtils.daysToMillis(1);
            case 2:
                return TimeUtils.daysToMillis(7);
            case 3:
                return TimeUtils.daysToMillis(30);
            case 4:
                return TimeUtils.daysToMillis(90);
            case 5:
                return TimeUtils.daysToMillis(180);
            default:
                return TimeUtils.daysToMillis(365);
        }
    }

    public void loadFromConfig(org.bukkit.configuration.file.FileConfiguration config) {
        for (String entry : config.getStringList("warnings")) {
            String[] parts = entry.split(":");
            if (parts.length >= 5) {
                WarnEntry warn = new WarnEntry(parts[0], parts[1], Long.parseLong(parts[2]), parts[3]);
                if (Boolean.parseBoolean(parts[4])) {
                    warn = warn.revoke();
                }
                db.upsertWarning(warn);
            }
        }
    }

    public void saveToConfig(org.bukkit.configuration.file.FileConfiguration config) {
    }

}
