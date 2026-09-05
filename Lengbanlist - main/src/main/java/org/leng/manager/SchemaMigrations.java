package org.leng.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Schema 版本化迁移注册表。
 * 新增迁移：在 {@link #MIGRATIONS} 末尾追加一条即可（version 必须严格递增）。
 *
 * 工作流：
  <ol>
  <li>{@link #runAll(DatabaseManager, String)} 比较当前 DB 版本与最新版本</li>
  <li>按版本顺序执行缺失的迁移</li>
  <li>每条迁移内部需要自己保证原子性（必要时启用事务）</li>
  </ol>
 */
public final class SchemaMigrations {

    public static final int CURRENT_VERSION = 4;

    private static final TreeMap<Integer, Migration> MIGRATIONS = new TreeMap<>();

    static {
        register(3, "bans/ip_bans 表加 id 主键列",
                db -> {
                    db.migrateBanTableToV3("bans");
                    db.migrateBanTableToV3("ip_bans");
                });
        register(4, "audit_log 加 prev_hash 列,初始化链式哈希",
                db -> {
                    db.addColumnIfMissing("audit_log", "prev_hash",
                            db.varcharType(64) + " NOT NULL DEFAULT ''");
                    if (db.isMySql()) {
                        db.execute("INSERT IGNORE INTO schema_meta (meta_key, meta_value) VALUES ('audit.tail', '')");
                    } else {
                        db.execute("INSERT OR IGNORE INTO schema_meta (meta_key, meta_value) VALUES ('audit.tail', '')");
                    }
                    db.backfillAuditChain();
                });
    }

    private SchemaMigrations() {}

    private static void register(int version, String description, MigrationAction action) {
        MIGRATIONS.put(version, new Migration(version, description, action));
    }

    /**
     * 执行当前版本之后的所有迁移。currentVersion 为 null 表示全新安装。
     */
    public static void runAll(DatabaseManager db, String currentVersion) {
        int startVersion = 0;
        if (currentVersion != null) {
            try {
                startVersion = Integer.parseInt(currentVersion);
            } catch (NumberFormatException ignored) {
                startVersion = 0;
            }
        }
        for (Migration m : MIGRATIONS.values()) {
            if (m.version() > startVersion) {
                try {
                    db.getPlugin().getLogger().info("正在执行 schema v" + m.version() + " 迁移: " + m.description());
                    m.action().run(db);
                    db.setMeta("schema.version", String.valueOf(m.version()));
                } catch (Exception e) {
                    db.getPlugin().getLogger().severe("schema v" + m.version() + " 迁移失败: " + e.getMessage());
                    throw new RuntimeException("Migration v" + m.version() + " failed", e);
                }
            }
        }
    }

    public static List<Migration> list() {
        return new ArrayList<>(MIGRATIONS.values());
    }

    public record Migration(int version, String description, MigrationAction action) {}

    @FunctionalInterface
    public interface MigrationAction {
        void run(DatabaseManager db) throws Exception;
    }
}