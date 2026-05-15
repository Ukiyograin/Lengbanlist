package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.WarnEntry;
import org.leng.utils.TimeUtils;

import java.util.List;
import java.util.stream.Collectors;

/** 警告管理，处理警告记录、LBAC 自动封禁/解封逻辑。 */
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

    private void checkAutoBan(String player) {
        long now = System.currentTimeMillis();
        long timeWindow = 30L * 24 * 60 * 60 * 1000;
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

            plugin.getBanManager().banPlayer(banEntry);
            String message = String.format("§6[LBAC] §e%s §c因30天内累计%d次警告被自动封禁§a%s §6<此封禁由系统决定>", player, validWarnings.size(), formattedDuration);
            plugin.getServer().broadcastMessage(message);
        }
    }

    private int extractTriggerCount(String reason) {
        try {
            int start = reason.lastIndexOf("第") + 1;
            int end = reason.indexOf("次触发");
            if (start > 0 && end > start) {
                return Integer.parseInt(reason.substring(start, end));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public void checkUnbanIfNecessary(String player) {
        long now = System.currentTimeMillis();
        long timeWindow = 30L * 24 * 60 * 60 * 1000;
        List<WarnEntry> validWarnings = getAllWarnings(player).stream()
                .filter(e -> (now - e.getTime()) <= timeWindow)
                .collect(Collectors.toList());

        if (validWarnings.size() < 3 && plugin.getBanManager().isBanned(player, "LBAC")) {
            plugin.getBanManager().unbanPlayer(player);
            String message = String.format("§6[LBAC] §e%s §a因警告次数减少至%d次，自动解封", player, validWarnings.size());
            plugin.getServer().broadcastMessage(message);
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

    public void loadFromConfig(org.bukkit.configuration.file.FileConfiguration config) {
        for (String entry : config.getStringList("warnings")) {
            String[] parts = entry.split(":");
            if (parts.length >= 5) {
                WarnEntry warn = new WarnEntry(parts[0], parts[1], Long.parseLong(parts[2]), parts[3]);
                if (Boolean.parseBoolean(parts[4])) {
                    warn.revoke();
                }
                db.upsertWarning(warn);
            }
        }
    }

    public void saveToConfig(org.bukkit.configuration.file.FileConfiguration config) {}
}
