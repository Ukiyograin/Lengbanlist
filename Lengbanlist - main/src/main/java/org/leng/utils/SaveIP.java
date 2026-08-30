package org.leng.utils;

import org.bukkit.entity.Player;
import org.leng.Lengbanlist;

import java.util.List;

public class SaveIP {
    public static boolean isRealIP(String ip) {
        if (ip == null) return false;
        if (ip.startsWith("10.") || ip.startsWith("127.") || ip.startsWith("192.168.")) {
            return false;
        }
        if (ip.startsWith("172.")) {
            int dot = ip.indexOf('.', 4);
            if (dot > 0) {
                try {
                    int secondOctet = Integer.parseInt(ip.substring(4, dot));
                    if (secondOctet >= 16 && secondOctet <= 31) {
                        return false;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (ip.equalsIgnoreCase("::1")) {
            return false;
        }
        if (ip.startsWith("fd") || ip.startsWith("fc")) {
            return false;
        }
        return true;
    }

    public static void saveIP(Player player) {
        java.net.InetSocketAddress addr = player.getAddress();
        if (addr == null) return;
        java.net.InetAddress inet = addr.getAddress();
        if (inet == null) return;
        String newIP = inet.getHostAddress();
        if (newIP == null || !isRealIP(newIP)) return;
        Lengbanlist plugin = Lengbanlist.getInstance();
        plugin.getDatabaseManager().upsertPlayerIp(player.getName(), newIP, System.currentTimeMillis());
        plugin.getDatabaseManager().recordPlayerIp(player.getName(), newIP, System.currentTimeMillis());
    }

    public static String getIP(String player) {
        return Lengbanlist.getInstance().getDatabaseManager().getPlayerIp(player);
    }

    public static List<String> getPlayersByIp(String ip) {
        return Lengbanlist.getInstance().getDatabaseManager().getPlayersByIp(ip);
    }
}
