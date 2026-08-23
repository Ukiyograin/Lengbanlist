package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.leng.Lengbanlist;
import org.leng.object.AuditEntry;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.manager.BanManager;
import org.leng.manager.BanMutationFeedback;
import org.leng.manager.ModelManager;
import org.leng.models.Model;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;
import org.leng.utils.SaveIP;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class LengbanlistCommand extends Command implements CommandExecutor, Listener, TabCompleter {
    private final Lengbanlist plugin;
    private final Gson gson = new Gson();

    public LengbanlistCommand(String name, Lengbanlist plugin) {
        super(name);
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Model currentModel = ModelManager.getInstance().getCurrentModel();
        if (args.length == 0) {
            currentModel.showHelp(message -> Utils.sendMessage(sender, message));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "toggle":
                if (!plugin.isFeatureEnabled("broadcast")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
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
                if (!plugin.isFeatureEnabled("broadcast")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.broadcast")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                String defaultMessage = plugin.getBroadcastFC().getString("default-message");
                int banCount = plugin.getBanManager().getBanList().size();
                int banIpCount = plugin.getBanManager().getBanIpList().size();
                int totalBans = banCount + banIpCount;

                String replacedMessage = defaultMessage
                        .replace("%s", String.valueOf(banCount))
                        .replace("%i", String.valueOf(banIpCount))
                        .replace("%t", String.valueOf(totalBans));

                plugin.getServer().broadcastMessage(replacedMessage);
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
                if (!sender.hasPermission("lengbanlist.ban")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 4) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式: /lban add <玩家名/IP> <时间/auto> <原因>");
                    return true;
                }
                try {
                    long durationLong;
                    boolean isAuto = args[2].equalsIgnoreCase("auto");
                    org.leng.manager.EscalationManager.EscalationResult escalationResult = null;
                    if (isAuto) {
                        escalationResult = args[1].contains(".")
                                ? plugin.getEscalationManager().resolveIpBan(args[1])
                                : plugin.getEscalationManager().resolveBan(args[1]);
                        durationLong = escalationResult.durationMillis;
                    } else {
                        durationLong = TimeUtils.parseTime(args[2]);
                        if (durationLong <= 0) {
                            throw new IllegalArgumentException("§c时间格式无效喵，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever, auto");
                        }
                    }
                    long endTime = TimeUtils.calculateEndTime(durationLong);

                    BanManager.BanMutationResult addResult;
                    if (args[1].contains(".")) {
                        addResult = plugin.getBanManager().tryBanIp(new BanIpEntry(args[1], Utils.getSenderName(sender), endTime, args[3], isAuto), false);
                    } else {
                        addResult = plugin.getBanManager().tryBanPlayer(new BanEntry(args[1], Utils.getSenderName(sender), endTime, args[3], isAuto), false);
                    }
                    if (addResult.isApplied() && isAuto && escalationResult != null && escalationResult.offenseCount > 0) {
                        Utils.sendMessage(sender, currentModel.onEscalatedBan(args[1], escalationResult.offenseCount, TimeUtils.formatDuration(durationLong)));
                    }
                    if (!addResult.isApplied()) {
                        BanMutationFeedback.sendFailure(msg -> Utils.sendMessage(sender, msg), addResult, args[1], args[1].contains("."));
                    }
                } catch (IllegalArgumentException e) {
                    Utils.sendMessage(sender, plugin.prefix() + e.getMessage());
                }
                break;
            case "remove":
                if (!plugin.isFeatureEnabled("unban")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.unban")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式/lban remove <玩家名/IP>");
                    return true;
                }
                if (args[1].contains(".")) {
                    if (!plugin.getBanManager().isIpBannedByCidr(args[1])) {
                        Utils.sendMessage(sender, plugin.prefix() + "§cIP " + args[1] + " 未被封禁或封禁已过期");
                        return true;
                    }
                    org.leng.object.BanIpEntry matchingBan = plugin.getBanManager().getMatchingIpBan(args[1]);
                    String targetIp = (matchingBan != null && !matchingBan.getIp().equals(args[1])) ? matchingBan.getIp() : args[1];
                    BanManager.BanMutationResult removeIp = plugin.getBanManager().tryUnbanIp(targetIp, Utils.getSenderName(sender), false);
                    if (!removeIp.isApplied()) {
                        BanMutationFeedback.sendFailure(msg -> Utils.sendMessage(sender, msg), removeIp, targetIp, true);
                    }
                } else {
                    if (!plugin.getBanManager().isPlayerBanned(args[1])) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c玩家 " + args[1] + " 未被封禁或封禁已过期");
                        return true;
                    }
                    BanManager.BanMutationResult removeP = plugin.getBanManager().tryUnbanPlayer(args[1], Utils.getSenderName(sender), false);
                    if (!removeP.isApplied()) {
                        BanMutationFeedback.sendFailure(msg -> Utils.sendMessage(sender, msg), removeP, args[1], false);
                    }
                }
                break;
            case "help":
                if (!sender.hasPermission("lengbanlist.help")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                currentModel.showHelp(message -> Utils.sendMessage(sender, message));
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
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    openChestUI(player);
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
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式 /lban getip <玩家名>");
                    return false;
                }
                String target = args[1];
                String ip = SaveIP.getIP(target);
                if (ip == null) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l查询不到玩家 " + target + " 的 IP 地址");
                } else {
                    SchedulerUtils.runAsync(plugin, () -> {
                        String location = getIPLocation(ip);
                        SchedulerUtils.runTask(plugin, sender, () -> {
                            if (location != null) {
                                Utils.sendMessage(sender, plugin.prefix() + "§a查询到玩家 " + target + " 的 IP 地址为 " + ip + "，地理位置：" + location);
                            } else {
                                Utils.sendMessage(sender, plugin.prefix() + "§a查询到玩家 " + target + " 的 IP 地址为 " + ip + "，但无法解析地理位置");
                            }
                        });
                    });
                }
                break;
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
                        availableModels.append(name).append(" ");
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
                if (args.length < 4) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式 /lban mute <玩家名> <时间/auto> <原因>");
                    return true;
                }
                String muteTarget = args[1];
                String muteTimeArg = args[2];
                long muteDuration;
                if (muteTimeArg.equalsIgnoreCase("auto")) {
                    muteDuration = calculateAutoBanTime(muteTarget);
                } else {
                    muteDuration = TimeUtils.parseDurationToMillis(muteTimeArg);
                    if (muteDuration <= 0) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c时间格式错误喵，请使用 10s, 5m, 2h, 7d, 1w, 1M, 1y, forever 或 auto。");
                        return true;
                    }
                }
                String muteReason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                try {
                    MuteEntry muteEntry = new MuteEntry(muteTarget, Utils.getSenderName(sender), TimeUtils.calculateEndTime(muteDuration), muteReason);
                    plugin.getMuteManager().mutePlayer(muteEntry);
                    Utils.broadcast(currentModel.addMute(muteTarget, muteReason));
                } catch (Exception e) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c禁言失败: " + e.getMessage());
                }
                break;
            case "unmute":
                if (!plugin.isFeatureEnabled("mute")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.mute")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式 /lban unmute <玩家名>");
                    return true;
                }
                String unmuteTarget = args[1];
                plugin.getMuteManager().unmutePlayer(unmuteTarget);
                Utils.broadcast(currentModel.removeMute(unmuteTarget));
                break;
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
                if (args.length < 3) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式：/lban warn <玩家名/IP> <原因>");
                    return true;
                }
                String warnTarget = args[1];
                String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getWarnManager().warnPlayer(warnTarget, Utils.getSenderName(sender), reason);
                Utils.sendMessage(sender, currentModel.addWarn(warnTarget, reason));
                break;
            case "unwarn":
                if (!plugin.isFeatureEnabled("unwarn")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.unwarn")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式：/lban unwarn <玩家名>");
                    return true;
                }
                String unwarnTarget = args[1];
                plugin.getWarnManager().getActiveWarnings(unwarnTarget).forEach(warn -> {
                    try {
                        int warnId = Integer.parseInt(warn.getId());
                        plugin.getWarnManager().unwarnPlayer(unwarnTarget, warnId);
                    } catch (NumberFormatException e) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c警告ID格式不对喵: " + warn.getId());
                    }
                });
                Utils.sendMessage(sender, currentModel.removeWarn(unwarnTarget));
                break;
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
                    plugin.getAuditManager().exportAudit(message -> Utils.sendMessage(sender, message), auditExportLimit);
                    break;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("verify")) {
                    if (!sender.hasPermission("lengbanlist.export")) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                        return true;
                    }
                    plugin.getAuditManager().verifyAudit(message -> Utils.sendMessage(sender, message));
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
                    Utils.sendMessage(sender, currentModel.getImmunityDenied(handleTarget));
                    return true;
                }
                boolean handleAuto = args[2].equalsIgnoreCase("auto");
                try {
                    long handleEndTime;
                    if (args[2].equalsIgnoreCase("forever")) {
                        handleEndTime = Long.MAX_VALUE;
                    } else if (handleAuto) {
                        org.leng.manager.EscalationManager.EscalationResult escalationResult = handleTarget.contains(".")
                                ? plugin.getEscalationManager().resolveIpBan(handleTarget)
                                : plugin.getEscalationManager().resolveBan(handleTarget);
                        if (escalationResult.offenseCount > 0) {
                            Utils.sendMessage(sender, currentModel.onEscalatedBan(handleTarget, escalationResult.offenseCount, TimeUtils.formatDuration(escalationResult.durationMillis)));
                        }
                        handleEndTime = TimeUtils.calculateEndTime(escalationResult.durationMillis);
                    } else {
                        long handleDuration = TimeUtils.parseDurationToMillis(args[2]);
                        if (handleDuration <= 0) {
                            Utils.sendMessage(sender, plugin.prefix() + "§c时间格式无效喵，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever, auto");
                            return true;
                        }
                        handleEndTime = TimeUtils.calculateEndTime(handleDuration);
                    }
                    String handleReason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : handleReport.getReason();
                    BanManager.BanMutationResult handleResult = plugin.getReportManager().tryBanFromReport(handleReport, Utils.getSenderName(sender), handleEndTime, handleReason, handleAuto);
                    if (!handleResult.isApplied()) {
                        BanMutationFeedback.sendFailure(msg -> Utils.sendMessage(sender, msg), handleResult, handleTarget, handleTarget.contains("."));
                        break;
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
                new org.leng.manager.SyncManager(plugin).execute(message -> Utils.sendMessage(sender, message));
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
                return new org.leng.commands.RollbackCommand(plugin).onCommand(sender, null, "lban rollback", rollbackArgs);
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

    private void openChestUI(Player player) {
        Inventory chest = Bukkit.createInventory(null, 54, "§bLengbanlist");

        ItemStack glass = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§7我只是个装饰物");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                chest.setItem(i, glass);
            }
        }

        ItemStack toggleBroadcast = createItem(
                Material.LEVER,
                "§a切换自动广播 (" + (plugin.isBroadcastEnabled() ? "开启" : "关闭") + ")",
                "§7/lban toggle",
                "§7开启或关闭自动广播",
                Sound.BLOCK_LEVER_CLICK,
                player
        );
        ItemStack broadcast = createItem(
                Material.NOTE_BLOCK,
                "§a广播封禁人数",
                "§7/lban a",
                "§7广播当前封禁人数",
                Sound.BLOCK_NOTE_BLOCK_PLING,
                player
        );
        ItemStack list = createItem(
                Material.WRITABLE_BOOK,
                "§a查看封禁名单",
                "§7/lban list",
                "§7查看被封禁的玩家列表",
                Sound.BLOCK_NOTE_BLOCK_HARP,
                player
        );
        ItemStack reload = createItem(
                Material.COMPARATOR,
                "§a重新加载配置",
                "§7/lban reload",
                "§7重新加载插件配置",
                Sound.BLOCK_NOTE_BLOCK_BELL,
                player
        );
        ItemStack addBan = createItem(
                Material.REDSTONE_BLOCK,
                "§a添加封禁",
                "§7/lban add",
                "§7添加一个玩家到封禁名单",
                Sound.BLOCK_NOTE_BLOCK_BASS,
                player
        );
        ItemStack removeBan = createItem(
                Material.EMERALD_BLOCK,
                "§a解除封禁",
                "§7/lban remove",
                "§7从封禁名单中移除一个玩家",
                Sound.BLOCK_NOTE_BLOCK_SNARE,
                player
        );
        ItemStack help = createItem(
                Material.BOOK,
                "§a帮助信息",
                "§7/lban help",
                "§7显示帮助信息",
                Sound.BLOCK_NOTE_BLOCK_FLUTE,
                player
        );
        ItemStack model = createItem(
                Material.NAME_TAG,
                "§a切换模型 (" + ModelManager.getInstance().getCurrentModelName() + ")",
                "§7/lban model",
                "§7当前模型: " + ModelManager.getInstance().getCurrentModelName(),
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                player
        );
        ItemStack sponsor = createItem(
                Material.GOLD_INGOT,
                "§6赞助作者",
                "§7ACTION_SPONSOR",
                "§7点击获取赞助链接：https://afdian.com/a/lengmc",
                Sound.BLOCK_NOTE_BLOCK_PLING,
                player
        );
        ItemStack mute = createItem(
                Material.BARRIER,
                "§a禁言玩家",
                "§7/lban mute",
                "§7禁言一个玩家",
                Sound.BLOCK_NOTE_BLOCK_BASS,
                player
        );
        ItemStack unmute = createItem(
                Material.MILK_BUCKET,
                "§a解除禁言",
                "§7/lban unmute",
                "§7解除一个玩家的禁言",
                Sound.BLOCK_NOTE_BLOCK_SNARE,
                player
        );
        ItemStack listMute = createItem(
                Material.BOOKSHELF,
                "§a查看禁言列表",
                "§7/lban list-mute",
                "§7查看被禁言的玩家列表",
                Sound.BLOCK_NOTE_BLOCK_HARP,
                player
        );

        chest.setItem(10, toggleBroadcast);
        chest.setItem(12, broadcast);
        chest.setItem(14, list);
        chest.setItem(16, reload);
        chest.setItem(20, addBan);
        chest.setItem(22, removeBan);
        chest.setItem(24, help);
        chest.setItem(28, model);
        chest.setItem(30, mute);
        chest.setItem(32, unmute);
        chest.setItem(34, listMute);
        chest.setItem(40, sponsor);

        player.openInventory(chest);
    }

    private ItemStack createItem(Material material, String displayName, String command, String description, Sound sound, Player player) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        List<String> lore = new ArrayList<>();
        lore.add(command);
        lore.add(description);
        meta.setLore(lore);
        item.setItemMeta(meta);

        if (sound != null && player != null) {
            SchedulerUtils.runTaskLater(plugin, player, () -> {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }, 1L);
        }

        return item;
    }

    private String getIPLocation(String ip) {
        try {
            String apiUrl = "https://ipapi.co/" + ip + "/json/";
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                plugin.getLogger().warning("IP API请求失败，状态码: " + responseCode);
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JsonObject jsonObject = gson.fromJson(response.toString(), JsonObject.class);

            if (jsonObject.has("error")) {
                plugin.getLogger().warning("IP API返回错误: " + jsonObject.get("reason").getAsString());
                return null;
            }

            String country = jsonObject.has("country_name") ? jsonObject.get("country_name").getAsString() : "未知国家";
            String region = jsonObject.has("region") ? jsonObject.get("region").getAsString() : "未知地区";
            String city = jsonObject.has("city") ? jsonObject.get("city").getAsString() : "未知城市";

            return country + ", " + region + ", " + city;
        } catch (Exception e) {
            plugin.getLogger().warning("解析IP地理位置时出错: " + e.getMessage());
            return null;
        }
    }

