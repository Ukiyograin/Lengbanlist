package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.object.AuditEntry;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.utils.IpMatcher;
import org.leng.utils.TimeUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理员操作回滚：基于审计日志，回滚指定管理员在指定时间范围内的操作。
 *
 * 支持的操作类型（type）：
 *   ban      封禁玩家         → 回滚为解封该玩家
 *   ban-ip   封禁IP           → 回滚为解封该 IP
 *   unban    解封玩家         → 回滚为恢复封禁（时长 30 天）
 *   unban-ip 解封IP           → 回滚为恢复封禁 IP（时长 30 天）
 *   mute     禁言玩家         → 回滚为解除禁言
 *   unmute   解除禁言         → 回滚为恢复禁言（时长 7 天）
 *   warn     警告玩家         → 回滚为撤销该警告
 *   unwarn   取消警告         → 回滚为恢复该警告
 *   kick     踢出玩家         → 无法回滚，跳过并计入跳过数
 * 其余操作类型一律跳过。
 *
 * 说明：审计日志中的目标（target）同时用于玩家名与 IP；
 * 解封/解除禁言等操作的审计记录不包含原封禁时长，恢复时使用固定时长，
 * 恢复操作的执行人记录为发起回滚的管理员（rollback 的 executor 参数）。
 */
public class RollbackManager {

    /** 恢复封禁/禁言时使用的默认时长。 */
    public static final long DEFAULT_BAN_MILLIS = TimeUtils.daysToMillis(30);
    public static final long DEFAULT_MUTE_MILLIS = TimeUtils.daysToMillis(7);

    /** 警告记录 ID 提取（格式：<player>|<staff>|<time>|<reason>|<revoked>）。 */
    private static final Pattern WARN_ID_PATTERN = Pattern.compile("^([^|]*)\\|([^|]*)\\|(\\d+)\\|");

    private final Lengbanlist plugin;

    public RollbackManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    /** 回滚结果统计。 */
    public static class RollbackResult {
        public int matched;
        public int executed;
        public int skipped;
        public List<String> details = new ArrayList<>();
    }

    /**
     * 回滚指定操作人在 [fromMillis, toMillis] 时间范围内、类型为 type 的操作。
     * @param actor 操作人（必填）
     * @param fromMillis 开始时间戳（含）
     * @param toMillis 结束时间戳（含）
     * @param type 操作类型，null 或空表示全部
     * @param executor 发起回滚的管理员（记录为恢复操作的执行人，出现在审计日志中）
     * @return 回滚结果统计
     */
    public RollbackResult rollback(String actor, long fromMillis, long toMillis, String type, String executor) {
        RollbackResult result = new RollbackResult();
        String rollbackActor = executor == null || executor.trim().isEmpty() ? "CONSOLE" : executor.trim();
        if (actor == null || actor.trim().isEmpty()) {
            result.details.add("§c操作人不能为空");
            return result;
        }
        if (fromMillis > toMillis) {
            result.details.add("§c开始时间不能晚于结束时间");
            return result;
        }

        List<AuditEntry> logs = plugin.getDatabaseManager().getAuditLogsByActorInRange(actor.trim(), fromMillis, toMillis);
        // 按操作类型分组去重：同一目标同类型多次操作只回滚一次，避免重复解封/重复恢复
        Map<String, AuditEntry> toApply = new LinkedHashMap<>();
        for (AuditEntry log : logs) {
            String action = log.getAction() == null ? "" : log.getAction();
            if (!isSupported(action)) {
                continue;
            }
            if (type != null && !type.trim().isEmpty() && !typeMatches(type.trim(), action)) {
                continue;
            }
            String target = log.getTarget() == null ? "" : log.getTarget();
            if (target.isEmpty()) {
                continue;
            }
            String key = action + ":" + target.toLowerCase();
            if (!toApply.containsKey(key)) {
                toApply.put(key, log);
                result.matched++;
            }
        }

        for (Map.Entry<String, AuditEntry> entry : toApply.entrySet()) {
            AuditEntry log = entry.getValue();
            String action = log.getAction();
            String target = log.getTarget();
            boolean ok;
            String detail;
            try {
                switch (action) {
                    case "封禁":
                        ok = rollbackBan(target, rollbackActor);
                        detail = "§a回滚封禁：解封 " + target;
                        break;
                    case "封禁IP":
                        ok = rollbackBanIp(target, rollbackActor);
                        detail = "§a回滚封禁IP：解封 " + target;
                        break;
                    case "解封":
                        ok = rollbackUnban(target, rollbackActor);
                        detail = "§a回滚解封：重新封禁 " + target + "（" + TimeUtils.formatDuration(DEFAULT_BAN_MILLIS) + "）";
                        break;
                    case "解封IP":
                        ok = rollbackUnbanIp(target, rollbackActor);
                        detail = "§a回滚解封IP：重新封禁 " + target + "（" + TimeUtils.formatDuration(DEFAULT_BAN_MILLIS) + "）";
                        break;
                    case "禁言":
                        ok = rollbackMute(target, rollbackActor);
                        detail = "§a回滚禁言：解除 " + target + " 的禁言";
                        break;
                    case "解除禁言":
                        ok = rollbackUnmute(target, rollbackActor);
                        detail = "§a回滚解除禁言：重新禁言 " + target + "（" + TimeUtils.formatDuration(DEFAULT_MUTE_MILLIS) + "）";
                        break;
                    case "警告":
                        ok = rollbackWarn(log, target, fromMillis, toMillis);
                        detail = "§a回滚警告：撤销 " + target + " 的警告";
                        break;
                    case "取消警告":
                        ok = rollbackUnwarn(log, target);
                        detail = "§a回滚取消警告：恢复 " + target + " 的警告";
                        break;
                    case "踢出":
                        ok = false;
                        detail = "§7跳过踢出（无法回滚）：" + target;
                        break;
                    default:
                        ok = false;
                        detail = "§7跳过不支持的操作类型：" + action;
                        break;
                }
            } catch (Exception e) {
                ok = false;
                detail = "§c回滚 " + target + "（" + action + "）失败: " + e.getMessage();
            }
            if (ok) {
                result.executed++;
                result.details.add(detail);
            } else {
                result.skipped++;
                result.details.add(detail);
            }
        }
        return result;
    }

