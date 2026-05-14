package org.leng.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.leng.Lengbanlist;
import org.leng.manager.ReportManager;
import org.leng.utils.GitHubUpdateChecker;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;

import java.util.Calendar;

public class OpJoinListener implements Listener {
    private final Lengbanlist plugin;

    public OpJoinListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    if (player.isOp()) {
        if (plugin.isFeatureEnabled("update-check") || plugin.isFeatureEnabled("auto-update")) {
            try {
                String pluginVersion = plugin.getDescription().getVersion();
                String updateUrl = "https://github.com/LengMC/Lengbanlist/releases/latest";
                String latestVersion = GitHubUpdateChecker.getLatestReleaseVersion();

                if (GitHubUpdateChecker.isUpdateAvailable(pluginVersion)) {
                    String prefix = plugin.prefix();
                    TextComponent message = new TextComponent(prefix + " §a喵喵发现有新版本可用，当前版本：§e" + pluginVersion + "§a，最新版本：§e" + latestVersion + "§a 请前往: §b" + updateUrl);
                    TextComponent clickableLink = new TextComponent("§f【§b点击前往喵~§f】");
                    clickableLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, updateUrl));
                    clickableLink.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击打开更新页面喵~").create()));

                    player.spigot().sendMessage(message, clickableLink);
                } else {
                    player.sendMessage(plugin.prefix() + " §a喵喵发现现在是最新版本！");
                }
            } catch (Exception e) {
                player.sendMessage(plugin.prefix() + "§c无法获取最新版本信息，请检查网络连接！");
            }
        }

        // 显示未处理的举报数量（受admin功能开关控制）
        if (plugin.isFeatureEnabled("admin")) {
            ReportManager reportManager = plugin.getReportManager();
            int pendingReports = reportManager.getPendingReportCount();

            String greeting = getGreetingMessage();
            TextComponent adminMessage = new TextComponent("——————————\n" +
                    plugin.prefix() + "\n" +
                    "尊敬的Admin：" + player.getName() + "\n" +
                    greeting + "，来杯咖啡，开始今天的工作吧\n" +
                    "您有" + pendingReports + "个举报没处理\n" +
                    "——————————\n");

            TextComponent clickableAdminLink = new TextComponent("§a【点击前往】");
            clickableAdminLink.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lban admin"));
            clickableAdminLink.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击前往举报管理界面").create()));

            player.spigot().sendMessage(adminMessage, clickableAdminLink);
        }
    }
}
private String getGreetingMessage() {
    Calendar calendar = Calendar.getInstance();
    int hour = calendar.get(Calendar.HOUR_OF_DAY);

    if (hour >= 5 && hour < 12) {
        return "早上好";
    } else if (hour >= 12 && hour < 18) {
        return "下午好";
    } else if (hour >= 18 && hour < 22) {
        return "晚上好";
    } else {
        return "深夜好";
    }
}
}