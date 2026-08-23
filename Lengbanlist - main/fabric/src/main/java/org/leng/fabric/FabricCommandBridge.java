package org.leng.fabric;

import org.leng.manager.BanManager;
import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.object.WarnEntry;
import org.leng.platform.MessageSink;
import org.leng.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class FabricCommandBridge {
    private static final String[] COMMANDS = {
            "lban", "ban", "ban-ip", "unban", "warn", "unwarn", "check", "kick", "info",
            "mute", "unmute", "listmute", "history", "report", "admin", "setban", "allowmsg",
            "warnmsg", "getip", "sc"
    };

    // 反射目标缓存：避免每次命令执行重复 Class.forName / Method lookup
    private static final java.lang.reflect.Method M_SOURCE_GET_NAME;
    private static final Class<?> C_STRING_ARG_TYPE;
    private static final java.lang.reflect.Method M_GET_STRING;
    private static final Class<?> C_COMMAND_CONTEXT;

    static {
        try {
            C_STRING_ARG_TYPE = Class.forName("com.mojang.brigadier.arguments.StringArgumentType");
            C_COMMAND_CONTEXT = Class.forName("com.mojang.brigadier.context.CommandContext");
            M_GET_STRING = C_STRING_ARG_TYPE.getMethod("getString", C_COMMAND_CONTEXT, String.class);
            M_SOURCE_GET_NAME = resolveSourceGetName();
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    private static java.lang.reflect.Method resolveSourceGetName() {
        // 优先用 Entity.getName()（玩家），否则用 Object.getName（CommandSource）
        try {
            return Class.forName("net.minecraft.entity.Entity").getMethod("getName");
        } catch (Throwable ignored) {
            try {
                return Class.forName("net.minecraft.server.command.ServerCommandSource").getMethod("getName");
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    private FabricCommandBridge() {
    }

    public static void register(FabricLengbanlist plugin) {
        try {
            Class<?> eventClass = Class.forName("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
            Object event = eventClass.getField("EVENT").get(null);
            Class<?> eventType = Class.forName("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    FabricCommandBridge.class.getClassLoader(),
                    new Class[]{eventType},
                    (proxy, method, args) -> {
                        if ("register".equals(method.getName())) {
                            Object dispatcher = args[0];
                            registerCommands(plugin, dispatcher);
                        }
                        return null;
                    });
            ReflectionSupport.registerCallback(event, callback);
        } catch (Throwable e) {
            plugin.getLogger().warning("Fabric命令注册失败: " + e.getMessage());
        }
    }

    private static void registerCommands(FabricLengbanlist plugin, Object dispatcher) {
        for (String command : COMMANDS) {
            if (isCoreCommand(command) || plugin.isFeatureEnabled(command)) {
                registerLiteral(plugin, dispatcher, command);
            }
        }
    }

    private static boolean isCoreCommand(String command) {
        return "lban".equalsIgnoreCase(command) || "info".equalsIgnoreCase(command) || "admin".equalsIgnoreCase(command);
    }

    private static void registerLiteral(FabricLengbanlist plugin, Object dispatcher, String command) {
        try {
            Class<?> commandManager = Class.forName("net.minecraft.server.command.CommandManager");
            Class<?> commandInterface = Class.forName("com.mojang.brigadier.Command");
            Object literal = commandManager.getMethod("literal", String.class).invoke(null, command);
            Object noArgExecutor = executor(plugin, command, false);
            literal = literal.getClass().getMethod("executes", commandInterface).invoke(literal, noArgExecutor);

            Class<?> stringArgumentType = Class.forName("com.mojang.brigadier.arguments.StringArgumentType");
            Object greedy = stringArgumentType.getMethod("greedyString").invoke(null);
            Object argument = commandManager.getMethod("argument", String.class, Class.forName("com.mojang.brigadier.arguments.ArgumentType")).invoke(null, "args", greedy);
            Object argExecutor = executor(plugin, command, true);
            argument = argument.getClass().getMethod("executes", commandInterface).invoke(argument, argExecutor);
            literal = literal.getClass().getMethod("then", Class.forName("com.mojang.brigadier.builder.ArgumentBuilder")).invoke(literal, argument);

            dispatcher.getClass().getMethod("register", Class.forName("com.mojang.brigadier.builder.LiteralArgumentBuilder")).invoke(dispatcher, literal);
        } catch (Throwable e) {
            plugin.getLogger().warning("命令 " + command + " 注册失败: " + describe(e));
        }
    }

    private static String describe(Throwable e) {
        Throwable cause = e;
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (cause != null && depth < 5) {
            if (depth > 0) sb.append(" <- ");
            sb.append(cause.getClass().getSimpleName()).append(": ");
            String msg = cause.getMessage();
            sb.append(msg == null ? "<no message>" : msg);
            cause = cause.getCause();
            depth++;
        }
        return sb.toString();
    }

    private static Object executor(FabricLengbanlist plugin, String command, boolean withArgs) throws Exception {
        Class<?> commandInterface = Class.forName("com.mojang.brigadier.Command");
        return java.lang.reflect.Proxy.newProxyInstance(
                FabricCommandBridge.class.getClassLoader(),
                new Class[]{commandInterface},
                (proxy, method, args) -> {
                    if (!"run".equals(method.getName())) return 0;
                    Object context = args[0];
                    Object source = context.getClass().getMethod("getSource").invoke(context);
                    String[] parsedArgs = new String[0];
                    if (withArgs) {
                        String raw = String.valueOf(M_GET_STRING.invoke(null, context, "args"));
                        parsedArgs = raw.trim().isEmpty() ? new String[0] : raw.trim().split("\\s+");
                    }
                    execute(plugin, source, command, parsedArgs);
                    return 1;
                });
    }

    public static void execute(FabricLengbanlist plugin, Object source, String commandName, String[] args) {
        MessageSink sink = message -> ReflectionSupport.sendMessage(source, message);
        String name = sourceName(source);
        Model model = plugin.getModelManager().getCurrentModel();
        boolean silent = false;
        if ("lban".equalsIgnoreCase(commandName)) {
            if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
                model.showHelp(sink);
                return;
            }
            String sub = args[0].toLowerCase();
            if ("list".equals(sub)) {
                if (!requirePermission(source, sink)) return;
                showBanList(plugin, sink);
                return;
            }
            if ("list-mute".equals(sub)) {
                if (!requirePermission(source, sink)) return;
                showMuteList(plugin, sink);
                return;
            }
            if ("reload".equals(sub)) {
                if (!requirePermission(source, sink)) return;
                plugin.reloadConfigFiles();
                sink.sendMessage(model.reloadConfig());
                return;
            }
            if ("model".equals(sub)) {
                if (!requirePermission(source, sink)) return;
                if (args.length < 2) {
                    sink.sendMessage(plugin.prefix() + "§c§l命令格式不对喵，正确格式/lban model <模型名称>");
                    return;
                }
                org.leng.manager.ModelManager.switchModel(args[1]);
                sink.sendMessage(plugin.prefix() + "§a已切换到模型: " + args[1]);
                return;
            }
            if ("add".equals(sub)) sub = args.length > 1 && args[1].contains(".") ? "ban-ip" : "ban";
            if ("remove".equals(sub)) sub = "unban";
            if ("audit".equals(sub)) {
                if (!requirePermission(source, sink)) return;
                if (args.length >= 2 && "export".equalsIgnoreCase(args[1])) {
                    int limit = 0;
                    if (args.length >= 3) limit = parseInt(args[2], 0);
                    plugin.getAuditManager().exportAudit(sink, limit);
                    return;
                }
                if (args.length >= 2 && "verify".equalsIgnoreCase(args[1])) {
                    plugin.getAuditManager().verifyAudit(sink);
                    return;
                }
                String auditFilter = args.length >= 2 ? args[1] : "";
                List<org.leng.object.AuditEntry> auditLogs = plugin.getAuditManager().getLogsByActor(auditFilter, 20);
                if (auditLogs.isEmpty()) {
                    sink.sendMessage(plugin.prefix() + "§c暂无审计记录" + (auditFilter.isEmpty() ? "" : " (操作人: " + auditFilter + ")"));
                    return;
                }
                sink.sendMessage("§7--§bLengbanlist 审计日志" + (auditFilter.isEmpty() ? "" : " (操作人: §f" + auditFilter + "§b)") + "§7--");
                for (org.leng.object.AuditEntry auditEntry : auditLogs) {
                    String mark = auditEntry.isSuccess() ? "§a[成功]" : "§c[失败]";
                    sink.sendMessage(mark + " §7[" + TimeUtils.timestampToReadable(auditEntry.getTimestamp()) + "] §e" + auditEntry.getAction() + " §f" + auditEntry.getActor() + " → " + auditEntry.getTarget() + " §7" + auditEntry.getReason());
                }
                return;
            }
            if ("handle".equals(sub)) {
                if (!requirePermission(source, sink)) return;
                if (args.length < 3) {
                    sink.sendMessage(plugin.prefix() + "§c§l命令格式不对喵，正确格式: /lban handle <举报ID> <时间/auto/forever> [原因]");
                    return;
                }
                ReportEntry handleReport = plugin.getReportManager().getReport(args[1]);
                if (handleReport == null) {
                    sink.sendMessage(plugin.prefix() + "§c未找到举报编号: " + args[1]);
                    return;
                }
                String handleStatus = handleReport.getStatus();
                if (handleStatus != null && !handleStatus.equals("受理中") && !handleStatus.equals("已读")) {
                    sink.sendMessage(plugin.prefix() + "§c该举报已处理，无法再次操作。");
                    return;
                }
                String handleTarget = handleReport.getTarget();
                boolean handleAuto = args[2].equalsIgnoreCase("auto");
                long handleEndTime;
                if (args[2].equalsIgnoreCase("forever")) {
                    handleEndTime = Long.MAX_VALUE;
                } else if (handleAuto) {
                    org.leng.manager.EscalationManager.EscalationResult escalationResult = handleTarget.contains(".")
                            ? plugin.getEscalationManager().resolveIpBan(handleTarget)
                            : plugin.getEscalationManager().resolveBan(handleTarget);
                    handleEndTime = TimeUtils.calculateEndTime(escalationResult.durationMillis);
                } else {
                    long handleDuration = TimeUtils.parseDurationToMillis(args[2]);
                    if (handleDuration <= 0) {
                        sink.sendMessage(plugin.prefix() + "§c时间格式无效喵，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever, auto");
                        return;
                    }
                    handleEndTime = TimeUtils.calculateEndTime(handleDuration);
                }
                String handleReason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : handleReport.getReason();
                plugin.getReportManager().tryBanFromReport(handleReport, sourceName(source), handleEndTime, handleReason, handleAuto);
                String handleDurationText = handleEndTime == Long.MAX_VALUE ? "永久" : TimeUtils.formatDuration(handleEndTime - System.currentTimeMillis());
                sink.sendMessage(plugin.prefix() + "§a已处理举报 " + handleReport.getId() + "，封禁玩家 " + handleTarget + "（" + handleDurationText + "）");
                return;
            }
            if ("sync".equals(sub)) {
                if (!requirePermission(source, sink)) return;
                new org.leng.manager.SyncManager(plugin).execute(sink);
                return;
            }
            if ("rollback".equals(sub)) {
                if (!requirePermission(source, sink)) return;
                if (args.length < 4) {
                    sink.sendMessage(plugin.prefix() + "§c用法错误喵: /lban rollback <操作人> <开始时间> <结束时间> [操作类型]");
                    return;
                }
                String actor = args[1];
                Long from = parseTime(args[2], true);
                Long to = parseTime(args[3], false);
                if (from == null || to == null) {
                    sink.sendMessage(plugin.prefix() + "§c时间格式无效喵，请使用：YYYY-MM-DD 或 YYYY-MM-DD HH:mm:ss");
                    return;
                }
                if (from > to) {
                    sink.sendMessage(plugin.prefix() + "§c开始时间不能晚于结束时间。");
                    return;
                }
                String type = args.length >= 5 ? args[4] : null;
                sink.sendMessage(plugin.prefix() + "§e正在回滚 " + actor + " 的操作...");
                plugin.runAsync(() -> {
                    org.leng.manager.RollbackManager.RollbackResult result = new org.leng.manager.RollbackManager(plugin).rollback(actor, from, to, type, sourceName(source));
                    plugin.runSync(() -> {
                        sink.sendMessage(model.getRollbackResult(result.matched, result.executed, result.skipped));
                        for (String detail : result.details) {
                            sink.sendMessage(" §7" + detail);
                        }
                        if (result.details.isEmpty()) {
                            sink.sendMessage(plugin.prefix() + "§7没有找到可回滚的操作记录。");
                        }
                    });
                });
                return;
            }
            commandName = sub;
            args = Arrays.copyOfRange(args, 1, args.length);
        }

        switch (commandName.toLowerCase()) {
            case "ban":
                if (!requirePermission(source, sink)) return;
                silent = isLeadingSilent(args); args = stripLeadingSilent(args);
                if (args.length < 3) {
                    sink.sendMessage("§c用法错误喵: /ban <必填>玩家 <必填>时间/auto <必填>原因  [可选 -s 静默]");
                    return;
                }
                ban(plugin, sink, name, args[0], args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)), false, silent);
                break;
            case "ban-ip":
                if (!requirePermission(source, sink)) return;
                silent = isLeadingSilent(args); args = stripLeadingSilent(args);
                if (args.length < 3) {
                    sink.sendMessage(plugin.prefix() + "§c用法喵: /ban-ip <必填>IP <必填>时间/auto <必填>原因  [可选 -s 静默]");
                    return;
                }
                ban(plugin, sink, name, args[0], args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)), true, silent);
                break;
            case "unban":
                if (!requirePermission(source, sink)) return;
                silent = isLeadingSilent(args); args = stripLeadingSilent(args);
                if (args.length < 1) return;
                if (args[0].contains(".")) plugin.getBanManager().tryUnbanIp(args[0], name, silent); else plugin.getBanManager().tryUnbanPlayer(args[0], name, silent);
                break;
            case "warn":
                if (!requirePermission(source, sink)) return;
                if (args.length < 2) return;
                String warnReason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                plugin.getWarnManager().warnPlayer(args[0], name, warnReason);
                sink.sendMessage(model.addWarn(args[0], warnReason));
                break;
            case "unwarn":
                if (!requirePermission(source, sink)) return;
                if (args.length < 1) return;
                int warnId = args.length >= 2 ? parseInt(args[1], 1) : 1;
                plugin.getWarnManager().unwarnPlayer(args[0], warnId);
                sink.sendMessage(model.removeWarn(args[0]));
                break;
            case "mute":
                if (!requirePermission(source, sink)) return;
                silent = isLeadingSilent(args); args = stripLeadingSilent(args);
                if (args.length < 3) {
                    sink.sendMessage(plugin.prefix() + "§c用法喵: /lban mute <必填>玩家 <必填>时间/auto <必填>原因  [可选 -s 静默]");
                    return;
                }
                long muteDuration = TimeUtils.parseDurationToMillis(args[1]);
                if (muteDuration <= 0) {
                    sink.sendMessage(plugin.prefix() + "§c时间格式错误喵，请使用 10s, 5m, 2h, 7d, 1w, 1M, 1y, forever 或 auto。");
                    return;
                }
                String muteReason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getMuteManager().mutePlayer(new MuteEntry(args[0], name, TimeUtils.calculateEndTime(muteDuration), muteReason));
                if (!silent) plugin.broadcastMessage(model.addMute(args[0], muteReason));
                break;
            case "unmute":
                if (!requirePermission(source, sink)) return;
                silent = isLeadingSilent(args); args = stripLeadingSilent(args);
                if (args.length < 1) return;
                plugin.getMuteManager().unmutePlayer(args[0]);
                if (!silent) plugin.broadcastMessage(model.removeMute(args[0]));
                break;
            case "listmute":
                if (!requirePermission(source, sink)) return;
                showMuteList(plugin, sink);
                break;
            case "check":
                if (!requirePermission(source, sink)) return;
                if (args.length < 1) return;
                check(plugin, sink, args[0]);
                break;
            case "history":
                if (!requirePermission(source, sink)) return;
                if (args.length < 1) return;
                sink.sendMessage(model.getHistory(args[0], historyEntries(plugin, args[0])));
                break;
            case "info":
                sink.sendMessage(plugin.prefix() + "§6插件版本：v" + plugin.getPluginVersion());
                break;
            case "kick":
                if (!requirePermission(source, sink)) return;
                if (args.length < 1) return;
                plugin.kickPlayerIfOnline(args[0], args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Kicked");
                break;
            case "setban":
                if (!requirePermission(source, sink)) return;
                if (args.length < 3) return;
                String setReason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                ban(plugin, sink, name, args[0], args[1], setReason, args[0].contains("."), false);
                break;
            case "report":
                if (!plugin.isFeatureEnabled("report")) {
                    sink.sendMessage(plugin.prefix() + "§c该功能已被管理员禁用");
                    return;
                }
                if (args.length < 2) return;
                String reportReason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                ReportEntry report = new ReportEntry(args[0], name, reportReason, UUID.randomUUID().toString(), System.currentTimeMillis(), "未处理");
                plugin.getReportManager().addReport(report);
                sink.sendMessage(plugin.prefix() + "§a举报已提交，感谢你的反馈。ID: " + report.getId());
                break;
            case "admin":
                if (!requirePermission(source, sink)) return;
                handleAdmin(plugin, sink, args);
                break;
            case "getip":
                if (!requirePermission(source, sink)) return;
                if (args.length < 1) {
                    String self = sourcePlayerName(source);
                    if (self == null) {
                        sink.sendMessage(plugin.prefix() + "§c用法喵: /lban getip <可选>玩家名（不填则查询自己）");
                        return;
                    }
                    args = new String[]{self};
                }
                List<String[]> ips = plugin.getIpAssociationManager().getPlayerIps(args[0]);
                if (ips.isEmpty()) sink.sendMessage(plugin.prefix() + "§c§l查询不到玩家 " + args[0] + " 的 IP 地址");
                else sink.sendMessage(plugin.prefix() + "§a查询到玩家 " + args[0] + " 的 IP 地址为 " + ips.get(0)[0]);
                break;
            case "alts":
                if (!requirePermission(source, sink)) return;
                if (args.length < 1) {
                    sink.sendMessage(plugin.prefix() + "§c用法喵: /lban alts <必填>玩家名");
                    return;
                }
                List<org.leng.manager.IpAssociationManager.AltAccount> alts = plugin.getIpAssociationManager().getAlts(args[0]);
                if (alts.isEmpty()) {
                    sink.sendMessage(plugin.prefix() + "§7没有找到玩家 " + args[0] + " 的关联小号。");
                } else {
                    sink.sendMessage(plugin.prefix() + "§7玩家 " + args[0] + " 的关联小号（" + alts.size() + "）：");
                    int max = Math.min(alts.size(), Math.max(1, plugin.getConfigInt("alts.max-scan", 5)));
                    for (int i = 0; i < max; i++) {
                        org.leng.manager.IpAssociationManager.AltAccount alt = alts.get(i);
                        String tag = alt.banned ? " §c[已封禁]" : "";
                        sink.sendMessage(" §f#" + (i + 1) + " §e" + alt.name + tag);
                    }
                }
                break;
            case "allowmsg":
                if (!requirePermission(source, sink)) return;
                sink.sendMessage(plugin.prefix() + "§a已允许玩家发送消息");
                break;
            case "warnmsg":
                if (!requirePermission(source, sink)) return;
                sink.sendMessage(plugin.prefix() + "§a已警告玩家发送违规消息");
                break;
            case "sc":
                if (!requirePermission(source, sink)) return;
                if (args.length > 0) plugin.broadcastMessage("§7[§bStaffChat§7] §f" + name + ": " + String.join(" ", args));
                break;
            default:
                model.showHelp(sink);
                break;
        }
    }

    // 若 args 首项是 "-s"，返回 true；否则 false。与 stripLeadingSilent() 配对使用。
    private static boolean isLeadingSilent(String[] args) {
        return args.length > 0 && "-s".equalsIgnoreCase(args[0]);
    }

    private static String[] stripLeadingSilent(String[] args) {
        if (isLeadingSilent(args)) {
            String[] trimmed = new String[args.length - 1];
            System.arraycopy(args, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return args;
    }

    // 仅当 source 是玩家时返回其名字，否则返回 null。
    private static String sourcePlayerName(Object source) {
        try {
            Class<?> entityCls = Class.forName("net.minecraft.entity.Entity");
            if (!entityCls.isInstance(source)) return null;
            if (M_SOURCE_GET_NAME == null) return null;
            Object nameObj = M_SOURCE_GET_NAME.invoke(source);
            return nameObj == null ? null : String.valueOf(nameObj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean requirePermission(Object source, MessageSink sink) {
        if (ReflectionSupport.hasPermission(source, 2)) return true;
        sink.sendMessage("§c不是你的工作喵！");
        return false;
    }

    private static int parseInt(String value, int def) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static final java.util.regex.Pattern DATE_TIME_PATTERN =
            java.util.regex.Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})[ T](\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?$");
    private static final java.util.regex.Pattern DATE_PATTERN =
            java.util.regex.Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$");

    private static Long parseTime(String text, boolean isStart) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        java.util.regex.Matcher dt = DATE_TIME_PATTERN.matcher(trimmed);
        if (dt.matches()) {
            try {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setLenient(false);
                cal.clear();
                cal.set(Integer.parseInt(dt.group(1)), Integer.parseInt(dt.group(2)) - 1, Integer.parseInt(dt.group(3)),
                        Integer.parseInt(dt.group(4)), Integer.parseInt(dt.group(5)), dt.group(6) != null ? Integer.parseInt(dt.group(6)) : 0);
                return cal.getTimeInMillis();
            } catch (Exception e) {
                return null;
            }
        }
        java.util.regex.Matcher d = DATE_PATTERN.matcher(trimmed);
        if (d.matches()) {
            try {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setLenient(false);
                cal.clear();
                cal.set(Integer.parseInt(d.group(1)), Integer.parseInt(d.group(2)) - 1, Integer.parseInt(d.group(3)),
                        isStart ? 0 : 23, isStart ? 0 : 59, isStart ? 0 : 59);
                return cal.getTimeInMillis();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static void ban(FabricLengbanlist plugin, MessageSink sink, String staff, String target, String timeArg, String reason, boolean ip, boolean silent) {
        long duration;
        boolean isAuto = "auto".equalsIgnoreCase(timeArg);
        org.leng.manager.EscalationManager.EscalationResult escalationResult = null;
        if (isAuto) {
            escalationResult = ip
                    ? plugin.getEscalationManager().resolveIpBan(target)
                    : plugin.getEscalationManager().resolveBan(target);
            duration = escalationResult.durationMillis;
        } else {
            duration = TimeUtils.parseDurationToMillis(timeArg);
        }
        if (duration <= 0) {
            sink.sendMessage("§c时间格式错误喵，请使用以下格式:");
            sink.sendMessage("§c - 10s: 秒 (10 秒)");
            sink.sendMessage("§c - 5m: 分钟 (5 分钟)");
            sink.sendMessage("§c - 2h: 小时 (2 小时)");
            sink.sendMessage("§c - 7d: 天 (7 天)");
            sink.sendMessage("§c - 1w: 周 (1 周，等于 7 天)");
            sink.sendMessage("§c - 1M: 月 (1 月，按 30 天计算)");
            sink.sendMessage("§c - 1y: 年 (1 年，按 365 天计算)");
            sink.sendMessage("§c - forever: 永久封禁");
            sink.sendMessage("§c - auto: 自动计算封禁时间");
            return;
        }
        long end = TimeUtils.calculateEndTime(duration);
        BanManager.BanMutationResult banResult;
        if (ip) {
            banResult = plugin.getBanManager().tryBanIp(new BanIpEntry(target, staff, end, reason, isAuto), silent);
        } else {
            banResult = plugin.getBanManager().tryBanPlayer(new BanEntry(target, staff, end, reason, isAuto), silent);
        }
        if (!banResult.isApplied()) {
            org.leng.manager.BanMutationFeedback.sendFailure(sink, banResult, target, ip);
            return;
        }
        if (escalationResult != null && escalationResult.offenseCount > 0) {
            sink.sendMessage(plugin.getModelManager().getCurrentModel().onEscalatedBan(
                    target, escalationResult.offenseCount, TimeUtils.formatDuration(duration)));
        }
    }

    private static List<String> historyEntries(FabricLengbanlist plugin, String player) {
        List<String> entries = new ArrayList<>();
        for (BanEntry entry : plugin.getDatabaseManager().getBansByPlayer(player)) {
            entries.add("封禁 - " + entry.getReason() + " - " + TimeUtils.timestampToReadable(entry.getTime()));
        }
        for (MuteEntry entry : plugin.getDatabaseManager().getMutesByPlayer(player)) {
            entries.add("禁言 - " + entry.getReason() + " - " + TimeUtils.timestampToReadable(entry.getTime()));
        }
        for (WarnEntry entry : plugin.getWarnManager().getAllWarnings(player)) {
            entries.add("警告 - " + entry.getReason() + " - " + TimeUtils.timestampToReadable(entry.getTime()));
        }
        return entries;
    }

    private static void handleAdmin(FabricLengbanlist plugin, MessageSink sink, String[] args) {
        if (args.length >= 2 && "close".equalsIgnoreCase(args[0])) {
            ReportEntry report = plugin.getReportManager().getReport(args[1]);
            if (report == null) {
                sink.sendMessage(plugin.prefix() + "§c举报不存在");
                return;
            }
            report.setStatus("已关闭");
            plugin.getReportManager().updateReport(report);
            sink.sendMessage(plugin.prefix() + "§a举报 " + args[1] + " 已关闭");
            return;
        }
        sink.sendMessage(plugin.prefix() + "§e待处理举报: " + plugin.getReportManager().getPendingReportCount());
        for (ReportEntry report : plugin.getReportManager().getPendingReports()) {
            sink.sendMessage("§7#" + report.getId() + " §f" + report.getReporter() + " -> " + report.getTarget() + " §e" + report.getReason());
        }
    }

    private static void showBanList(FabricLengbanlist plugin, MessageSink sink) {
        sink.sendMessage("§7--§bLengbanlist 封禁名单§7--");
        for (BanEntry entry : plugin.getBanManager().getBanList()) {
            sink.sendMessage("§c被封禁者：§f" + entry.getTarget() + " §e处理人：§f" + entry.getStaff() + " §e封禁原因：§f" + entry.getReason() + " §f解封时间：" + TimeUtils.timestampToReadable(entry.getTime()));
        }
        for (BanIpEntry entry : plugin.getBanManager().getBanIpList()) {
            sink.sendMessage("§c被封禁IP：§f" + entry.getIp() + " §e处理人：§f" + entry.getStaff() + " §e封禁原因：§f" + entry.getReason() + " §f解封时间：" + TimeUtils.timestampToReadable(entry.getTime()));
        }
    }

    private static void showMuteList(FabricLengbanlist plugin, MessageSink sink) {
        sink.sendMessage("§7--§bLengbanlist 禁言名单§7--");
        for (MuteEntry entry : plugin.getMuteManager().getMuteList()) {
            sink.sendMessage("§c被禁言者：§f" + entry.getTarget() + " §e处理人：§f" + entry.getStaff() + " §e禁言原因：§f" + entry.getReason() + " §f解禁时间：" + TimeUtils.timestampToReadable(entry.getTime()));
        }
    }

    private static void check(FabricLengbanlist plugin, MessageSink sink, String target) {
        if (target.contains(".")) {
            BanIpEntry entry = plugin.getBanManager().getBanIpEntry(target);
            sink.sendMessage(entry == null ? plugin.prefix() + "§a该 IP 没有被封禁" : plugin.prefix() + "§cIP " + target + " 已被封禁，原因：" + entry.getReason());
        } else {
            BanEntry entry = plugin.getBanManager().getBanEntry(target);
            sink.sendMessage(entry == null ? plugin.prefix() + "§a玩家 " + target + " 没有被封禁" : plugin.prefix() + "§c玩家 " + target + " 已被封禁，原因：" + entry.getReason());
        }
    }

    private static String sourceName(Object source) {
        try {
            return String.valueOf(source.getClass().getMethod("getName").invoke(source));
        } catch (Exception ignored) {
            return "Console";
        }
    }
}
