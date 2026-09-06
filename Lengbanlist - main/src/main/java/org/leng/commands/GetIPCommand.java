package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.utils.IpGeoLookup;
import org.leng.utils.SaveIP;
import org.leng.utils.SchedulerUtils;

import java.net.InetSocketAddress;


public class GetIPCommand implements CommandExecutor {
    private final Lengbanlist plugin;
    private final IpGeoLookup ipGeoLookup;

    public GetIPCommand(Lengbanlist plugin) {
        this.plugin = plugin;
        this.ipGeoLookup = new IpGeoLookup(plugin);
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
                java.net.InetSocketAddress addr = player.getAddress();
                if (addr == null || addr.getAddress() == null) {
                    sender.sendMessage(plugin.prefix() + "§c无法获取你的地址");
                    return true;
                }
                showIpLocation(player, addr.getAddress().getHostAddress(), "你");
            } else {
                sender.sendMessage(plugin.prefix() + "§c请指定一个玩家名称，例如: /lban getip <玩家名称>");
            }
        } else {
            // 在线：拿实时地址；离线：拿 SaveIP 缓存（兼容 /lban getip 离线查询能力）
            Player targetPlayer = plugin.getServer().getPlayer(args[0]);
            if (targetPlayer != null) {
                java.net.InetSocketAddress addr = targetPlayer.getAddress();
                if (addr == null || addr.getAddress() == null) {
                    sender.sendMessage(plugin.prefix() + "§c该玩家没有可用地址");
                    return true;
                }
                showIpLocation(sender, addr.getAddress().getHostAddress(), "玩家 " + targetPlayer.getName());
            } else {
                String cachedIp = SaveIP.getIP(args[0]);
                if (cachedIp == null) {
                    sender.sendMessage(plugin.prefix() + "§c未找到玩家：" + args[0]);
                    return true;
                }
                showIpLocation(sender, cachedIp, "玩家 " + args[0]);
            }
        }
        return true;
    }

    private void showIpLocation(CommandSender sender, String ip, String who) {
        if (isLocalIp(ip)) {
            sender.sendMessage(plugin.prefix() + "§e" + who + " 的 IP 为 " + ip + "（本地/局域网地址，无法查询地理位置）");
            return;
        }
        getPlayerLocationAsync(ip, sender, location -> {
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

    private void getPlayerLocationAsync(String ip, CommandSender sender, LocationInfoCallback callback) {
        SchedulerUtils.runAsync(plugin, () -> {
            String locationInfo = ipGeoLookup.lookup(ip);
            SchedulerUtils.runTask(plugin, sender, () -> callback.onLocationInfoReceived(locationInfo));
        });
    }

    public interface LocationInfoCallback {
        void onLocationInfoReceived(String locationInfo);
    }
}
