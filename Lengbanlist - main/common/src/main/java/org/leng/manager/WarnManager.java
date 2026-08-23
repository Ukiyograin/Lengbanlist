package org.leng.manager;

import org.leng.object.BanEntry;
import org.leng.object.WarnEntry;
import org.leng.platform.LengbanlistPlatform;
import org.leng.utils.TimeUtils;

import java.util.List;
import java.util.stream.Collectors;


public class WarnManager {
    private final LengbanlistPlatform plugin;
    private final DatabaseManager db;

    public WarnManager(LengbanlistPlatform plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void warnPlayer(String player, String staff, String reason) {
        WarnEntry entry = new WarnEntry(player, staff, System.currentTimeMillis(), reason);
        db.upsertWarning(entry);
        checkAutoBan(player);
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

    public boolean unwarnPlayer(String target, int warnId) {
        List<WarnEntry> playerWarnings = getAllWarnings(target);
        if (warnId > 0 && warnId <= playerWarnings.size()) {
            WarnEntry entry = playerWarnings.get(warnId - 1);
            if (!entry.isRevoked()) {
                entry.revoke();
                db.updateWarningRevoked(entry.getId(), true);
                checkUnbanIfNecessary(target);
                return true;
            }
        }
        return false;
    }

    private long warnWindowMillis() {
        long days = plugin.getConfigInt("warn-manager.window-days", 30);
        if (days <= 0) days = 30;
        return days * 24L * 60 * 60 * 1000;
    }

    private void checkAutoBan(String player) {
        long now = System.currentTimeMillis();
        long timeWindow = warnWindowMillis();
        List<WarnEntry> validWarnings = getAllWarnings(player).stream()
                .filter(e -> (now - e.getTime()) <= timeWindow)
                .collect(Collectors.toList());

        if (validWarnings.size() >= 3) {
            int triggerCount = Math.max(1, validWarnings.size() / 3);

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
                plugin.broadcastMessage(message);
            } else {
                logAutomaticMutationFailure("封禁玩家", player, result);
            }
        }
    }

    private int extractTriggerCount(String reason) {
        if (reason == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("第(\\d+)次触发").matcher(reason);
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
        long now = System.currentTimeMillis();
        long timeWindow = warnWindowMillis();
        List<WarnEntry> validWarnings = getAllWarnings(player).stream()
                .filter(e -> (now - e.getTime()) <= timeWindow)
                .collect(Collectors.toList());

        if (validWarnings.size() < 3 && plugin.getBanManager().isBanned(player, "LBAC")) {
            BanManager.BanMutationResult result = plugin.getBanManager().tryUnbanPlayer(player, "LBAC", false);
            if (result.isApplied()) {
                String message = String.format("§6[LBAC] §e%s §a因警告次数减少至%d次，自动解封", player, validWarnings.size());
                plugin.broadcastMessage(message);
            } else {
                logAutomaticMutationFailure("解封玩家", player, result);
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
            case 1: return TimeUtils.daysToMillis(1);
            case 2: return TimeUtils.daysToMillis(7);
            case 3: return TimeUtils.daysToMillis(30);
            case 4: return TimeUtils.daysToMillis(90);
            case 5: return TimeUtils.daysToMillis(180);
            default: return TimeUtils.daysToMillis(365);
        }
    }
}