    private boolean isSupported(String action) {
        switch (action) {
            case "封禁":
            case "封禁IP":
            case "解封":
            case "解封IP":
            case "禁言":
            case "解除禁言":
            case "警告":
            case "取消警告":
            case "踢出":
                return true;
            default:
                return false;
        }
    }

    private boolean typeMatches(String type, String action) {
        switch (type.toLowerCase()) {
            case "ban":
                return action.equals("封禁") || action.equals("解封");
            case "ban-ip":
                return action.equals("封禁IP") || action.equals("解封IP");
            case "unban":
                return action.equals("解封");
            case "unban-ip":
                return action.equals("解封IP");
            case "mute":
                return action.equals("禁言") || action.equals("解除禁言");
            case "unmute":
                return action.equals("解除禁言");
            case "warn":
                return action.equals("警告") || action.equals("取消警告");
            case "unwarn":
                return action.equals("取消警告");
            case "kick":
                return action.equals("踢出");
            case "all":
            case "全部":
            case "*":
                return true;
            default:
                return false;
        }
    }

    /** 封禁 → 解封。 */
    private boolean rollbackBan(String target, String actor) {
        if (!plugin.getBanManager().isPlayerBanned(target)) {
            return false;
        }
        return plugin.getBanManager().tryUnbanPlayer(target, actor, true).isApplied();
    }

    /** 封禁IP → 解封。 */
    private boolean rollbackBanIp(String target, String actor) {
        boolean bannedExact = plugin.getBanManager().isIpBanned(target);
        boolean bannedCidr = !IpMatcher.isIpv4(target) && plugin.getBanManager().isIpBannedByCidr(target);
        if (!bannedExact && !bannedCidr) {
            return false;
        }
        return plugin.getBanManager().tryUnbanIp(target, actor, true).isApplied();
    }

    /** 解封 → 恢复封禁（30 天）。 */
    private boolean rollbackUnban(String target, String actor) {
        if (plugin.getBanManager().isPlayerBanned(target)) {
            return false;
        }
        long endTime = TimeUtils.calculateEndTime(DEFAULT_BAN_MILLIS);
        return plugin.getBanManager().tryBanPlayer(
                new BanEntry(target, actor, endTime, "管理员操作回滚（原解封）", false), true).isApplied();
    }