@EventHandler
public void onInventoryClick(InventoryClickEvent event) {
    if (event.getView().getTitle().equals("§bLengbanlist")) {
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();

        if (!plugin.isFeatureEnabled("chest-ui")) {
            plugin.sendFeatureDisabled(player);
            player.closeInventory();
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        ItemMeta clickMeta = clickedItem.getItemMeta();
        if (clickMeta.getLore() == null || clickMeta.getLore().isEmpty()) {
            return;
        }

        String command = clickMeta.getLore().get(0).replace("§7", "");
        player.closeInventory();

        switch (command) {
            case "/lban toggle":
            case "/lban a":
            case "/lban list":
            case "/lban reload":
            case "/lban help":
            case "/lban list-mute":
                player.performCommand(command.startsWith("/") ? command.substring(1) : command);
                break;
            case "/lban add":
                startChatWizard(player, "ban");
                break;
            case "/lban remove":
                startChatWizard(player, "unban");
                break;
            case "/lban model":
                plugin.getModelChoiceListener().openModelSelectionUI(player);
                break;
            case "ACTION_SPONSOR":
                player.spigot().sendMessage(
                        new net.md_5.bungee.api.chat.TextComponent(plugin.prefix() + "§6赞助作者："),
                        Utils.clickableUrl("§e【点击打开爱发电】", "https://afdian.com/a/lengmc")
                );
                break;
            case "/lban mute":
                startChatWizard(player, "mute");
                break;
            case "/lban unmute":
                startChatWizard(player, "unmute");
                break;
            default:
                if (command.startsWith("/")) {
                    player.performCommand(command.substring(1));
                }
                break;
        }
    }
}

public void startChatWizard(Player player, String action) {
    switch (action) {
        case "ban":
            if (!plugin.isFeatureEnabled("ban")) {
                plugin.sendFeatureDisabled(player);
                return;
            }
            break;
        case "unban":
            if (!plugin.isFeatureEnabled("unban")) {
                plugin.sendFeatureDisabled(player);
                return;
            }
            break;
        case "mute":
        case "unmute":
            if (!plugin.isFeatureEnabled("mute")) {
                plugin.sendFeatureDisabled(player);
                return;
            }
            break;
    }
    player.setMetadata("lengbanlist-action", new org.bukkit.metadata.FixedMetadataValue(plugin, action));
    switch (action) {
        case "ban":
            player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "playerID"));
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f玩家名或IP§e：");
            break;
        case "unban":
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f解封的玩家名或IP§e：");
            break;
        case "mute":
            player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "playerID"));
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f禁言的玩家名§e：");
            break;
        case "unmute":
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f解除禁言的玩家名§e：");
            break;
    }
}

