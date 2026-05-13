package org.leng.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.object.WarnEntry;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class StorageMigrationManager {
    private final Lengbanlist plugin;
    private final DatabaseManager databaseManager;

    public StorageMigrationManager(Lengbanlist plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void migrateYamlIfNeeded() {
        migratePlayerIps();
        migrateBans();
        migrateIpBans();
        migrateMutes();
        migrateWarnings();
        migrateReports();
    }

    private void migratePlayerIps() {
        File file = new File(plugin.getDataFolder(), "ip.yml");
        if (!file.exists()) return;
        int count = 0;
        for (String entry : load(file).getStringList("ip")) {
            int index = entry.indexOf(':');
            if (index <= 0 || index == entry.length() - 1) {
                warn(file, entry, null);
                continue;
            }
            databaseManager.upsertPlayerIp(entry.substring(0, index), entry.substring(index + 1), System.currentTimeMillis());
            count++;
        }
        finish("yaml.ip.migrated", file, count);
    }

    private void migrateBans() {
        File file = new File(plugin.getDataFolder(), "ban-list.yml");
        if (!file.exists()) return;
        int count = 0;
        for (String entry : load(file).getStringList("ban-list")) {
            ParsedEntry parsed = parseEntry(entry, 3, true);
            if (parsed == null) {
                warn(file, entry, null);
                continue;
            }
            try {
                databaseManager.upsertBan(new BanEntry(parsed.parts.get(0), parsed.parts.get(1), Long.parseLong(parsed.parts.get(2)), parsed.reason, parsed.flag));
                count++;
            } catch (Exception e) {
                warn(file, entry, e);
            }
        }
        finish("yaml.bans.migrated", file, count);
    }

    private void migrateIpBans() {
        File file = new File(plugin.getDataFolder(), "banip-list.yml");
        if (!file.exists()) return;
        int count = 0;
        for (String entry : load(file).getStringList("banip-list")) {
            ParsedEntry parsed = parseEntry(entry, 3, true);
            if (parsed == null) {
                warn(file, entry, null);
                continue;
            }
            try {
                databaseManager.upsertIpBan(new BanIpEntry(parsed.parts.get(0), parsed.parts.get(1), Long.parseLong(parsed.parts.get(2)), parsed.reason, parsed.flag));
                count++;
            } catch (Exception e) {
                warn(file, entry, e);
            }
        }
        finish("yaml.ip_bans.migrated", file, count);
    }

    private void migrateMutes() {
        File file = new File(plugin.getDataFolder(), "mute-list.yml");
        if (!file.exists()) return;
        int count = 0;
        for (String entry : load(file).getStringList("mute-list")) {
            ParsedEntry parsed = parseEntry(entry, 3, false);
            if (parsed == null) {
                warn(file, entry, null);
                continue;
            }
            try {
                databaseManager.upsertMute(new MuteEntry(parsed.parts.get(0), parsed.parts.get(1), Long.parseLong(parsed.parts.get(2)), parsed.reason));
                count++;
            } catch (Exception e) {
                warn(file, entry, e);
            }
        }
        finish("yaml.mutes.migrated", file, count);
    }

    private void migrateWarnings() {
        File file = new File(plugin.getDataFolder(), "warn-list.yml");
        if (!file.exists()) return;
        FileConfiguration config = load(file);
        List<String> entries = new ArrayList<>();
        entries.addAll(config.getStringList("warnings"));
        entries.addAll(config.getStringList("players"));
        int count = 0;
        for (String entry : entries) {
            ParsedEntry parsed = parseEntry(entry, 3, true);
            if (parsed == null) {
                warn(file, entry, null);
                continue;
            }
            try {
                String player = parsed.parts.get(0);
                String staff = parsed.parts.get(1);
                long time = Long.parseLong(parsed.parts.get(2));
                WarnEntry warnEntry = new WarnEntry(stableId(player + "|" + staff + "|" + time + "|" + parsed.reason + "|" + parsed.flag), player, staff, time, parsed.reason);
                if (parsed.flag) {
                    warnEntry.revoke();
                }
                databaseManager.upsertWarning(warnEntry);
                count++;
            } catch (Exception e) {
                warn(file, entry, e);
            }
        }
        finish("yaml.warnings.migrated", file, count);
    }

    private void migrateReports() {
        File file = new File(plugin.getDataFolder(), "reports.yml");
        if (!file.exists()) return;
        FileConfiguration config = load(file);
        ConfigurationSection section = config.getConfigurationSection("reports");
        if (section == null) {
            finish("yaml.reports.migrated", file, 0);
            return;
        }
        int count = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection reportSection = section.getConfigurationSection(key);
            if (reportSection == null) continue;
            try {
                Map<String, Object> values = reportSection.getValues(false);
                if (!values.containsKey("id")) {
                    values.put("id", key);
                }
                ReportEntry report = ReportEntry.deserialize(values);
                if (report != null) {
                    databaseManager.upsertReport(report);
                    count++;
                }
            } catch (Exception e) {
                warn(file, key, e);
            }
        }
        finish("yaml.reports.migrated", file, count);
    }

    private ParsedEntry parseEntry(String entry, int fixedPrefixFields, boolean booleanSuffix) {
        String[] raw = entry.split(":");
        int minimum = fixedPrefixFields + 1 + (booleanSuffix ? 1 : 0);
        if (raw.length < minimum) return null;
        ParsedEntry parsed = new ParsedEntry();
        for (int i = 0; i < fixedPrefixFields; i++) {
            parsed.parts.add(raw[i]);
        }
        int reasonEnd = raw.length;
        if (booleanSuffix) {
            String last = raw[raw.length - 1];
            if (!"true".equalsIgnoreCase(last) && !"false".equalsIgnoreCase(last)) {
                return null;
            }
            parsed.flag = Boolean.parseBoolean(last);
            reasonEnd--;
        }
        StringBuilder reason = new StringBuilder();
        for (int i = fixedPrefixFields; i < reasonEnd; i++) {
            if (i > fixedPrefixFields) reason.append(':');
            reason.append(raw[i]);
        }
        parsed.reason = reason.toString();
        return parsed;
    }

    private FileConfiguration load(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }

    private void finish(String metaKey, File file, int count) {
        databaseManager.setMeta(metaKey, String.valueOf(System.currentTimeMillis()));
        plugin.getLogger().info("已从 " + file.getName() + " 迁移 " + count + " 条旧数据到数据库。");
    }

    private void warn(File file, String entry, Exception e) {
        String message = "迁移 " + file.getName() + " 时跳过无法解析的数据: " + entry;
        if (e == null) {
            plugin.getLogger().warning(message);
        } else {
            plugin.getLogger().log(Level.WARNING, message, e);
        }
    }

    private String stableId(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] bytes = digest.digest(value.getBytes("UTF-8"));
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static class ParsedEntry {
        private final List<String> parts = new ArrayList<>();
        private String reason;
        private boolean flag;
    }
}
