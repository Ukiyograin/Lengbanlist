package org.leng.manager;

import org.bukkit.command.CommandSender;
import org.json.JSONArray;
import org.json.JSONObject;
import org.leng.Lengbanlist;
import org.leng.object.AuditEntry;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.io.BufferedWriter;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class AuditManager {
    private final Lengbanlist plugin;
    private final DatabaseManager db;

    public AuditManager(Lengbanlist plugin) {
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

    public void exportAudit(CommandSender sender, int limit) {
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                int max = limit <= 0 ? 10000 : Math.max(1, Math.min(limit, 100000));
                int step = 1000;
                int total = 0;
                File dir = new File(plugin.getDataFolder(), "exports");
                dir.mkdirs();
                File file = new File(dir, "audit_export_" + System.currentTimeMillis() + ".json");
                try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                    writer.write('[');
                    boolean first = true;
                    for (int offset = 0; offset < max; ) {
                        int fetch = Math.min(step, max - offset);
                        List<AuditEntry> rows = db.getAuditLogsAsc(offset, fetch);
                        if (rows.isEmpty()) {
                            break;
                        }
                        for (AuditEntry row : rows) {
                            String hash = DatabaseManager.hashRow(row.getPrevHash(), row.getTimestamp(), row.getActor(), row.getAction(), row.getTarget(), row.getReason(), row.isSuccess());
                            if (!first) {
                                writer.write(',');
                            }
                            first = false;
                            writer.write("{\"id\":" + row.getId() + ",\"timestamp\":" + row.getTimestamp() + ",\"actor\":" + JSONObject.quote(row.getActor()) + ",\"action\":" + JSONObject.quote(row.getAction()) + ",\"target\":" + JSONObject.quote(row.getTarget()) + ",\"reason\":" + JSONObject.quote(row.getReason()) + ",\"success\":" + row.isSuccess() + ",\"prev_hash\":" + JSONObject.quote(row.getPrevHash()) + ",\"hash\":" + JSONObject.quote(hash) + "}");
                            total++;
                        }
                        offset += rows.size();
                        if (rows.size() < fetch) {
                            break;
                        }
                    }
                    writer.write(']');
                }
                cleanupExports(dir);
                final String fPath = file.getAbsolutePath();
                final int fTotal = total;
                SchedulerUtils.runTask(plugin, () -> Utils.sendMessage(sender, plugin.prefix() + "§a审计日志已导出：" + fPath + "，共 " + fTotal + " 行"));
            } catch (Exception e) {
                plugin.getLogger().warning("导出审计日志失败: " + e.getMessage());
                SchedulerUtils.runTask(plugin, () -> Utils.sendMessage(sender, plugin.prefix() + "§c导出审计日志失败: " + e.getMessage()));
            }
        });
    }

    /**
     * 清理 exports 目录:仅保留 {@code maxFiles} 个最新文件,超出按最后修改时间从旧到新删除。
     * 上限从 {@code audit.export.max-files} 读取,缺省 20。
     */
    public int cleanupExports(File dir) {
        int maxFiles = plugin.getConfig().getInt("audit.export.max-files", 20);
        if (maxFiles <= 0) return 0;
        File[] files = dir.listFiles((d, name) -> name.startsWith("audit_export_") && name.endsWith(".json"));
        if (files == null || files.length <= maxFiles) return 0;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        int removed = 0;
        for (int i = 0; i < files.length - maxFiles; i++) {
            if (files[i].delete()) removed++;
        }
        if (removed > 0) {
            plugin.getLogger().info("已清理 " + removed + " 个旧审计导出文件,保留 " + maxFiles + " 个最新文件");
        }
        return removed;
    }

    public void verifyAudit(CommandSender sender) {
        if (!plugin.isFeatureEnabled("audit-chain")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c审计哈希链未启用（config.yml features.audit-chain），无法校验。");
            return;
        }
        SchedulerUtils.runAsync(plugin, () -> {
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
                SchedulerUtils.runTask(plugin, () -> Utils.sendMessage(sender, plugin.prefix() + "§c校验审计日志失败: " + e.getMessage()));
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
            SchedulerUtils.runTask(plugin, () -> {
                Utils.sendMessage(sender, plugin.prefix() + "§7审计链校验完成：共 " + fTotal + " 行，耗时 " + fElapsed + " 毫秒");
                if (fIntact) {
                    Utils.sendMessage(sender, plugin.prefix() + "§a审计链完整");
                } else {
                    Utils.sendMessage(sender, plugin.prefix() + "§c审计链断裂");
                    Utils.sendMessage(sender, plugin.prefix() + "§7首个断裂行：ID " + fBrokenId + "，时间 " + TimeUtils.timestampToReadable(fBrokenTimestamp) + "，操作人 " + fBrokenActor);
                    Utils.sendMessage(sender, plugin.prefix() + "§7（该行 prev_hash 与期望不符，其前驱行可能被篡改）");
                    Utils.sendMessage(sender, plugin.prefix() + "§7期望 prev_hash：" + fBrokenExpected);
                    Utils.sendMessage(sender, plugin.prefix() + "§7实际 prev_hash：" + fBrokenActual);
                }
            });
        });
    }

    private void notifyWebhook(String action, String actor, String target, String reason) {
        if (!plugin.isFeatureEnabled("webhook-events")) {
            return;
        }
        boolean enabled = plugin.getConfig().getBoolean("webhook.enabled", true);
        String webhookUrl = plugin.getConfig().getString("webhook.url", "");
        if (!enabled || webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }
        List<String> eventTypes = plugin.getConfig().getStringList("webhook.event-types");
        if (!eventTypes.isEmpty() && !eventTypes.contains(action)) {
            return;
        }
        final String fUrl = webhookUrl.trim();
        final String fAction = action;
        final String fActor = actor;
        final String fTarget = target == null ? "" : target;
        final String fReason = reason == null ? "" : reason;
        final String fUsername = plugin.getConfig().getString("webhook.username", "Lengbanlist");
        final String fAvatarUrl = plugin.getConfig().getString("webhook.avatar-url", "");
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                JSONArray fields = new JSONArray();
                fields.put(field("操作", fAction, true));
                fields.put(field("操作人", fActor, true));
                fields.put(field("目标", fTarget.isEmpty() ? "—" : fTarget, true));
                fields.put(field("原因", fReason.isEmpty() ? "—" : fReason, false));

                JSONObject embed = new JSONObject();
                embed.put("title", "Lengbanlist 审计日志");
                embed.put("color", 0xE74C3C);
                embed.put("fields", fields);
                embed.put("timestamp", Instant.now().toString());

                JSONObject payload = new JSONObject();
                payload.put("username", fUsername);
                if (fAvatarUrl != null && !fAvatarUrl.trim().isEmpty()) {
                    payload.put("avatar_url", fAvatarUrl.trim());
                }
                payload.put("embeds", new JSONArray().put(embed));

                try (org.leng.utils.HttpHelper http = new org.leng.utils.HttpHelper(5000, 5000)) {
                    int code = http.postJson(fUrl, payload.toString(), "Lengbanlist-Webhook/1.0");
                    if (code < 200 || code >= 300) {
                        plugin.getLogger().warning("Webhook 推送失败，HTTP " + code);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Webhook 推送异常: " + e.getMessage());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Webhook 构造失败: " + e.getMessage());
            }
        });
    }

    private JSONObject field(String name, String value, boolean inline) {
        return new JSONObject().put("name", name).put("value", value).put("inline", inline);
    }
}