public void handleChatWizard(Player player, String input) {
    if (!player.hasMetadata("lengbanlist-action")) return;

    String action = player.getMetadata("lengbanlist-action").get(0).asString();

    switch (action) {
        case "ban":
            if (!plugin.isFeatureEnabled("ban")) {
                plugin.sendFeatureDisabled(player);
                clearWizard(player);
                return;
            }
            handleBanWizard(player, input);
            break;
        case "unban":
            if (!plugin.isFeatureEnabled("unban")) {
                plugin.sendFeatureDisabled(player);
                clearWizard(player);
                return;
            }
            if (input.contains(".")) {
                plugin.getBanManager().tryUnbanIp(input, player.getName(), false);
            } else {
                plugin.getBanManager().tryUnbanPlayer(input, player.getName(), false);
            }
            clearWizard(player);
            break;
        case "mute":
            if (!plugin.isFeatureEnabled("mute")) {
                plugin.sendFeatureDisabled(player);
                clearWizard(player);
                return;
            }
            handleMuteWizard(player, input);
            break;
        case "unmute":
            if (!plugin.isFeatureEnabled("mute")) {
                plugin.sendFeatureDisabled(player);
                clearWizard(player);
                return;
            }
            plugin.getMuteManager().unmutePlayer(input);
            Utils.broadcast(ModelManager.getInstance().getCurrentModel().removeMute(input));
            clearWizard(player);
            break;
    }
}

