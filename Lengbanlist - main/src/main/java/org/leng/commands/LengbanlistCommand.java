package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.AuditEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.object.WarnEntry;
import org.leng.manager.EscalationManager.EscalationResult;
import org.leng.manager.BanManager;
import org.leng.manager.BanMutationFeedback;
import org.leng.manager.ModelManager;
import org.leng.models.Model;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;
import org.leng.utils.SaveIP;
import org.leng.utils.IpMatcher;
import org.leng.utils.IpGeoLookup;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class LengbanlistCommand extends Command implements CommandExecutor, TabCompleter {

    private final Lengbanlist plugin;
    private final IpGeoLookup ipGeoLookup;
    private final GuiCommand guiCommand;

    public LengbanlistCommand(String name, Lengbanlist plugin) {
        super(name);
        this.plugin = plugin;
        this.ipGeoLookup = new IpGeoLookup(plugin);
        this.guiCommand = new GuiCommand(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Model currentModel = ModelManager.getInstance().getCurrentModel();
        if (args.length == 0) {
            currentModel.showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "toggle":
                if (!sender.hasPermission("lengbanlist.toggle")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                boolean enabled = !plugin.isBroadcastEnabled();
                plugin.setBroadcastEnabled(enabled);
                Utils.sendMessage(sender, currentModel.toggleBroadcast(enabled));
                plugin.getAuditManager().log(enabled ? "开启广播" : "关闭广播", Utils.getSenderName(sender), "", "");
                break;
            case "a":
                if (!sender.hasPermission("lengbanlist.broadcast")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                String defaultMessage = plugin.getBroadcastFC().getString("default-message");
                if (defaultMessage == null || defaultMessage.isEmpty()) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c广播消息未配置，请在 broadcast.yml 中设置 default-message。");
                    break;
                }
                int banCount = plugin.getBanManager().getBanList().size();
                int banIpCount = plugin.getBanManager().getBanIpList().size();
                int totalBans = banCount + banIpCount;

                String replacedMessage = defaultMessage
                        .replace("%s", String.valueOf(banCount))
                        .replace("%i", String.valueOf(banIpCount))
                        .replace("%t", String.valueOf(totalBans));

                plugin.getServer().broadcastMessage(plugin.prefix() + " " + replacedMessage);
                break;
            case "list":
                if (!plugin.isFeatureEnabled("ban")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.list")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                showBanList(sender);
                break;
            case "reload":
                if (!sender.hasPermission("lengbanlist.reload")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                plugin.reloadConfig();
                ModelManager.getInstance().reloadModel();

                File broadcastFile = new File(plugin.getDataFolder(), "broadcast.yml");
                if (broadcastFile.exists()) {
                    try {
                        plugin.getBroadcastFC().load(broadcastFile);
                    } catch (Exception e) {
                        plugin.getLogger().warning("重载broadcast.yml失败: " + e.getMessage());
                    }
                }
                File chatConfigFile = new File(plugin.getDataFolder(), "chatconfig.yml");
                if (chatConfigFile.exists()) {
                    try {
                        plugin.getChatConfig().load(chatConfigFile);
                    } catch (Exception e) {
                        plugin.getLogger().warning("重载chatconfig.yml失败: " + e.getMessage());
                    }
                }
                plugin.registerFeatureCommands();
                Utils.sendMessage(sender, currentModel.reloadConfig());
                plugin.reloadWebServer();
                break;
            case "add":
                if (args.length >= 2 && args[1].contains(".")) {
                    if (!plugin.isFeatureEnabled("ban-ip")) {
                        plugin.sendFeatureDisabled(sender);
                        return true;
                    }
                } else {
                    if (!plugin.isFeatureEnabled("ban")) {
                        plugin.sendFeatureDisabled(sender);
                        return true;
                    }
                }
                // 权限：ban 或 banip 任一即可（路由器层放行,精确校验交由独立命令类）
                if (!sender.hasPermission("lengbanlist.ban") && !sender.hasPermission("lengbanlist.banip")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                String[] delegateArgs = Arrays.copyOfRange(args, 1, args.length);
                boolean isIp = delegateArgs.length > 0 && !delegateArgs[0].equalsIgnoreCase("-s") && delegateArgs[0].contains(".");
                if (isIp) {
                    return new BanIpCommand(plugin).onCommand(sender, null, label, delegateArgs);
                }
                return new BanCommand(plugin).onCommand(sender, null, label, delegateArgs);
            case "remove":
                if (!plugin.isFeatureEnabled("unban")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.unban")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new UnbanCommand(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "help":
                if (!sender.hasPermission("lengbanlist.help")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                currentModel.showHelp(sender);
                break;
            case "open":
                if (!plugin.isFeatureEnabled("chest-ui")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.open")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    guiCommand.openChestUI(player);
                } else {
                    Utils.sendMessage(sender, plugin.prefix() + "§c此命令只能由玩家执行。");
                }
                break;
            case "getip":
                if (!plugin.isFeatureEnabled("getip")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.getip")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new GetIPCommand(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "model":
                if (!plugin.isFeatureEnabled("model")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.model")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式/lban model <模型名称>");
                    StringBuilder availableModels = new StringBuilder("§6§l可用模型： §b");
                    for (String modelName : ModelManager.getInstance().getModels().keySet()) {
                        availableModels.append(modelName).append(" ");
                    }
                    Utils.sendMessage(sender, availableModels.toString());
                    return true;
                }
                String modelName = args[1].toLowerCase();
                boolean found = false;
                for (String name : ModelManager.getInstance().getModels().keySet()) {
                    if (name.equalsIgnoreCase(modelName)) {
                        ModelManager.switchModel(name);
                        Utils.sendMessage(sender, plugin.prefix() + "§a已切换到模型: " + name);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不认识这个模型喵。");
                    StringBuilder availableModels = new StringBuilder("§6§l可用模型： §b");
                    for (String name : ModelManager.getInstance().getModels().keySet()) {
                        availableModels.append(modelName).append(" ");
                    }
                    Utils.sendMessage(sender, availableModels.toString());
                }
                break;
            case "mute":
                if (!plugin.isFeatureEnabled("mute")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.mute")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new MuteCommand(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "unmute":
                if (!plugin.isFeatureEnabled("mute")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.mute")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new UnmuteCommand(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "list-mute":
                if (!plugin.isFeatureEnabled("mute")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.listmute")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                showMuteList(sender);
                break;
            case "warn":
                if (!plugin.isFeatureEnabled("warn")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.warn")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new WarnCommand(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "unwarn":
                if (!plugin.isFeatureEnabled("unwarn")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.unwarn")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new UnwarnCommand(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "report":
                if (!plugin.isFeatureEnabled("report")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!(sender instanceof Player)) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c此命令只能由玩家执行。");
                    return true;
                }

                String[] reportArgs = Arrays.copyOfRange(args, 1, args.length);
                return new ReportCommand(plugin).onCommand(sender, this, label, reportArgs);
            case "tp":
                if (!plugin.isFeatureEnabled("tp")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.admin")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /lban tp <玩家名>");
                    return true;
                }
                Player targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer != null && sender instanceof Player) {
                    ((Player) sender).teleport(targetPlayer);
                    Utils.sendMessage(sender, plugin.prefix() + "§a已传送到玩家 " + targetPlayer.getName());
                } else {
                    Utils.sendMessage(sender, plugin.prefix() + "§c玩家不在线");
                }
                break;
            case "admin":
                if (!plugin.isFeatureEnabled("admin")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.admin")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                new AdminReportCommand(plugin).onCommand(sender, this, label, args);
                break;
            case "check":
                if (!plugin.isFeatureEnabled("check")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.check")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式：/lban check <玩家名/IP>");
                    return true;
                }
                String checkTarget = args[1];
                String normalizedCheck = IpMatcher.normalizeIpOrCidr(checkTarget);
                if (normalizedCheck != null) checkTarget = normalizedCheck;
                CheckCommand checkCommand = new CheckCommand(plugin);
                checkCommand.execute(sender, "check", new String[]{checkTarget});
                break;
            case "info":
                if (!plugin.isFeatureEnabled("info")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.info")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new InfoCommand(plugin).onCommand(sender, null, "info", new String[0]);
            case "history":
                if (!plugin.isFeatureEnabled("history")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.history")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /lban history <玩家名>");
                    return true;
                }
                String[] histArgs = Arrays.copyOfRange(args, 1, args.length);
                if (histArgs.length > 0) {
                    String histTarget = histArgs[0];
                    String normalizedHist = IpMatcher.normalizeIpOrCidr(histTarget);
                    if (normalizedHist != null) histArgs[0] = normalizedHist;
                }
                return new HistoryCommand(plugin).onCommand(sender, this, "history", histArgs);
            case "audit":
                if (!plugin.isFeatureEnabled("audit")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.audit")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("export")) {
                    if (!plugin.isFeatureEnabled("export")) {
                        plugin.sendFeatureDisabled(sender);
                        return true;
                    }
                    if (!sender.hasPermission("lengbanlist.export")) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                        return true;
                    }
                    int auditExportLimit = 0;
                    if (args.length >= 3) {
                        try {
                            auditExportLimit = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            Utils.sendMessage(sender, plugin.prefix() + "§c§l导出数量格式不对喵，正确格式: /lban audit export [数量]");
                            return true;
                        }
                    }
                    plugin.getAuditManager().exportAudit(sender, auditExportLimit);
                    break;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("verify")) {
                    if (!sender.hasPermission("lengbanlist.export")) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                        return true;
                    }
                    plugin.getAuditManager().verifyAudit(sender);
                    break;
                }
                String auditFilter = args.length >= 2 ? args[1] : "";
                List<AuditEntry> auditLogs = plugin.getAuditManager().getLogsByActor(auditFilter, 20);
                if (auditLogs.isEmpty()) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c暂无审计记录" + (auditFilter.isEmpty() ? "" : " (操作人: " + auditFilter + ")"));
                    return true;
                }
                Utils.sendMessage(sender, "§7--§bLengbanlist 审计日志" + (auditFilter.isEmpty() ? "" : " (操作人: §f" + auditFilter + "§b)") + "§7--");
                for (AuditEntry auditEntry : auditLogs) {
                    String mark = auditEntry.isSuccess() ? "§a[成功]" : "§c[失败]";
                    Utils.sendMessage(sender, mark + " §7[" + TimeUtils.timestampToReadable(auditEntry.getTimestamp()) + "] §e" + auditEntry.getAction() + " §f" + auditEntry.getActor() + " → " + auditEntry.getTarget() + " §7" + auditEntry.getReason());
                }
                break;
            case "handle":
                if (!plugin.isFeatureEnabled("report")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.admin")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 3) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式: /lban handle <举报ID> <时间/auto/forever> [原因]");
                    return true;
                }
                ReportEntry handleReport = plugin.getReportManager().getReport(args[1]);
                if (handleReport == null) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c未找到举报编号: " + args[1]);
                    return true;
                }
                String handleStatus = handleReport.getStatus();
                if (handleStatus == null || (!handleStatus.equals("未处理") && !handleStatus.equals("受理中") && !handleStatus.equals("已读"))) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c该举报已处理，无法再次操作。");
                    return true;
                }
                String handleTarget = handleReport.getTarget();
                if (!plugin.getImmunityManager().canPunish(sender, handleTarget)) {
                    Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(handleTarget));
                    return true;
                }
                boolean handleAuto = args[2].equalsIgnoreCase("auto");
                try {
                    long handleEndTime;
                    EscalationResult handleEscalationResult = null;
                    if (args[2].equalsIgnoreCase("forever")) {
                        handleEndTime = Long.MAX_VALUE;
                    } else if (handleAuto) {
                        handleEscalationResult = handleTarget.contains(".")
                                ? plugin.getEscalationManager().resolveIpBan(handleTarget)
                                : plugin.getEscalationManager().resolveBan(handleTarget);
                        handleEndTime = TimeUtils.calculateEndTime(handleEscalationResult.durationMillis);
                    } else {
                        long handleDuration = TimeUtils.parseDurationToMillis(args[2]);
                        if (handleDuration <= 0) {
                            Utils.sendMessage(sender, plugin.prefix() + "§c时间格式无效喵，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever, auto");
                            return true;
                        }
                        handleEndTime = TimeUtils.calculateEndTime(handleDuration);
                    }
                    String handleReason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : handleReport.getReason();
                    BanManager.BanMutationResult handleResult = plugin.getReportManager().tryBanFromReport(
                            handleReport, Utils.getSenderName(sender), handleEndTime, handleReason, handleAuto);
                    if (!handleResult.isApplied()) {
                        BanMutationFeedback.sendFailure(sender, handleResult, handleTarget, handleTarget.contains("."));
                        break;
                    }
                    if (handleEscalationResult != null && handleEscalationResult.offenseCount > 0) {
                        Utils.sendMessage(sender, currentModel.onEscalatedBan(handleTarget,
                                handleEscalationResult.offenseCount,
                                TimeUtils.formatDuration(handleEscalationResult.durationMillis)));
                    }
                    String handleDurationText = handleEndTime == Long.MAX_VALUE ? "永久" : TimeUtils.formatDuration(handleEndTime - System.currentTimeMillis());
                    Utils.sendMessage(sender, plugin.prefix() + "§a已处理举报 " + handleReport.getId() + "，封禁玩家 " + handleTarget + "（" + handleDurationText + "）");
                } catch (IllegalArgumentException e) {
                    Utils.sendMessage(sender, plugin.prefix() + e.getMessage());
                }
                break;
            case "alts":
                if (!plugin.isFeatureEnabled("alts")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.alts")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2 || args[1].isEmpty()) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式: /lban alts <玩家名>");
                    return true;
                }
                if (args[1].contains(".")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l参数应为玩家名，不能是 IP：/lban alts <玩家名>");
                    return true;
                }
                plugin.getAltsCommand().execute(sender, args[1]);
                break;
            case "sync":
                if (!plugin.isFeatureEnabled("sync")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.sync")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                new org.leng.manager.SyncManager(plugin).execute(sender);
                break;
            case "rollback":
                if (!plugin.isFeatureEnabled("rollback")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.rollback")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                String[] rollbackArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                return new RollbackCommand(plugin).onCommand(sender, null, "lban rollback", rollbackArgs);
            default:
                Utils.sendMessage(sender, plugin.prefix() + "§c未知子命令喵: §f" + args[0] + "§c，输入 §f/lban help §c看看能用什么喵。");
                break;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            String[] subs = {"toggle", "a", "list", "reload", "add", "remove", "help", "open",
                    "getip", "model", "mute", "unmute", "list-mute", "warn", "unwarn",
                    "report", "admin", "check", "info", "tp", "history", "audit", "handle", "alts", "sync", "rollback"};
            for (String s : subs) {
                if (s.startsWith(prefix)) completions.add(s);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            switch (sub) {
                case "mute":
                case "warn":
                case "add":
                case "check":
                case "getip":
                case "tp":
                case "history":
                case "alts":
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(prefix)) completions.add(p.getName());
                    }
                    break;
                case "audit":
                    for (String s : new String[]{"export", "verify"}) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                    break;
                case "handle":
                    for (ReportEntry r : plugin.getReportManager().getPendingReports()) {
                        if (r.getId().startsWith(prefix)) completions.add(r.getId());
                    }
                    break;
                case "unmute":
                    for (MuteEntry e : plugin.getMuteManager().getMuteList()) {
                        if (e.getTarget().toLowerCase().startsWith(prefix)) completions.add(e.getTarget());
                    }
                    break;
                case "unwarn":
                    for (String name : plugin.getWarnManager().getWarnedPlayers()) {
                        if (name.toLowerCase().startsWith(prefix)) completions.add(name);
                    }
                    break;
                case "remove":
                    for (BanEntry e : plugin.getBanManager().getBanList()) {
                        if (e.getTarget().toLowerCase().startsWith(prefix)) completions.add(e.getTarget());
                    }
                    break;
                case "model":
                    for (String name : ModelManager.getInstance().getModels().keySet()) {
                        if (name.toLowerCase().startsWith(prefix)) completions.add(name);
                    }
                    break;
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("report")) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase();
                for (String s : new String[]{"accept", "close"}) {
                    if (s.startsWith(prefix)) completions.add(s);
                }
            } else if (args.length == 3 && (args[1].equalsIgnoreCase("accept") || args[1].equalsIgnoreCase("close"))) {
                String prefix = args[2].toLowerCase();
                for (ReportEntry r : plugin.getReportManager().getPendingReports()) {
                    if (r.getId().startsWith(prefix)) completions.add(r.getId());
                }
            }
        }
        return completions;
    }

    private void showBanList(CommandSender sender) {
        Utils.sendMessage(sender, "§7--§bLengbanlist 封禁名单§7--");
        for (BanEntry entry : plugin.getBanManager().getBanList()) {
            Utils.sendMessage(sender, "§c被封禁者：§f" + entry.getTarget() + " §e处理人：§f" + entry.getStaff() + " §e封禁原因：§f" + entry.getReason() + " §f解封时间：" + TimeUtils.timestampToReadable(entry.getTime()));
        }
        for (BanIpEntry entry : plugin.getBanManager().getBanIpList()) {
            Utils.sendMessage(sender, "§c被封禁IP：§f" + entry.getIp() + " §e处理人：§f" + entry.getStaff() + " §e封禁原因：§f" + entry.getReason() + " §f解封时间：" + TimeUtils.timestampToReadable(entry.getTime()));
        }
    }

    private void showMuteList(CommandSender sender) {
        Utils.sendMessage(sender, "§7--§bLengbanlist 禁言名单§7--");
        for (MuteEntry entry : plugin.getMuteManager().getMuteList()) {
            Utils.sendMessage(sender, "§c被禁言者：§f" + entry.getTarget() + " §e处理人：§f" + entry.getStaff() + " §e禁言原因：§f" + entry.getReason() + " §f解禁时间：" + TimeUtils.timestampToReadable(entry.getTime()));
        }
    }
}