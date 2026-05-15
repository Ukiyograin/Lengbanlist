package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.MuteEntry;
import org.leng.object.WarnEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HistoryCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public HistoryCommand(Lengbanlist plugin) {
        this.plugin = plugin;
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
            Utils.sendMessage(sender, plugin.prefix() + "§c用法: /" + label + " <玩家名>");
            return true;
        }

        String target = args[0];
        List<HistoryEntry> raw = new ArrayList<>();

        for (BanEntry ban : plugin.getDatabaseManager().getBansByPlayer(target)) {
            raw.add(new HistoryEntry(ban.getTime(), "ban", ban));
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
            entries.add(he.format());
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
                    boolean expired = b.isExpired();
                    boolean permanent = b.getTime() == Long.MAX_VALUE;
                    String expiredStr;
                    String expiryStr;
                    if (permanent) {
                        expiredStr = "§c否";
                        expiryStr = "永久";
                    } else if (expired) {
                        expiredStr = "§a是";
                        expiryStr = TimeUtils.timestampToReadable(b.getTime());
                    } else {
                        expiredStr = "§c否";
                        expiryStr = TimeUtils.timestampToReadable(b.getTime());
                    }
                    String autoTag = b.isAuto() ? " §7[LBAC自动]" : "";
                    return "§7- §c封禁 §7| 是否过期: " + expiredStr + " §7| 过期时间: §f" + expiryStr + " §7| 处理人: §b" + b.getStaff() + " §7| 原因: §f" + b.getReason() + autoTag;
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
