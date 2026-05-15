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
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
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
            currentModel.showHelp(sender);
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
    String reloadDefaultMessage = plugin.getConfig().getString("default-message");
    if (reloadDefaultMessage != null) {
        plugin.getServer().broadcastMessage(
            reloadDefaultMessage.replace("%s", String.valueOf(plugin.getBanManager().getBanList().size()))
        );
    }
    Utils.sendMessage(sender, currentModel.reloadConfig());
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
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式错误，正确格式: /lban add <玩家名/IP> <时间/auto> <原因>");
                    return true;
                }
                try {
                    long durationLong = TimeUtils.parseTime(args[2]);
                    if (durationLong > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("§c封禁时间过长，最大支持 " + Integer.MAX_VALUE + " 毫秒（约24.8天）");
                    }
                    int duration = (int) durationLong;

                    if (args[1].contains(".")) {
                        plugin.getBanManager().banIp(new BanIpEntry(args[1], sender.getName(), duration, args[3], false));
                        Utils.sendMessage(sender, currentModel.addBanIp(args[1], duration, args[3]));
                    } else {
                        plugin.getBanManager().banPlayer(new BanEntry(args[1], sender.getName(), duration, args[3], false));
                        Utils.sendMessage(sender, currentModel.addBan(args[1], duration, args[3]));
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
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式/lban remove <玩家名/IP>");
                    return true;
                }
                if (args[1].contains(".")) {
                    plugin.getBanManager().unbanIp(args[1]);
                    Utils.sendMessage(sender, currentModel.removeBanIp(args[1]));
                } else {
                    plugin.getBanManager().unbanPlayer(args[1]);
                    Utils.sendMessage(sender, currentModel.removeBan(args[1]));
                }
                break;
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
                if (!sender.hasPermission("lengbanlist.getIP")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式 /lban getip <玩家名>");
                    return false;
                }
                String target = args[1];
                String ip = SaveIP.getIP(target);
                if (ip == null) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l查询不到玩家 " + target + " 的 IP 地址");
                } else {
                    SchedulerUtils.runAsync(plugin, () -> {
                        String location = getIPLocation(ip);
                        SchedulerUtils.runTask(plugin, () -> {
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
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式/lban model <模型名称>");
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
                    Utils.sendMessage(sender, plugin.prefix() + "§c不支持的模型名称。");
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
                if (args.length < 3) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式 /lban mute <玩家名> <原因>");
                    return true;
                }
                String muteTarget = args[1];
                String muteReason = args[2];
                try {
                    MuteEntry muteEntry = new MuteEntry(muteTarget, sender.getName(), System.currentTimeMillis(), muteReason);
                    plugin.getMuteManager().mutePlayer(muteEntry);
                    Utils.sendMessage(sender, currentModel.addMute(muteTarget, muteReason));
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
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式 /lban unmute <玩家名>");
                    return true;
                }
                String unmuteTarget = args[1];
                plugin.getMuteManager().unmutePlayer(unmuteTarget);
                Utils.sendMessage(sender, currentModel.removeMute(unmuteTarget));
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
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式：/lban warn <玩家名/IP> <原因>");
                    return true;
                }
                String warnTarget = args[1];
                String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getWarnManager().warnPlayer(warnTarget, sender.getName(), reason);
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
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式：/lban unwarn <玩家名>");
                    return true;
                }
                String unwarnTarget = args[1];
                plugin.getWarnManager().getActiveWarnings(unwarnTarget).forEach(warn -> {
                    try {
                        int warnId = Integer.parseInt(warn.getId()); // 字符串转整数
                        plugin.getWarnManager().unwarnPlayer(unwarnTarget, warnId);
                    } catch (NumberFormatException e) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c警告ID格式错误: " + warn.getId());
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
            // 转发给ReportCommand处理，去掉"report"参数
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
                Utils.sendMessage(sender, plugin.prefix() + "§c用法: /lban tp <玩家名>");
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
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式：/lban check <玩家名/IP>");
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
                    "report", "admin", "check", "info", "tp"};
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
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(prefix)) completions.add(p.getName());
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
            Utils.sendMessage(sender, "§9§o被禁言者: " + entry.getTarget() + " §6处理人: " + entry.getStaff() + " §d禁言时间: " + TimeUtils.timestampToReadable(entry.getTime()) + " §l§n禁言原因: " + entry.getReason());
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
                "§a切换自动广播 (" + (plugin.isBroadcastEnabled() ? "开启" : "关闭") + ")",
                "§7/lban toggle",
                "§7开启或关闭自动广播",
                Sound.BLOCK_LEVER_CLICK,
                player
        );
        ItemStack broadcast = createItem(
                "§a广播封禁人数", 
                "§7/lban a", 
                "§7广播当前封禁人数", 
                Sound.BLOCK_NOTE_BLOCK_PLING,
                player
        );
        ItemStack list = createItem(
                "§a查看封禁名单", 
                "§7/lban list", 
                "§7查看被封禁的玩家列表", 
                Sound.BLOCK_NOTE_BLOCK_HARP,
                player
        );
        ItemStack reload = createItem(
                "§a重新加载配置", 
                "§7/lban reload", 
                "§7重新加载插件配置", 
                Sound.BLOCK_NOTE_BLOCK_BELL,
                player
        );
        ItemStack addBan = createItem(
                "§a添加封禁", 
                "§7/lban add", 
                "§7添加一个玩家到封禁名单", 
                Sound.BLOCK_NOTE_BLOCK_BASS,
                player
        );
        ItemStack removeBan = createItem(
                "§a解除封禁", 
                "§7/lban remove", 
                "§7从封禁名单中移除一个玩家", 
                Sound.BLOCK_NOTE_BLOCK_SNARE,
                player
        );
        ItemStack help = createItem(
                "§a帮助信息", 
                "§7/lban help", 
                "§7显示帮助信息", 
                Sound.BLOCK_NOTE_BLOCK_FLUTE,
                player
        );
        ItemStack model = createItem(
                "§a切换模型 (" + ModelManager.getInstance().getCurrentModelName() + ")",
                "§7/lban model",
                "§7当前模型: " + ModelManager.getInstance().getCurrentModelName(),
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                player
        );
        ItemStack sponsor = createItem(
                "§6赞助作者", 
                "§7点击打开赞助链接", 
                "§7https://afdian.com/a/lengmc",
                Sound.BLOCK_NOTE_BLOCK_PLING,
                player
        );
        ItemStack mute = createItem(
                "§a禁言玩家", 
                "§7/lban mute", 
                "§7禁言一个玩家", 
                Sound.BLOCK_NOTE_BLOCK_BASS,
                player
        );
        ItemStack unmute = createItem(
                "§a解除禁言", 
                "§7/lban unmute", 
                "§7解除一个玩家的禁言", 
                Sound.BLOCK_NOTE_BLOCK_SNARE,
                player
        );
        ItemStack listMute = createItem(
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

    private ItemStack createItem(String displayName, String command, String description, Sound sound, Player player) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        List<String> lore = new ArrayList<>();
        lore.add(command);
        lore.add(description);
        meta.setLore(lore);
        item.setItemMeta(meta);

        if (sound != null && player != null) {
            SchedulerUtils.runTaskLater(plugin, () -> {
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

        String command = clickedItem.getItemMeta().getLore().get(0).replace("§7", "");
        player.closeInventory();

        switch (command) {
            case "/lban toggle":
            case "/lban a":
            case "/lban list":
            case "/lban reload":
            case "/lban help":
            case "/lban list-mute":
                player.performCommand(command);
                break;
            case "/lban add":
                startChatWizard(player, "ban");
                break;
            case "/lban remove":
                startChatWizard(player, "unban");
                break;
            case "/lban model":
                ModelManager.getInstance().openModelSelectionUI(player);
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
                plugin.getBanManager().unbanIp(input);
                Utils.sendMessage(player, plugin.prefix() + "§a解封IP成功：" + input);
            } else {
                plugin.getBanManager().unbanPlayer(input);
                Utils.sendMessage(player, plugin.prefix() + "§a解封玩家成功：" + input);
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
            Utils.sendMessage(player, plugin.prefix() + "§a解除禁言成功：" + input);
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
            Utils.sendMessage(player, plugin.prefix() + "§c时间格式无效，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever");
            return;
        }
        player.setMetadata("lengbanlist-time", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
        player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "reason"));
        Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f封禁原因§e：");
    } else if (step.equals("reason")) {
        String playerID = player.getMetadata("lengbanlist-playerID").get(0).asString();
        String time = player.getMetadata("lengbanlist-time").get(0).asString();
        long duration = TimeUtils.parseTime(time);
        if (playerID.contains(".")) {
            if (!plugin.isFeatureEnabled("ban-ip")) {
                plugin.sendFeatureDisabled(player);
                clearWizard(player);
                return;
            }
            plugin.getBanManager().banIp(new BanIpEntry(playerID, player.getName(), duration, input, false));
            Utils.sendMessage(player, plugin.prefix() + "§a封禁IP成功：" + playerID);
        } else {
            plugin.getBanManager().banPlayer(new BanEntry(playerID, player.getName(), duration, input, false));
            Utils.sendMessage(player, plugin.prefix() + "§a封禁玩家成功：" + playerID);
        }
        clearWizard(player);
    }
}

private void handleMuteWizard(Player player, String input) {
    String step = player.getMetadata("lengbanlist-step").get(0).asString();
    if (step.equals("playerID")) {
        player.setMetadata("lengbanlist-playerID", new org.bukkit.metadata.FixedMetadataValue(plugin, input));
        player.setMetadata("lengbanlist-step", new org.bukkit.metadata.FixedMetadataValue(plugin, "reason"));
        Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f禁言原因§e：");
    } else if (step.equals("reason")) {
        String playerID = player.getMetadata("lengbanlist-playerID").get(0).asString();
        MuteEntry entry = new MuteEntry(playerID, player.getName(), System.currentTimeMillis(), input);
        plugin.getMuteManager().mutePlayer(entry);
        Utils.sendMessage(player, plugin.prefix() + "§a禁言玩家成功：" + playerID);
        clearWizard(player);
    }
}

private void clearWizard(Player player) {
    player.removeMetadata("lengbanlist-action", plugin);
    player.removeMetadata("lengbanlist-step", plugin);
    player.removeMetadata("lengbanlist-playerID", plugin);
    player.removeMetadata("lengbanlist-time", plugin);
}
}