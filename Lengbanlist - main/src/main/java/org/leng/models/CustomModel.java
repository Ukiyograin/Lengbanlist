package org.leng.models;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomModel implements Model {
    private final String name;
    private final FileConfiguration config;

    public CustomModel(String name, FileConfiguration config) {
        this.name = name;
        this.config = config;
    }

    @Override
    public String getName() {
        return name;
    }

    /** 构建占位符 map（Java 8 兼容，替代 Map.of） */
    private static Map<String, String> placeholders(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            if (kv[i + 1] != null) {
                map.put(kv[i], kv[i + 1]);
            }
        }
        return map;
    }

    private String msg(String key) {
        return msg(key, Collections.<String, String>emptyMap());
    }

    /**
     * 天数文案（可被模型 YAML 覆盖）：
     * {@code days-forever} 永久文案（默认"永久"）、{@code days-suffix} 单位后缀（默认" 天"）。
     * 让 English 等非中文模型输出 "permanently" / "N days"。
     */
    private String banDays(int days) {
        String forever = config.getString("days-forever", "永久");
        String suffix = config.getString("days-suffix", " 天");
        return days == Integer.MAX_VALUE ? forever : days + suffix;
    }

    private String msg(String key, Map<String, String> placeholders) {
        String template = config.getString("messages." + key);
        if (template == null || template.isEmpty()) {
            return "§c[模型 " + name + " 缺少配置: " + key + "]";
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    @Override
    public void showHelp(CommandSender sender) {
        List<String> helpLines = config.getStringList("help");
        if (helpLines.isEmpty()) {
            Utils.sendMessage(sender, "§6" + name + " 模型 - 没有自定义帮助信息");
            return;
        }
        String version = Lengbanlist.getInstance().getPluginVersion();
        for (String line : helpLines) {
            Utils.sendMessage(sender, line.replace("{version}", version == null ? "" : version));
        }
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        // enabled-on/enabled-off 可被模型 YAML 覆盖（English 模型用 enabled/disabled）
        String on = config.getString("enabled-on", "开启");
        String off = config.getString("enabled-off", "关闭");
        return msg("toggle-broadcast", placeholders("enabled", enabled ? on : off));
    }

    @Override
    public String reloadConfig() {
        return msg("reload-config");
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return msg("add-ban", placeholders("player", player, "days", banDays(days), "reason", reason));
    }

    @Override
    public String removeBan(String player) {
        return msg("remove-ban", placeholders("player", player));
    }

    @Override
    public String addMute(String player, String reason) {
        return msg("add-mute", placeholders("player", player, "reason", reason));
    }

    @Override
    public String removeMute(String player) {
        return msg("remove-mute", placeholders("player", player));
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return msg("add-ban-ip", placeholders("ip", ip, "days", banDays(days), "reason", reason));
    }

    @Override
    public String removeBanIp(String ip) {
        return msg("remove-ban-ip", placeholders("ip", ip));
    }

    @Override
    public String addWarn(String player, String reason) {
        return msg("add-warn", placeholders("player", player, "reason", reason));
    }

    @Override
    public String removeWarn(String player) {
        return msg("remove-warn", placeholders("player", player));
    }

    @Override
    public String getKickMessage(String reason) {
        return msg("get-kick-message", placeholders("reason", reason));
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return msg("on-kick-success", placeholders("player", playerName, "reason", reason));
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return msg("history-empty", placeholders("player", player));
        }
        StringBuilder sb = new StringBuilder();
        sb.append(msg("history-header", placeholders("player", player)));
        for (String entry : entries) {
            sb.append("\n").append(msg("history-entry-format", placeholders("entry", entry)));
        }
        // 可选 footer（从内置 Java 模型迁移时保持 4 段结构）
        String footer = msg("history-footer", placeholders("player", player));
        if (footer != null && !footer.startsWith("§c[模型") && !footer.trim().isEmpty()) {
            sb.append("\n").append(footer);
        }
        return sb.toString();
    }

    @Override
    public String onMuteCommandBlocked() {
        return msg("mute-command-blocked");
    }

    @Override
    public String onWarnOffline(String player, String reason) {
        return msg("warn-offline", placeholders("player", player, "reason", reason));
    }

    @Override
    public String getPendingWarningsNotice(int count) {
        return msg("pending-warnings-notice", placeholders("count", String.valueOf(count)));
    }

    @Override
    public String getExpiryReminder(String type, String target, String remaining) {
        return msg("expiry-reminder", placeholders("type", type, "target", target, "remaining", remaining));
    }

    @Override
    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return msg("escalated-ban", placeholders("player", player, "count", String.valueOf(offenseCount), "duration", duration));
    }

    @Override
    public String getAltsResult(String player, int count) {
        return msg("alts-result", placeholders("player", player, "count", String.valueOf(count)));
    }

    @Override
    public String getNoAlts(String player) {
        return msg("no-alts", placeholders("player", player));
    }

    @Override
    public String onReportBan(String player, String duration) {
        return msg("on-report-ban", placeholders("player", player, "duration", duration));
    }

    @Override
    public String getExportResult(int count) {
        return msg("export-result", placeholders("count", String.valueOf(count)));
    }

    @Override
    public String getVerifyResult(boolean valid, int count) {
        String key = valid ? "verify-result-valid" : "verify-result-invalid";
        return msg(key, placeholders("count", String.valueOf(count)));
    }

    @Override
    public String getSyncStatus(String detail) {
        return msg("sync-status", placeholders("detail", detail));
    }

    @Override
    public String getImmunityDenied(String target) {
        return msg("immunity-denied", placeholders("target", target));
    }

    @Override
    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return msg("rollback-preview", placeholders("count", String.valueOf(matched), "actor", actor, "time", timeRange));
    }

    @Override
    public String getRollbackResult(int matched, int executed, int skipped) {
        return msg("rollback-result", placeholders("matched", String.valueOf(matched), "executed", String.valueOf(executed), "skipped", String.valueOf(skipped)));
    }

    @Override
    public String getRollbackNoRecords(String actor) {
        return msg("rollback-no-records", placeholders("actor", actor));
    }
}
