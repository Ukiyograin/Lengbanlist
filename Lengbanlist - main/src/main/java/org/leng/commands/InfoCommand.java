package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.GitHubUpdateChecker;
import org.leng.utils.Utils;
import java.lang.management.ManagementFactory;

public class InfoCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public InfoCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("info")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        if (!sender.hasPermission("lengbanlist.info")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }

        // 获取服务器信息
        String serverVersion = plugin.getServer().getVersion();
        String serverCore = getServerCoreName();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        double cpuLoad = getSystemCpuLoad();

        // 构建信息字符串
        StringBuilder infoMessage = new StringBuilder();
        infoMessage.append("§b§lLengbanlist 插件信息 §b§l").append(plugin.getDescription().getVersion()).append("\n");
        infoMessage.append("§7当前运行在：§b").append(serverVersion).append("\n");
        infoMessage.append("§7当前服务端核心：§b").append(serverCore).append("\n");
        infoMessage.append("§7当前内存占用：§b").append(usedMemory / (1024 * 1024)).append("MB / ").append(totalMemory / (1024 * 1024)).append("MB\n");
        infoMessage.append("§7当前在线玩家：§b").append(onlinePlayers).append("\n");
        infoMessage.append("§7当前CPU占用：§b").append(String.format("%.2f", cpuLoad)).append("%\n");

        // 异步检查更新
        if (!plugin.getConfig().getBoolean("disable-update-check", false)) {
            GitHubUpdateChecker.getLatestReleaseVersionAsync(plugin).thenAccept(latestVersion -> {
                String message;
                if (latestVersion == null) {
                    message = "§c检查更新失败，请检查网络连接或稍后再试。\n";
                } else if (GitHubUpdateChecker.compareVersions(plugin.getDescription().getVersion(), latestVersion) < 0) {
                    message = "§a发现新版本：§e" + latestVersion + "§a，当前版本：§e" + plugin.getDescription().getVersion() + "\n" +
                             "§b更新地址：§ehttps://github.com/LengMC/Lengbanlist/releases\n";
                } else {
                    message = "§a你正在使用最新版本：§e" + plugin.getDescription().getVersion() + "\n";
                }
                Utils.sendMessage(sender, infoMessage.toString() + message);
            });
        } else {
            infoMessage.append("§a更新检查已禁用\n");
            Utils.sendMessage(sender, infoMessage.toString());
        }

        return true;
    }

    private String getServerCoreName() {
        try {
            String serverPackage = plugin.getServer().getClass().getPackage().getName();
            if (serverPackage.contains("spigot")) return "Spigot";
            else if (serverPackage.contains("paper")) return "Paper";
            else if (serverPackage.contains("leaves")) return "Leaves";
            else if (serverPackage.contains("bukkit")) return "Bukkit";
            else return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private double getSystemCpuLoad() {
        try {
            com.sun.management.OperatingSystemMXBean osBean = 
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            return osBean.getSystemCpuLoad() * 100;
        } catch (Exception e) {
            return 0.0;
        }
    }
}