    /** 解封IP → 恢复封禁 IP（30 天）。 */
    private boolean rollbackUnbanIp(String target, String actor) {
        if (plugin.getBanManager().isIpBanned(target)) {
            return false;
        }
        long endTime = TimeUtils.calculateEndTime(DEFAULT_BAN_MILLIS);
        BanManager.BanMutationResult result = plugin.getBanManager().tryBanIp(
                new BanIpEntry(target, actor, endTime, "管理员操作回滚（原解封IP）", false), true);
        if (result == BanManager.BanMutationResult.REJECTED_PRIVATE_OR_RESERVED_IP) {
            throw new IllegalStateException("目标 IP 属于私有/保留地址，无法恢复封禁（可能为旧数据，需手动处理）");
        }
        return result.isApplied();
    }

    /** 禁言 → 解除禁言。 */
    private boolean rollbackMute(String target, String actor) {
        if (!plugin.getMuteManager().isPlayerMuted(target)) {
            return false;
        }
        plugin.getMuteManager().unmutePlayer(target, actor);
        return true;
    }

    /** 解除禁言 → 恢复禁言（7 天）。 */
    private boolean rollbackUnmute(String target, String actor) {
        if (plugin.getMuteManager().isPlayerMuted(target)) {
            return false;
        }
        long endTime = TimeUtils.calculateEndTime(DEFAULT_MUTE_MILLIS);
        Long applied = plugin.getMuteManager().mutePlayer(new MuteEntry(target, actor, endTime, "管理员操作回滚（原解除禁言）"));
        return applied != null;
    }

    /**
     * 警告 → 撤销该警告。
     * "警告"动作的审计 reason 是玩家可输入的原始原因文本（不含警告 ID），
     * 因此优先尝试按原因文本 + 时间范围匹配；找不到时回退为撤销该目标范围内任一条未撤销警告。
     */
    private boolean rollbackWarn(AuditEntry log, String target, long fromMillis, long toMillis) {
        String reason = log.getReason() == null ? "" : log.getReason();
        List<org.leng.object.WarnEntry> warnings = plugin.getWarnManager().getAllWarnings(target);
        org.leng.object.WarnEntry fallback = null;
        for (org.leng.object.WarnEntry warn : warnings) {
            if (warn.isRevoked()) {
                continue;
            }
            if (warn.getTime() >= fromMillis && warn.getTime() <= toMillis) {
                if (!reason.isEmpty() && reason.equals(warn.getReason())) {
                    warn = warn.revoke();
                    plugin.getDatabaseManager().updateWarningRevoked(warn.getId(), true);
                    return true;
                }
                if (fallback == null) {
                    fallback = warn;
                }
            }
        }
        if (fallback != null) {
            fallback = fallback.revoke();
            plugin.getDatabaseManager().updateWarningRevoked(fallback.getId(), true);
            return true;
        }
        return false;
    }

    /** 取消警告 → 恢复该警告。从审计 reason 中的"警告ID: <id>"列表定位,可一次恢复多条。 */
    private boolean rollbackUnwarn(AuditEntry log, String target) {
        List<String> warnIds = extractWarnIds(log);
        List<org.leng.object.WarnEntry> warnings = plugin.getWarnManager().getAllWarnings(target);
        boolean anyRestored = false;
        for (org.leng.object.WarnEntry warn : warnings) {
            if (warnIds != null && !warnIds.contains(warn.getId())) {
                continue;
            }
            if (warn.isRevoked()) {
                warn = warn.unrevoke();
                plugin.getDatabaseManager().updateWarningRevoked(warn.getId(), false);
                anyRestored = true;
            }
        }
        return anyRestored;
    }

    /**
     * 从审计日志的 reason 中提取所有警告 ID。
     * 批量取消警告的 reason 形如 "警告ID: &lt;id1&gt;,警告ID: &lt;id2&gt;,..."。
     * package-private 便于 RollbackManagerTest 直接验证解析逻辑。
     */
    List<String> extractWarnIds(AuditEntry log) {
        String reason = log.getReason() == null ? "" : log.getReason();
        if (!reason.contains("警告ID: ")) {
            return null;
        }
        List<String> ids = new java.util.ArrayList<>();
        int idx = 0;
        while ((idx = reason.indexOf("警告ID: ", idx)) >= 0) {
            idx += "警告ID: ".length();
            int end = idx;
            while (end < reason.length() && reason.charAt(end) != ',' && reason.charAt(end) != ' ') {
                end++;
            }
            String id = reason.substring(idx, end).trim();
            if (!id.isEmpty()) ids.add(id);
            idx = end;
        }
        return ids.isEmpty() ? null : ids;
    }
}
