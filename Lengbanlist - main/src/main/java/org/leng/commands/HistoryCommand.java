package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.WarnEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HistoryCommand implements CommandExecutor, TabCompleter {
    private final Lengbanlist plugin;

    public HistoryCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("history")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        if (!sender.hasPermission("lengbanlist.history")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }
        if (args.length < 1) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法: /" + label + " <玩家名/IP>");
            return true;
        }

        String target = args[0];
        List<HistoryEntry> raw = new ArrayList<>();

        boolean isIp = target.contains(".");

        if (isIp) {
            for (BanIpEntry ban : plugin.getDatabaseManager().getIpBansByIp(target)) {
                raw.add(new HistoryEntry(ban.getTime(), "ipban", ban));
            }
        } else {
            for (BanEntry ban : plugin.getDatabaseManager().getBansByPlayer(target)) {
                raw.add(new HistoryEntry(ban.getTime(), "ban", ban));
            }
        }

        for (MuteEntry mute : plugin.getDatabaseManager().getMutesByPlayer(target)) {
            raw.add(new HistoryEntry(mute.getTime(), "mute", mute));
        }

        for (WarnEntry warn : plugin.getDatabaseManager().getWarnings(target, false)) {
            raw.add(new HistoryEntry(warn.getTime(), "warn", warn));
        }

        raw.sort(Comparator.comparingLong(e -> e.time));

        List<String> entries = new ArrayList<>();
        for (HistoryEntry he : raw) {
            String line = he.format();
            if (!line.isEmpty()) entries.add(line);
        }

        if (entries.isEmpty()) {
            if (isIp) {
                Utils.sendMessage(sender, plugin.prefix() + "§7该IP暂无封禁记录。");
            } else {
                Utils.sendMessage(sender, plugin.prefix() + "§7该玩家暂无处罚记录。");
            }
            return true;
        }

        Model model = ModelManager.getInstance().getCurrentModel();
        String result = model.getHistory(target, entries);
        for (String line : result.split("\n")) {
            Utils.sendMessage(sender, line);
        }
        return true;
    }

    private static class HistoryEntry {
        final long time;
        final String type;
        final Object data;

        HistoryEntry(long time, String type, Object data) {
            this.time = time;
            this.type = type;
            this.data = data;
        }

        String format() {
            switch (type) {
                case "ban": {
                    BanEntry b = (BanEntry) data;
                    boolean inactive = !b.isActive();
                    boolean expired = b.isExpired();
                    boolean permanent = b.getTime() == Long.MAX_VALUE;
                    if (inactive || expired) {
                        String expiryAt = permanent ? "永久" : TimeUtils.timestampToReadable(b.getTime());
                        return "§7- §c封禁 §7| §a已过期 §7| 过期时间: §f" + expiryAt + " §7| 处理人: §b" + b.getStaff() + " §7| 原因: §f" + b.getReason();
                    }
                    String expiryStr = permanent ? "永久" : TimeUtils.timestampToReadable(b.getTime());
                    return "§7- §c封禁 §7| 处理人: §b" + b.getStaff() + " §7| 封禁至: §f" + expiryStr + " §7| 原因: §f" + b.getReason();
                }
                case "ipban": {
                    BanIpEntry b = (BanIpEntry) data;
                    boolean inactive = !b.isActive();
                    boolean expired = b.isExpired();
                    boolean permanent = b.getTime() == Long.MAX_VALUE;
                    if (inactive || expired) {
                        String expiryAt = permanent ? "永久" : TimeUtils.timestampToReadable(b.getTime());
                        return "§7- §cIP封禁 §7| §a已过期 §7| 过期时间: §f" + expiryAt + " §7| 处理人: §b" + b.getStaff() + " §7| 原因: §f" + b.getReason();
                    }
                    String expiryStr = permanent ? "永久" : TimeUtils.timestampToReadable(b.getTime());
                    return "§7- §cIP封禁 §7| 处理人: §b" + b.getStaff() + " §7| 封禁至: §f" + expiryStr + " §7| 原因: §f" + b.getReason();
                }
                case "mute": {
                    MuteEntry m = (MuteEntry) data;
                    return "§7- §9禁言 §7| 处理人: §b" + m.getStaff() + " §7| 时间: §f" + TimeUtils.timestampToReadable(m.getTime()) + " §7| 原因: §f" + m.getReason();
                }
                case "warn": {
                    WarnEntry w = (WarnEntry) data;
                    String status = w.isRevoked() ? "§a是" : "§c否";
                    return "§7- §e警告 §7| 是否撤销: " + status + " §7| 处理人: §b" + w.getStaff() + " §7| 时间: §f" + TimeUtils.timestampToReadable(w.getTime()) + " §7| 原因: §f" + w.getReason();
                }
                default:
                    return "";
            }
        }
    }
}
