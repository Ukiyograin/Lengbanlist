package org.leng.models;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface Model {
    String getName();

    void showHelp(CommandSender sender);

    String toggleBroadcast(boolean enabled);

    String reloadConfig();

    String addBan(String player, int days, String reason);

    String removeBan(String player);

    String addMute(String player, String reason);

    String removeMute(String player);

    String addBanIp(String ip, int days, String reason);

    String removeBanIp(String ip);

    String addWarn(String player, String reason);

    String removeWarn(String player);

    String getKickMessage(String reason);

    String onKickSuccess(String playerName, String reason);

    String getHistory(String player, List<String> entries);
}