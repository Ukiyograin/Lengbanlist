package org.leng.utils;

import org.bukkit.entity.Player;
import org.leng.Lengbanlist;

import java.util.List;

public class SaveIP {
    private static boolean isRealIP(String ip) {
        if (ip != null) {
            if (ip.startsWith("10.") || ip.startsWith("172.") || ip.startsWith("192.168.") || ip.startsWith("127.")) {
                return false;
            }
            if (ip.equalsIgnoreCase("::1")) {
                return false;
            }
            if (ip.startsWith("fd")) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static void saveIP(Player player) {
        String newIP = player.getAddress().getAddress().getHostAddress();
        if (isRealIP(newIP)) {
            Lengbanlist.getInstance().getDatabaseManager().upsertPlayerIp(player.getName(), newIP, System.currentTimeMillis());
        }
    }

    public static String getIP(String player) {
        return Lengbanlist.getInstance().getDatabaseManager().getPlayerIp(player);
    }

    public static List<String> getPlayersByIp(String ip) {
        return Lengbanlist.getInstance().getDatabaseManager().getPlayersByIp(ip);
    }
}
