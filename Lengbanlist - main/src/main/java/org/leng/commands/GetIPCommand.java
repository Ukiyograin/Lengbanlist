package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

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

        // 如果没有提供参数，尝试获取发送者的 IP（如果发送者是玩家）
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                String playerIp = player.getAddress().getAddress().getHostAddress();
                getPlayerLocationAsync(playerIp, location -> {
                    if (location != null) {
                        player.sendMessage(plugin.prefix() + "§a你的 IP 地理位置为：§e" + location);
                    } else {
                        player.sendMessage(plugin.prefix() + "§c无法获取 IP 地理位置信息，请检查网络连接！");
                    }
                });
            } else {
                sender.sendMessage(plugin.prefix() + "§c请指定一个玩家名称，例如: /lban getip <玩家名称>");
            }
        } else {
            // 如果提供了玩家名称，尝试获取该玩家的 IP
            Player targetPlayer = plugin.getServer().getPlayer(args[0]);
            if (targetPlayer != null) {
                String playerIp = targetPlayer.getAddress().getAddress().getHostAddress();
                getPlayerLocationAsync(playerIp, location -> {
                    if (location != null) {
                        sender.sendMessage(plugin.prefix() + "§a玩家 " + targetPlayer.getName() + " 的 IP 地理位置为：§e" + location);
                    } else {
                        sender.sendMessage(plugin.prefix() + "§c无法获取玩家 " + targetPlayer.getName() + " 的 IP 地理位置信息！");
                    }
                });
            } else {
                sender.sendMessage(plugin.prefix() + "§c未找到玩家：" + args[0]);
            }
        }
        return true;
    }

    /**
     * 异步方式获取指定IP的地理位置信息。
     *
     * @param ip       目标IP地址
     * @param callback 当获取到地理位置信息后将通过这个回调返回
     */
    private void getPlayerLocationAsync(String ip, LocationInfoCallback callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String locationInfo = getIPLocation(ip);
            // 确保回调在主线程执行
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.onLocationInfoReceived(locationInfo));
        });
    }

    /**
     * 从外部API获取IP地址对应的地理位置信息。
     *
     * @param ip 目标IP地址
     * @return 返回地理位置信息，如果失败则返回null。
     */
    private String getIPLocation(String ip) {
        String apiUrl = "http://ip-api.com/json/" + ip + "?lang=zh-CN"; 
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
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

    /**
     * 地理位置信息获取完成后的回调接口。
     */
    public interface LocationInfoCallback {
        void onLocationInfoReceived(String locationInfo);
    }
}