private void handleBanWizard(Player player, String input) {
    String step = player.getMetadata("lengbanlist-step").get(0).asString();
    if (step.equals("playerID")) {
        player.setMetadata("lengbanlist-playerID", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
        player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "time"));
        Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f封禁时间§e（如：1d, 7d, forever）：");
    } else if (step.equals("time")) {
        if (!TimeUtils.isValidTime(input)) {
            Utils.sendMessage(player, plugin.prefix() + "§c时间格式无效喵，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever, auto");
            return;
        }
        player.setMetadata("lengbanlist-time", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
        player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "reason"));
        Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f封禁原因§e：");
    } else if (step.equals("reason")) {
        String playerID = player.getMetadata("lengbanlist-playerID").get(0).asString();
        String time = player.getMetadata("lengbanlist-time").get(0).asString();
        long duration;
        boolean isAuto = false;
        if (time.equalsIgnoreCase("auto")) {
            isAuto = true;
            duration = calculateAutoBanTime(playerID);
        } else {
            duration = TimeUtils.parseTime(time);
        }
        if (duration <= 0) {
            Utils.sendMessage(player, plugin.prefix() + "§c时间格式无效喵。");
            return;
        }
        long endTime = TimeUtils.calculateEndTime(duration);
        if (playerID.contains(".")) {
            if (!plugin.isFeatureEnabled("ban-ip")) {
                plugin.sendFeatureDisabled(player);
                clearWizard(player);
                return;
            }
            plugin.getBanManager().tryBanIp(new BanIpEntry(playerID, player.getName(), endTime, input, isAuto), false);
        } else {
            plugin.getBanManager().tryBanPlayer(new BanEntry(playerID, player.getName(), endTime, input, isAuto), false);
        }
        clearWizard(player);
    }
}

