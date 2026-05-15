package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.object.WarnEntry;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class DatabaseManager {
    private final Lengbanlist plugin;
    private Connection connection;
    private boolean mysql;

    public DatabaseManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws SQLException {
        String type = plugin.getConfig().getString("database.type", "sqlite");
        if (type == null || type.trim().isEmpty()) {
            type = "sqlite";
        }
        if ("yml".equalsIgnoreCase(type) || "yaml".equalsIgnoreCase(type)) {
            plugin.getLogger().warning("database.type: yml 已废弃，将自动使用 sqlite 并迁移旧 YAML 数据。");
            type = "sqlite";
        }

        if ("sqlite".equalsIgnoreCase(type)) {
            mysql = false;
            String fileName = plugin.getConfig().getString("database.sqlite.file", "lengbanlist.db");
            File dbFile = new File(plugin.getDataFolder(), fileName == null || fileName.trim().isEmpty() ? "lengbanlist.db" : fileName);
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            execute("PRAGMA foreign_keys = ON");
            execute("PRAGMA journal_mode = WAL");
            execute("PRAGMA busy_timeout = 5000");
        } else if ("mysql".equalsIgnoreCase(type)) {
            mysql = true;
            String host = plugin.getConfig().getString("database.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("database.mysql.port", 3306);
            String database = plugin.getConfig().getString("database.mysql.database", "lengbanlist");
            String username = plugin.getConfig().getString("database.mysql.username", "root");
            String password = plugin.getConfig().getString("database.mysql.password", "password");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8";
            connection = DriverManager.getConnection(url, username, password);
            execute("SELECT 1");
        } else {
            throw new SQLException("未知 database.type: " + type);
        }

        ensureSchema();
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isMySql() {
        return mysql;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "关闭数据库连接时出错", e);
            }
        }
    }

    public void ensureSchema() throws SQLException {
        execute("CREATE TABLE IF NOT EXISTS schema_meta (meta_key " + textPrimaryKey() + ", meta_value " + textType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS player_ips (player_name " + textPrimaryKey() + ", ip " + textType() + " NOT NULL, updated_at " + longType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS bans (target " + textPrimaryKey() + ", staff " + textType() + " NOT NULL, end_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, is_auto " + booleanType() + " NOT NULL DEFAULT 0)");
        execute("CREATE TABLE IF NOT EXISTS ip_bans (ip " + textPrimaryKey() + ", staff " + textType() + " NOT NULL, end_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, is_auto " + booleanType() + " NOT NULL DEFAULT 0)");
        execute("CREATE TABLE IF NOT EXISTS mutes (target " + textPrimaryKey() + ", staff " + textType() + " NOT NULL, end_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS warnings (id " + textPrimaryKey() + ", player " + textType() + " NOT NULL, staff " + textType() + " NOT NULL, warn_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, revoked " + booleanType() + " NOT NULL DEFAULT 0)");
        execute("CREATE TABLE IF NOT EXISTS reports (id " + textPrimaryKey() + ", target " + textType() + " NOT NULL, reporter " + textType() + " NOT NULL, reason " + textType() + " NOT NULL, status " + varcharType(32) + " NOT NULL DEFAULT '未处理', timestamp " + longType() + " NOT NULL)");

        addColumnIfMissing("schema_meta", "meta_value", nullableTextType());
        addColumnIfMissing("player_ips", "ip", nullableTextType());
        addColumnIfMissing("player_ips", "updated_at", longType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("bans", "staff", nullableTextType());
        addColumnIfMissing("bans", "end_time", longType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("bans", "reason", nullableTextType());
        addColumnIfMissing("bans", "is_auto", booleanType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("ip_bans", "staff", nullableTextType());
        addColumnIfMissing("ip_bans", "end_time", longType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("ip_bans", "reason", nullableTextType());
        addColumnIfMissing("ip_bans", "is_auto", booleanType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("mutes", "staff", nullableTextType());
        addColumnIfMissing("mutes", "end_time", longType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("mutes", "reason", nullableTextType());
        addColumnIfMissing("warnings", "player", nullableTextType());
        addColumnIfMissing("warnings", "staff", nullableTextType());
        addColumnIfMissing("warnings", "warn_time", longType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("warnings", "reason", nullableTextType());
        addColumnIfMissing("warnings", "revoked", booleanType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("reports", "target", nullableTextType());
        addColumnIfMissing("reports", "reporter", nullableTextType());
        addColumnIfMissing("reports", "reason", nullableTextType());
        addColumnIfMissing("reports", "status", varcharType(32) + " NOT NULL DEFAULT '未处理'");
        addColumnIfMissing("reports", "timestamp", longType() + " NOT NULL DEFAULT 0");

        createIndexIfMissing("warnings", "idx_warnings_player", "player");
        createIndexIfMissing("reports", "idx_reports_target", "target");
        createIndexIfMissing("reports", "idx_reports_reporter", "reporter");
        setMeta("schema.version", "1");
    }

    public void upsertPlayerIp(String playerName, String ip, long updatedAt) {
        executeUpdate(upsertSql("player_ips", "player_name", new String[]{"player_name", "ip", "updated_at"}, new String[]{"ip", "updated_at"}), playerName, ip, updatedAt);
    }

    public String getPlayerIp(String playerName) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT ip FROM player_ips WHERE player_name = ?")) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("ip") : null;
            }
        } catch (SQLException e) {
            logSql(e);
            return null;
        }
    }

    public List<String> getPlayersByIp(String ip) {
        List<String> players = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT player_name FROM player_ips WHERE ip = ? ORDER BY player_name")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    players.add(rs.getString("player_name"));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return players;
    }

    public void upsertBan(BanEntry entry) {
        executeUpdate(upsertSql("bans", "target", new String[]{"target", "staff", "end_time", "reason", "is_auto"}, new String[]{"staff", "end_time", "reason", "is_auto"}), entry.getTarget(), entry.getStaff(), entry.getTime(), entry.getReason(), entry.isAuto());
    }

    public void deleteBan(String target) {
        executeUpdate("DELETE FROM bans WHERE target = ?", target);
    }

    public boolean isPlayerBanned(String target) {
        return exists("SELECT 1 FROM bans WHERE target = ?", target);
    }

    public BanEntry getBan(String target) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason, is_auto FROM bans WHERE target = ?")) {
            ps.setString(1, target);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readBan(rs) : null;
            }
        } catch (SQLException e) {
            logSql(e);
            return null;
        }
    }

    public List<BanEntry> getBans() {
        List<BanEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason, is_auto FROM bans ORDER BY target"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.add(readBan(rs));
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public List<BanEntry> getBansByPlayer(String player) {
        List<BanEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason, is_auto FROM bans WHERE LOWER(target) = LOWER(?) ORDER BY end_time DESC")) {
            ps.setString(1, player);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readBan(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public void upsertIpBan(BanIpEntry entry) {
        executeUpdate(upsertSql("ip_bans", "ip", new String[]{"ip", "staff", "end_time", "reason", "is_auto"}, new String[]{"staff", "end_time", "reason", "is_auto"}), entry.getIp(), entry.getStaff(), entry.getTime(), entry.getReason(), entry.isAuto());
    }

    public void deleteIpBan(String ip) {
        executeUpdate("DELETE FROM ip_bans WHERE ip = ?", ip);
    }

    public boolean isIpBanned(String ip) {
        return exists("SELECT 1 FROM ip_bans WHERE ip = ?", ip);
    }

    public BanIpEntry getIpBan(String ip) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT ip, staff, end_time, reason, is_auto FROM ip_bans WHERE ip = ?")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readIpBan(rs) : null;
            }
        } catch (SQLException e) {
            logSql(e);
            return null;
        }
    }

    public List<BanIpEntry> getIpBans() {
        List<BanIpEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT ip, staff, end_time, reason, is_auto FROM ip_bans ORDER BY ip"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.add(readIpBan(rs));
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public void upsertMute(MuteEntry entry) {
        executeUpdate(upsertSql("mutes", "target", new String[]{"target", "staff", "end_time", "reason"}, new String[]{"staff", "end_time", "reason"}), entry.getTarget(), entry.getStaff(), entry.getTime(), entry.getReason());
    }

    public void deleteMute(String target) {
        executeUpdate("DELETE FROM mutes WHERE target = ?", target);
    }

    public MuteEntry getMute(String target) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason FROM mutes WHERE target = ?")) {
            ps.setString(1, target);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readMute(rs) : null;
            }
        } catch (SQLException e) {
            logSql(e);
            return null;
        }
    }

    public List<MuteEntry> getMutes() {
        List<MuteEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason FROM mutes ORDER BY target"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.add(readMute(rs));
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public List<MuteEntry> getMutesByPlayer(String player) {
        List<MuteEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason FROM mutes WHERE LOWER(target) = LOWER(?) ORDER BY end_time DESC")) {
            ps.setString(1, player);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readMute(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public void upsertWarning(WarnEntry entry) {
        executeUpdate(upsertSql("warnings", "id", new String[]{"id", "player", "staff", "warn_time", "reason", "revoked"}, new String[]{"player", "staff", "warn_time", "reason", "revoked"}), entry.getId(), entry.getPlayer(), entry.getStaff(), entry.getTime(), entry.getReason(), entry.isRevoked());
    }

    public void updateWarningRevoked(String id, boolean revoked) {
        executeUpdate("UPDATE warnings SET revoked = ? WHERE id = ?", revoked, id);
    }

    public List<WarnEntry> getWarnings(String player, boolean activeOnly) {
        List<WarnEntry> entries = new ArrayList<>();
        String sql = "SELECT id, player, staff, warn_time, reason, revoked FROM warnings WHERE LOWER(player) = LOWER(?)" + (activeOnly ? " AND revoked = 0" : "") + " ORDER BY warn_time ASC, id ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readWarning(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public List<String> getWarnedPlayers() {
        List<String> players = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT player FROM warnings")) {
            while (rs.next()) {
                players.add(rs.getString("player"));
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return players;
    }

    public void upsertReport(ReportEntry entry) {
        executeUpdate(upsertSql("reports", "id", new String[]{"id", "target", "reporter", "reason", "status", "timestamp"}, new String[]{"target", "reporter", "reason", "status", "timestamp"}), entry.getId(), entry.getTarget(), entry.getReporter(), entry.getReason(), status(entry.getStatus()), entry.getTimestamp());
    }

    public void deleteReport(String id) {
        executeUpdate("DELETE FROM reports WHERE id = ?", id);
    }

    public ReportEntry getReport(String id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id, target, reporter, reason, status, timestamp FROM reports WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readReport(rs) : null;
            }
        } catch (SQLException e) {
            logSql(e);
            return null;
        }
    }

    public List<ReportEntry> getPendingReports() {
        List<ReportEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT id, target, reporter, reason, status, timestamp FROM reports WHERE status IS NULL OR status <> ? ORDER BY timestamp ASC")) {
            ps.setString(1, "已关闭");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readReport(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public int getPendingReportCount() {
        return count("SELECT COUNT(*) FROM reports WHERE status IS NULL OR status <> ?", "已关闭");
    }

    public int getReportCount(String target) {
        return count("SELECT COUNT(*) FROM reports WHERE target = ?", target);
    }

    public List<ReportEntry> getReportsByReporterAndTarget(String reporter, String target) {
        List<ReportEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT id, target, reporter, reason, status, timestamp FROM reports WHERE reporter = ? AND target = ? ORDER BY timestamp DESC")) {
            ps.setString(1, reporter);
            ps.setString(2, target);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readReport(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public String getMeta(String key) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT meta_value FROM schema_meta WHERE meta_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("meta_value") : null;
            }
        } catch (SQLException e) {
            logSql(e);
            return null;
        }
    }

    public void setMeta(String key, String value) {
        executeUpdate(upsertSql("schema_meta", "meta_key", new String[]{"meta_key", "meta_value"}, new String[]{"meta_value"}), key, value);
    }

    private BanEntry readBan(ResultSet rs) throws SQLException {
        return new BanEntry(value(rs, "target"), value(rs, "staff"), rs.getLong("end_time"), value(rs, "reason"), rs.getBoolean("is_auto"));
    }

    private BanIpEntry readIpBan(ResultSet rs) throws SQLException {
        return new BanIpEntry(value(rs, "ip"), value(rs, "staff"), rs.getLong("end_time"), value(rs, "reason"), rs.getBoolean("is_auto"));
    }

    private MuteEntry readMute(ResultSet rs) throws SQLException {
        return new MuteEntry(value(rs, "target"), value(rs, "staff"), rs.getLong("end_time"), value(rs, "reason"));
    }

    private WarnEntry readWarning(ResultSet rs) throws SQLException {
        WarnEntry entry = new WarnEntry(value(rs, "id"), value(rs, "player"), value(rs, "staff"), rs.getLong("warn_time"), value(rs, "reason"));
        if (rs.getBoolean("revoked")) {
            entry.revoke();
        }
        return entry;
    }

    private ReportEntry readReport(ResultSet rs) throws SQLException {
        return new ReportEntry(value(rs, "target"), value(rs, "reporter"), value(rs, "reason"), value(rs, "id"), rs.getLong("timestamp"), status(rs.getString("status")));
    }

    private String value(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? "" : value;
    }

    private String upsertSql(String table, String keyColumn, String[] columns, String[] updateColumns) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(table).append(" (").append(join(columns)).append(") VALUES (").append(placeholders(columns.length)).append(") ");
        if (mysql) {
            sql.append("ON DUPLICATE KEY UPDATE ");
            for (int i = 0; i < updateColumns.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(updateColumns[i]).append(" = VALUES(").append(updateColumns[i]).append(")");
            }
        } else {
            sql.append("ON CONFLICT(").append(keyColumn).append(") DO UPDATE SET ");
            for (int i = 0; i < updateColumns.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(updateColumns[i]).append(" = excluded.").append(updateColumns[i]);
            }
        }
        return sql.toString();
    }

    private void executeUpdate(String sql, Object... values) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setValues(ps, values);
            ps.executeUpdate();
        } catch (SQLException e) {
            logSql(e);
        }
    }

    private boolean exists(String sql, Object value) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logSql(e);
            return false;
        }
    }

    private int count(String sql, Object value) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logSql(e);
            return 0;
        }
    }

    private void setValues(PreparedStatement ps, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof Boolean) {
                ps.setBoolean(i + 1, (Boolean) value);
            } else {
                ps.setObject(i + 1, value);
            }
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        if (!columnExists(table, column)) {
            execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns(null, null, table, column)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = metaData.getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }

    private void createIndexIfMissing(String table, String index, String column) throws SQLException {
        if (!indexExists(table, index)) {
            execute("CREATE INDEX " + index + " ON " + table + " (" + column + ")");
        }
    }

    private boolean indexExists(String table, String index) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getIndexInfo(null, null, table, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (index.equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String textPrimaryKey() {
        return mysql ? "VARCHAR(191) PRIMARY KEY" : "TEXT PRIMARY KEY";
    }

    private String textType() {
        return mysql ? "TEXT" : "TEXT";
    }

    private String varcharType(int length) {
        return mysql ? "VARCHAR(" + length + ")" : "TEXT";
    }

    private String nullableTextType() {
        return mysql ? "TEXT" : "TEXT NOT NULL DEFAULT ''";
    }

    private String longType() {
        return mysql ? "BIGINT" : "INTEGER";
    }

    private String booleanType() {
        return mysql ? "BOOLEAN" : "INTEGER";
    }

    private String placeholders(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(", ");
            builder.append("?");
        }
        return builder.toString();
    }

    private String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) builder.append(", ");
            builder.append(values[i]);
        }
        return builder.toString();
    }

    private String status(String status) {
        return status == null || status.trim().isEmpty() ? "未处理" : status;
    }

    private void logSql(SQLException e) {
        plugin.getLogger().log(Level.SEVERE, "数据库操作失败", e);
    }
}
