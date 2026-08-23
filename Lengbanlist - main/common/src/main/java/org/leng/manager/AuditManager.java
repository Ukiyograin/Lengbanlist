package org.leng.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.leng.object.AuditEntry;
import org.leng.platform.LengbanlistPlatform;
import org.leng.platform.MessageSink;
import org.leng.utils.TimeUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

public class AuditManager {
    private final LengbanlistPlatform plugin;
    private final DatabaseManager db;

    public AuditManager(LengbanlistPlatform plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void log(String action, String actor, String target, String reason) {
        log(action, actor, target, reason, true);
    }

    public void log(String action, String actor, String target, String reason, boolean success) {
        if (!plugin.isFeatureEnabled("audit")) {
            return;
        }
        if (actor == null || actor.trim().isEmpty()) {
            actor = "System";
        }
        boolean recorded;
        if (plugin.isFeatureEnabled("audit-chain")) {
            recorded = db.addAuditLogChained(actor, action, target == null ? "" : target, reason == null ? "" : reason, success);
        } else {
            recorded = db.addAuditLog(actor, action, target == null ? "" : target, reason == null ? "" : reason, success);
        }
        if (!recorded) {
            plugin.getLogger().severe("审计日志写入失败（操作: " + action + "，操作人: " + actor + "，目标: " + (target == null ? "" : target) + "）：该操作已执行但未记录，请检查数据库状态。");
        }
        notifyWebhook(action, actor, target, reason);
    }

    public List<AuditEntry> getLogs(String actorOrTarget, int limit) {
        return db.getAuditLogs(actorOrTarget == null ? "" : actorOrTarget.trim(), Math.max(1, Math.min(limit, 200)));
    }

    public List<AuditEntry> getLogsByActor(String actor, int limit) {
        return db.getAuditLogsByActor(actor == null ? "" : actor.trim(), Math.max(1, Math.min(limit, 200)));
    }

    public void exportAudit(MessageSink sender, int limit) {
        plugin.runAsync(() -> {
            try {
                int max = limit <= 0 ? 10000 : Math.max(1, Math.min(limit, 100000));
                int step = 1000;
                int total = 0;
                File dir = new File(plugin.getDataFolder(), "exports");
                dir.mkdirs();
                File file = new File(dir, "audit_export_" + System.currentTimeMillis() + ".json");
                try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                    JsonArray array = new JsonArray();
                    for (int offset = 0; offset < max; ) {
                        int fetch = Math.min(step, max - offset);
                        List<AuditEntry> rows = db.getAuditLogsAsc(offset, fetch);
                        if (rows.isEmpty()) {
                            break;
                        }
                        for (AuditEntry row : rows) {
                            String hash = DatabaseManager.hashRow(row.getPrevHash(), row.getTimestamp(), row.getActor(), row.getAction(), row.getTarget(), row.getReason(), row.isSuccess());
                            JsonObject obj = new JsonObject();
                            obj.addProperty("id", row.getId());
                            obj.addProperty("timestamp", row.getTimestamp());
                            obj.addProperty("actor", row.getActor());
                            obj.addProperty("action", row.getAction());
                            obj.addProperty("target", row.getTarget());
                            obj.addProperty("reason", row.getReason());
                            obj.addProperty("success", row.isSuccess());
                            obj.addProperty("prev_hash", row.getPrevHash());
                            obj.addProperty("hash", hash);
                            array.add(obj);
                            total++;
                        }
                        offset += rows.size();
                        if (rows.size() < fetch) {
                            break;
                        }
                    }
                    writer.write(array.toString());
                }
                cleanupExports(dir);
                final String fPath = file.getAbsolutePath();
                final int fTotal = total;
                plugin.runSync(() -> sender.sendMessage(plugin.prefix() + "§a审计日志已导出：" + fPath + "，共 " + fTotal + " 行"));
            } catch (Exception e) {
                plugin.getLogger().warning("导出审计日志失败: " + e.getMessage());
                plugin.runSync(() -> sender.sendMessage(plugin.prefix() + "§c导出审计日志失败: " + e.getMessage()));
            }
        });
    }

    /**
     * 清理 exports 目录：仅保留 {@code maxFiles} 个最新文件，超出按最后修改时间从旧到新删除。
     * 上限从 {@code audit.export.max-files} 读取，缺省 20。
     */
    public int cleanupExports(File dir) {
        int maxFiles = plugin.getConfigInt("audit.export.max-files", 20);
        if (maxFiles <= 0) return 0;
        File[] files = dir.listFiles((d, name) -> name.startsWith("audit_export_") && name.endsWith(".json"));
        if (files == null || files.length <= maxFiles) return 0;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        int removed = 0;
        for (int i = 0; i < files.length - maxFiles; i++) {
            if (files[i].delete()) removed++;
        }
        if (removed > 0) {
            plugin.getLogger().info("已清理 " + removed + " 个旧审计导出文件，保留 " + maxFiles + " 个最新文件");
        }
        return removed;
    }

