package org.leng.manager;

import org.bukkit.entity.Player;
import org.leng.Lengbanlist;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class IpAssociationManager {
    private final Lengbanlist plugin;

    public IpAssociationManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    private static String safeGetHostAddress(Player player) {
        java.net.InetSocketAddress addr = player.getAddress();
        if (addr == null || addr.getAddress() == null) return null;
        return addr.getAddress().getHostAddress();
    }

    public void recordLogin(Player player) {
        java.net.InetSocketAddress addr = player.getAddress();
        if (addr == null || addr.getAddress() == null) return;
        String ip = addr.getAddress().getHostAddress();
        if (ip == null || !isRealIp(ip)) return;
        plugin.getDatabaseManager().recordPlayerIp(player.getName(), ip, System.currentTimeMillis());
    }

    public List<String[]> getPlayerIps(String playerName) {
        return plugin.getDatabaseManager().getPlayerIpHistory(playerName);
    }

    public List<String> getPlayersByIp(String ip) {
        return plugin.getDatabaseManager().getPlayersByIpFromHistory(ip);
    }

    public static class AltAccount {
        public final String name;
        public final boolean banned;
        public final boolean currentIp;

        public AltAccount(String name, boolean banned, boolean currentIp) {
            this.name = name;
            this.banned = banned;
            this.currentIp = currentIp;
        }
    }

    public List<AltAccount> getAlts(String target) {
        List<AltAccount> result = new ArrayList<>();
        String ip = plugin.getDatabaseManager().getPlayerIp(target);
        if (ip == null || ip.isEmpty()) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (String name : plugin.getDatabaseManager().getPlayersByIp(ip)) {
            seen.add(name.toLowerCase());
            result.add(new AltAccount(name, plugin.getBanManager().isPlayerBanned(name), true));
        }
        for (String name : plugin.getDatabaseManager().getPlayersByIpFromHistory(ip)) {
            if (!seen.contains(name.toLowerCase())) {
                seen.add(name.toLowerCase());
                result.add(new AltAccount(name, plugin.getBanManager().isPlayerBanned(name), false));
            }
        }
        return result;
    }

    public Map<String, List<String>> getAssociatedPlayers(String playerName) {
        Map<String, List<String>> result = new HashMap<>();
        List<String[]> ipHistory = getPlayerIps(playerName);
        for (String[] record : ipHistory) {
            String ip = record[0];
            List<String> players = getPlayersByIp(ip);
            List<String> others = new ArrayList<>();
            for (String p : players) {
                if (!p.equalsIgnoreCase(playerName)) {
                    others.add(p);
                }
            }
            if (!others.isEmpty()) {
                result.put(ip, others);
            }
        }
        return result;
    }

    public Set<String> getAllAssociatedPlayerNames(String playerName) {
        Set<String> all = new HashSet<>();
        Map<String, List<String>> assoc = getAssociatedPlayers(playerName);
        for (List<String> players : assoc.values()) {
            all.addAll(players);
        }
        return all;
    }

    public boolean hasSuspiciousLogin(Player player) {
        String ip = safeGetHostAddress(player);
        if (ip == null || !isRealIp(ip)) return false;
        List<String> players = getPlayersByIp(ip);
        for (String p : players) {
            if (!p.equalsIgnoreCase(player.getName())) return true;
        }
        return false;
    }

    public List<String> getSuspiciousLoginDetails(Player player) {
        String ip = safeGetHostAddress(player);        List<String> details = new ArrayList<>();
        List<String> players = getPlayersByIp(ip);
        for (String p : players) {
            if (!p.equalsIgnoreCase(player.getName())) {
                details.add(p);
            }
        }
        return details;
    }

    public boolean isVpnIp(String ip) {
        try (org.leng.utils.HttpHelper http = new org.leng.utils.HttpHelper(3000, 3000)) {
            String apiUrl = "https://ip-api.com/json/" + ip + "?fields=status,proxy,hosting";
            String response = http.get(apiUrl, "Lengbanlist-VPNCheck/1.0", "*/*");
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if ("success".equals(json.get("status").getAsString())) {
                boolean proxy = json.has("proxy") && json.get("proxy").getAsBoolean();
                boolean hosting = json.has("hosting") && json.get("hosting").getAsBoolean();
                return proxy || hosting;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("VPN检测请求失败: " + e.getMessage());
        }
        return false;
    }

    public static boolean isRealIp(String ip) {
        return org.leng.utils.SaveIP.isRealIP(ip);
    }
}
