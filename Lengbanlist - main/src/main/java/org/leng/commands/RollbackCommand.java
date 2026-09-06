package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.manager.RollbackManager;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 管理员操作回滚命令。
 * 用法：/lban rollback [-y] <操作人> <开始时间> <结束时间> [操作类型]
 * 时间格式：YYYY-MM-DD 或 YYYY-MM-DD HH:mm:ss（未指定时分秒时按当天 00:00:00 / 23:59:59）
 *
 * 安全：涉及数据库批量写,默认先预览待回滚条数,30 秒内带 -y 才执行,避免误操作大范围撤销。
 */
public class RollbackCommand implements CommandExecutor {
    private static final Pattern DATE_ONLY = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$");
    private static final Pattern DATETIME = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})[ T](\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?$");
    /** 每个发送者的最近一次预览 token,30 秒内同 token + -y 才执行,避免他人借用 */
    private static final Map<String, PendingRollback> PENDING = new ConcurrentHashMap<>();
    private static final long CONFIRM_WINDOW_MS = 30_000L;

    private final Lengbanlist plugin;

    public RollbackCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("rollback")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        if (!sender.hasPermission("lengbanlist.rollback")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }
        if (args.length < 1) {
            sendUsage(sender, label);
            return true;
        }

        // -y/--yes: 跳过预览,直接执行。需先有有效 preview token 才能通过校验。
        boolean confirm = false;
        int argOffset = 0;
        if (args[0].equalsIgnoreCase("-y") || args[0].equalsIgnoreCase("--yes")) {
            confirm = true;
            argOffset = 1;
        }
        if (args.length - argOffset < 3) {
            sendUsage(sender, label);
            return true;
        }

        String actor = args[argOffset];
        Long from = parseTime(args[argOffset + 1], true);
        Long to = parseTime(args[argOffset + 2], false);
        if (from == null || to == null) {
            Utils.sendMessage(sender, plugin.prefix() + "§c时间格式无效喵，请使用：YYYY-MM-DD 或 YYYY-MM-DD HH:mm:ss");
            return true;
        }
        if (from > to) {
            Utils.sendMessage(sender, plugin.prefix() + "§c开始时间不能晚于结束时间。");
            return true;
        }

        String type = args.length - argOffset >= 4 ? args[argOffset + 3] : null;
        String senderKey = Utils.getSenderName(sender);
        String tokenKey = actor + "|" + from + "|" + to + "|" + (type == null ? "" : type);
        // 捕获到 final 局部变量,便于在 lambda 内引用 args[argOffset + ...]
        final int oFrom = argOffset + 1;
        final int oTo = argOffset + 2;
        final String[] capturedArgs = args;
        final String capturedType = type;

        if (!confirm) {
            // 预览模式:计算会回滚的条数并写 pending token
            org.leng.utils.SchedulerUtils.runAsync(plugin, () -> {
                int previewCount = new RollbackManager(plugin).previewCount(actor, from, to, capturedType);
                org.leng.utils.SchedulerUtils.runTask(plugin, sender, () -> {
                    if (previewCount == 0) {
                        Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getRollbackNoRecords(actor));
                        return;
                    }
                    Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getRollbackPreview(
                            previewCount, actor,
                            TimeUtils.timestampToReadable(from) + " ~ " + TimeUtils.timestampToReadable(to)));
                    Utils.sendMessage(sender, plugin.prefix() + "§e回滚将影响 " + previewCount + " 条记录，请在 30 秒内执行 §f/lban rollback -y " +
                            actor + " " + capturedArgs[oFrom] + " " + capturedArgs[oTo] +
                            (capturedType == null ? "" : " " + capturedType) + " §e确认。");
                    PENDING.put(senderKey + "|" + tokenKey, new PendingRollback(System.currentTimeMillis()));
                });
            });
            return true;
        }

        // 确认模式:校验 pending token,过期/缺失则拒绝
        PendingRollback pending = PENDING.remove(senderKey + "|" + tokenKey);
        if (pending == null || System.currentTimeMillis() - pending.at > CONFIRM_WINDOW_MS) {
            Utils.sendMessage(sender, plugin.prefix() + "§c缺少预览或已过期,请先执行 §f/lban rollback " +
                    actor + " " + capturedArgs[oFrom] + " " + capturedArgs[oTo] +
                    (capturedType == null ? "" : " " + capturedType) + " §c查看待回滚数量,30 秒内再带 -y 确认。");
            return true;
        }

        Utils.sendMessage(sender, plugin.prefix() + "§e正在回滚 " + actor + " 在 " +
                TimeUtils.timestampToReadable(from) + " ~ " + TimeUtils.timestampToReadable(to) +
                " 的操作" + (type == null ? "（全部类型）" : "（类型: " + type + "）") + "...");

        // 回滚涉及多个数据库写操作，放到异步线程执行，完成后回到主线程提示
        final String rollbackActor = Utils.getSenderName(sender);
        org.leng.utils.SchedulerUtils.runAsync(plugin, () -> {
            RollbackManager.RollbackResult result = new RollbackManager(plugin).rollback(actor, from, to, type, rollbackActor);
            org.leng.utils.SchedulerUtils.runTask(plugin, sender, () -> {
                Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getRollbackResult(
                        result.matched, result.executed, result.skipped));
                for (String detail : result.details) {
                    Utils.sendMessage(sender, " §7" + detail);
                }
                if (result.details.isEmpty()) {
                    Utils.sendMessage(sender, plugin.prefix() + "§7没有找到可回滚的操作记录。");
                }
            });
        });
        return true;
    }

    private record PendingRollback(long at) {}

    /**
     * 解析时间字符串。isStart 为 true 时，纯日期按当天 00:00:00 处理；否则按当天 23:59:59 处理。
     */
    private Long parseTime(String text, boolean isStart) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        Matcher dt = DATETIME.matcher(trimmed);
        if (dt.matches()) {
            try {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.clear();
                cal.set(parseInt(dt.group(1)), parseInt(dt.group(2)) - 1, parseInt(dt.group(3)),
                        parseInt(dt.group(4)), parseInt(dt.group(5)), dt.group(6) != null ? parseInt(dt.group(6)) : 0);
                return cal.getTimeInMillis();
            } catch (Exception e) {
                return null;
            }
        }
        Matcher d = DATE_ONLY.matcher(trimmed);
        if (d.matches()) {
            try {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.clear();
                cal.set(parseInt(d.group(1)), parseInt(d.group(2)) - 1, parseInt(d.group(3)),
                        isStart ? 0 : 23, isStart ? 0 : 59, isStart ? 0 : 59);
                return cal.getTimeInMillis();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private int parseInt(String s) {
        return Integer.parseInt(s);
    }

    private void sendUsage(CommandSender sender, String label) {
        Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /lban rollback [-y] <操作人> <开始时间> <结束时间> [操作类型]");
        Utils.sendMessage(sender, plugin.prefix() + "§7默认先预览待回滚条数，30 秒内带 -y 才执行实际回滚");
        Utils.sendMessage(sender, plugin.prefix() + "§7操作人：执行操作的管理员（必填）");
        Utils.sendMessage(sender, plugin.prefix() + "§7时间格式：YYYY-MM-DD 或 YYYY-MM-DD HH:mm:ss（开始默认 00:00:00，结束默认 23:59:59）");
        Utils.sendMessage(sender, plugin.prefix() + "§7操作类型（留空为全部）：ban / ban-ip / unban / unban-ip / mute / unmute / warn / unwarn / kick");
        Utils.sendMessage(sender, plugin.prefix() + "§7示例：/lban rollback Steve 2026-08-01 2026-08-14 ban");
        Utils.sendMessage(sender, plugin.prefix() + "§7示例：/lban rollback -y Steve 2026-08-01 2026-08-14 （确认执行）");
    }
}
