package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.utils.SchedulerUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** 异步获取指定玩家的 IP 地理位置信息。网络请求在异步线程执行，结果回调在主线程。 */
public class GetIPCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public GetIPCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lengbanlist.getip")) {
            sender.sendMessage(plugin.prefix() + "§c你没有权限使用该命令！");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                showIpLocation(player, player.getAddress().getAddress().getHostAddress(), "你");
            } else {
                sender.sendMessage(plugin.prefix() + "§c请指定一个玩家名称，例如: /lban getip <玩家名称>");
            }
        } else {
            Player targetPlayer = plugin.getServer().getPlayer(args[0]);
            if (targetPlayer != null) {
                showIpLocation(sender, targetPlayer.getAddress().getAddress().getHostAddress(), "玩家 " + targetPlayer.getName());
            } else {
                sender.sendMessage(plugin.prefix() + "§c未找到玩家：" + args[0]);
            }
        }
        return true;
    }

    private void showIpLocation(CommandSender sender, String ip, String who) {
        if (isLocalIp(ip)) {
            sender.sendMessage(plugin.prefix() + "§e" + who + " 的 IP 为 " + ip + "（本地/局域网地址，无法查询地理位置）");
            return;
        }
        getPlayerLocationAsync(ip, location -> {
            if (location != null) {
                sender.sendMessage(plugin.prefix() + "§a" + who + " 的 IP 地理位置为：§e" + location);
            } else {
                sender.sendMessage(plugin.prefix() + "§c无法获取 " + who + " 的 IP 地理位置信息！");
            }
        });
    }

    private boolean isLocalIp(String ip) {
        if (ip == null) return true;
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) return true;
        if (ip.startsWith("10.") || ip.startsWith("172.") || ip.startsWith("192.168.")) return true;
        if (ip.startsWith("fd") || ip.startsWith("fc")) return true;
        return false;
    }

    private void getPlayerLocationAsync(String ip, LocationInfoCallback callback) {
        SchedulerUtils.runAsync(plugin, () -> {
            String locationInfo = getIPLocation(ip);
            SchedulerUtils.runTask(plugin, () -> callback.onLocationInfoReceived(locationInfo));
        });
    }

    private String getIPLocation(String ip) {
        String apiUrl = "http://ip-api.com/json/" + ip + "?lang=zh-CN";
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
            if (jsonResponse.get("status").getAsString().equals("success")) {
                String country = jsonResponse.get("country").getAsString();
                String regionName = jsonResponse.get("regionName").getAsString();
                String city = jsonResponse.get("city").getAsString();
                return country + ", " + regionName + ", " + city;
            } else {
                plugin.getLogger().warning("[Lengbanlist] IP API 请求失败，错误信息: " + jsonResponse.get("message").getAsString());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Lengbanlist] Failed to fetch location for IP: " + ip + " - " + e.getMessage());
        }
        return null;
    }

    public interface LocationInfoCallback {
        void onLocationInfoReceived(String locationInfo);
    }
}
