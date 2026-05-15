package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class English implements Model {
    @Override
    public String getName() {
        return "English";
    }

    @Override
    public void showHelp(CommandSender sender) {
        Utils.sendMessage(sender, "§b╔══════════════════════════════════════╗");
        Utils.sendMessage(sender, "§b║ §2§oLengbanlist Help - English §b║");
        Utils.sendMessage(sender, "§b╠══════════════════════════════════════╣");
        Utils.sendMessage(sender, "§6§l◆ Punishments");
        Utils.sendMessage(sender, "§2✦ §b/lban add <player> <days> <reason> §7- §3Ban a player");
        Utils.sendMessage(sender, "§7  = §b/ban");
        Utils.sendMessage(sender, "§2✦ §b/lban remove <player> §7- §3Unban a player");
        Utils.sendMessage(sender, "§7  = §b/unban");
        Utils.sendMessage(sender, "§2✦ §b/ban-ip <IP> <days> <reason> §7- §3Ban an IP address");
        Utils.sendMessage(sender, "§2✦ §b/lban mute <player> <reason> §7- §3Mute a player");
        Utils.sendMessage(sender, "§7  = §b/mute");
        Utils.sendMessage(sender, "§2✦ §b/lban unmute <player> §7- §3Unmute a player");
        Utils.sendMessage(sender, "§7  = §b/unmute");
        Utils.sendMessage(sender, "§2✦ §b/lban warn <player> <reason> §7- §3Warn a player, 3 = auto-ban");
        Utils.sendMessage(sender, "§7  = §b/warn");
        Utils.sendMessage(sender, "§2✦ §b/lban unwarn <player> §7- §3Remove player warnings");
        Utils.sendMessage(sender, "§7  = §b/unwarn");
        Utils.sendMessage(sender, "§2✦ §b/kick <player> <reason> §7- §3Kick a player");
        Utils.sendMessage(sender, "§2✦ §b/setban <player/IP> <time/forever/auto> <reason> §7- §3Modify ban time");
        Utils.sendMessage(sender, "§6§l◆ Information");
        Utils.sendMessage(sender, "§2✦ §b/lban check <player/IP> §7- §3Check ban status");
        Utils.sendMessage(sender, "§2✦ §b/lban history <player> §7- §3Query punishment history");
        Utils.sendMessage(sender, "§7  = §b/history");
        Utils.sendMessage(sender, "§2✦ §b/report <player> <reason> §7- §3Report a player");
        Utils.sendMessage(sender, "§2✦ §b/lban getip <player> §7- §3Query player IP address");
        Utils.sendMessage(sender, "§6§l◆ Miscellaneous");
        Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3View ban list");
        Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3View mute list");
        Utils.sendMessage(sender, "§7  = §b/listmute");
        Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3Broadcast ban count");
        Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3Toggle auto broadcast");
        Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3Open visual operation UI");
        Utils.sendMessage(sender, "§2✦ §b/lban model <model> §7- §3Switch model");
        Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3Reload configuration");
        Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3View plugin info");
        Utils.sendMessage(sender, "§b╚══════════════════════════════════════╝");
        Utils.sendMessage(sender, "§2♡ Current Version: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §bModel: English");
    }

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §dEnglish Model Kick Notice  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ You have been kicked from the server!\n\n" +
               "§7Reason: §f" + reason + "\n\n" +
               "§dPlease follow the rules next time~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ English Model: §a" + playerName + " §ehas been kicked!\n" +
               "§bReason: §f" + reason + "\n" +
               "§bMaintaining order, no disruption allowed! §b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§bEnglish Model: §aAutomatic broadcast has been " + (enabled ? "enabled" : "disabled");
    }

    @Override
    public String reloadConfig() {
        return "§bEnglish Model: §aConfiguration reloaded successfully";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        String durationText = (days == Integer.MAX_VALUE / (1000 * 60 * 60 * 24)) ? "permanently" : days + " days";
        return "§bEnglish Model: §aPlayer " + player + " has been banned for " + durationText + ", reason: " + reason;
    }

    @Override
    public String removeBan(String player) {
        return "§bEnglish Model: §aPlayer " + player + " has been removed from ban list";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§bEnglish Model: §aPlayer " + player + " has been muted, reason: " + reason;
    }

    @Override
    public String removeMute(String player) {
        return "§bEnglish Model: §aPlayer " + player + " has been unmuted";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        String durationText = (days == Integer.MAX_VALUE / (1000 * 60 * 60 * 24)) ? "permanently" : days + " days";
        return "§bEnglish Model: §aIP " + ip + " has been banned for " + durationText + ", reason: " + reason;
    }

    @Override
    public String removeBanIp(String ip) {
        return "§bEnglish Model: §aIP " + ip + " has been unbanned";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§bEnglish Model: §aPlayer " + player + " has been warned, reason: " + reason + ". 3 warnings will result in automatic ban.";
    }

    @Override
    public String removeWarn(String player) {
        return "§bEnglish Model: §aWarning records for " + player + " have been removed.";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§bEnglish Model: §aPlayer " + player + " has a clean record. Good job!";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§bEnglish Model: §aPunishment history for ").append(player).append(":\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        return sb.toString().trim();
    }
}