private void handleMuteWizard(Player player, String input) {
    String step = player.getMetadata("lengbanlist-step").get(0).asString();
    if (step.equals("playerID")) {
        player.setMetadata("lengbanlist-playerID", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
        player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "time"));
        Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f禁言时间§e（如：10m, 1d, forever, auto）：");
    } else if (step.equals("time")) {
        if (!TimeUtils.isValidTime(input)) {
            Utils.sendMessage(player, plugin.prefix() + "§c时间格式无效喵，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever, auto");
            return;
        }
        player.setMetadata("lengbanlist-time", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
        player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "reason"));
        Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f禁言原因§e：");
    } else if (step.equals("reason")) {
        String playerID = player.getMetadata("lengbanlist-playerID").get(0).asString();
        String time = player.getMetadata("lengbanlist-time").get(0).asString();
        long duration = time.equalsIgnoreCase("auto") ? calculateAutoBanTime(playerID) : TimeUtils.parseTime(time);
        MuteEntry entry = new MuteEntry(playerID, player.getName(), TimeUtils.calculateEndTime(duration), input);
        plugin.getMuteManager().mutePlayer(entry);
        Utils.broadcast(ModelManager.getInstance().getCurrentModel().addMute(playerID, input));
        clearWizard(player);
    }
}

private long calculateAutoBanTime(String playerName) {
    int warnCount = Math.max(0, plugin.getWarnManager().getActiveWarnings(playerName).size());
    switch (warnCount) {
        case 0:  return TimeUtils.daysToMillis(1);
        case 1:  return TimeUtils.daysToMillis(3);
        case 2:  return TimeUtils.daysToMillis(7);
        case 3:  return TimeUtils.daysToMillis(14);
        case 4:  return TimeUtils.daysToMillis(30);
        default: return Long.MAX_VALUE;
    }
}

private void clearWizard(Player player) {
    player.removeMetadata("lengbanlist-action", plugin);
    player.removeMetadata("lengbanlist-step", plugin);
    player.removeMetadata("lengbanlist-playerID", plugin);
    player.removeMetadata("lengbanlist-time", plugin);
}
}