    public void verifyAudit(MessageSink sender) {
        if (!plugin.isFeatureEnabled("audit-chain")) {
            sender.sendMessage(plugin.prefix() + "§c审计哈希链未启用（config.yml features.audit-chain），无法校验。");
            return;
        }
        plugin.runAsync(() -> {
            long start = System.currentTimeMillis();
            int total = 0;
            boolean intact = true;
            String brokenId = "";
            long brokenTimestamp = 0;
            String brokenActor = "";
            String brokenExpected = "";
            String brokenActual = "";
            try {
                int step = 1000;
                int offset = 0;
                AuditEntry prevRow = null;
                while (intact) {
                    List<AuditEntry> rows = db.getAuditLogsAsc(offset, step);
                    if (rows.isEmpty()) {
                        break;
                    }
                    for (AuditEntry row : rows) {
                        if (prevRow != null) {
                            String expected = DatabaseManager.hashRow(prevRow.getPrevHash(), prevRow.getTimestamp(), prevRow.getActor(), prevRow.getAction(), prevRow.getTarget(), prevRow.getReason(), prevRow.isSuccess());
                            if (!row.getPrevHash().equals(expected)) {
                                intact = false;
                                brokenId = String.valueOf(row.getId());
                                brokenTimestamp = row.getTimestamp();
                                brokenActor = row.getActor();
                                brokenExpected = expected;
                                brokenActual = row.getPrevHash();
                                break;
                            }
                        }
                        prevRow = row;
                        total++;
                    }
                    offset += rows.size();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("校验审计日志失败: " + e.getMessage());
                plugin.runSync(() -> sender.sendMessage(plugin.prefix() + "§c校验审计日志失败: " + e.getMessage()));
                return;
            }
            long elapsed = System.currentTimeMillis() - start;
            final boolean fIntact = intact;
            final int fTotal = total;
            final long fElapsed = elapsed;
            final String fBrokenId = brokenId;
            final long fBrokenTimestamp = brokenTimestamp;
            final String fBrokenActor = brokenActor;
            final String fBrokenExpected = brokenExpected;
            final String fBrokenActual = brokenActual;
            plugin.runSync(() -> {
                sender.sendMessage(plugin.prefix() + "§7审计链校验完成：共 " + fTotal + " 行，耗时 " + fElapsed + " 毫秒");
                if (fIntact) {
                    sender.sendMessage(plugin.prefix() + "§a审计链完整");
                } else {
                    sender.sendMessage(plugin.prefix() + "§c审计链断裂");
                    sender.sendMessage(plugin.prefix() + "§7首个断裂行：ID " + fBrokenId + "，时间 " + TimeUtils.timestampToReadable(fBrokenTimestamp) + "，操作人 " + fBrokenActor);
                    sender.sendMessage(plugin.prefix() + "§7（该行 prev_hash 与期望不符，其前驱行可能被篡改）");
                    sender.sendMessage(plugin.prefix() + "§7期望 prev_hash：" + fBrokenExpected);
                    sender.sendMessage(plugin.prefix() + "§7实际 prev_hash：" + fBrokenActual);
                }
            });
        });
    }

    private void notifyWebhook(String action, String actor, String target, String reason) {
        if (!plugin.isFeatureEnabled("webhook-events")) {
            return;
        }
        boolean enabled = plugin.getConfigBoolean("webhook.enabled", true);
        String webhookUrl = plugin.getConfigString("webhook.url", "");
        if (!enabled || webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }
        List<String> eventTypes = plugin.getConfigStringList("webhook.event-types");
        if (!eventTypes.isEmpty() && !eventTypes.contains(action)) {
            return;
        }
        final String fUrl = webhookUrl.trim();
        final String fAction = action;
        final String fActor = actor;
        final String fTarget = target == null ? "" : target;
        final String fReason = reason == null ? "" : reason;
        final String fUsername = plugin.getConfigString("webhook.username", "Lengbanlist");
        final String fAvatarUrl = plugin.getConfigString("webhook.avatar-url", "");
        plugin.runAsync(() -> {
            try {
                JsonArray fields = new JsonArray();
                fields.add(field("操作", fAction, true));
                fields.add(field("操作人", fActor, true));
                fields.add(field("目标", fTarget.isEmpty() ? "—" : fTarget, true));
                fields.add(field("原因", fReason.isEmpty() ? "—" : fReason, false));

                JsonObject embed = new JsonObject();
                embed.addProperty("title", "Lengbanlist 审计日志");
                embed.addProperty("color", 0xE74C3C);
                embed.add("fields", fields);
                embed.addProperty("timestamp", Instant.now().toString());

                JsonObject payload = new JsonObject();
                payload.addProperty("username", fUsername);
                if (fAvatarUrl != null && !fAvatarUrl.trim().isEmpty()) {
                    payload.addProperty("avatar_url", fAvatarUrl.trim());
                }
                JsonArray embeds = new JsonArray();
                embeds.add(embed);
                payload.add("embeds", embeds);

                HttpURLConnection conn = (HttpURLConnection) new URL(fUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    plugin.getLogger().warning("Webhook 推送失败，HTTP " + code);
                }
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Webhook 推送异常: " + e.getMessage());
            }
        });
    }

    private JsonObject field(String name, String value, boolean inline) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);
        obj.addProperty("value", value);
        obj.addProperty("inline", inline);
        return obj;
    }
}
