package org.leng.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.leng.Lengbanlist;
import org.leng.commands.LengbanlistCommand;
import org.leng.object.MuteEntry;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ChatListener implements Listener {
    private final Lengbanlist plugin;
    private final Map<String, Integer> badWordCount = new ConcurrentHashMap<>();
    private static final java.util.regex.Pattern MEOW_REPEAT = java.util.regex.Pattern.compile(".*\\b(\\w*喵\\w*){2,}.*");

    public ChatListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (player.hasMetadata("lengbanlist-action")) {
            event.setCancelled(true);
            String wizardMessage = event.getMessage();
            SchedulerUtils.runTask(plugin, player, () -> {
                org.bukkit.command.CommandExecutor executor = plugin.getCommand("lban").getExecutor();
                if (executor instanceof LengbanlistCommand) {
                    ((LengbanlistCommand) executor).handleChatWizard(player, wizardMessage);
                }
            });
            return;
        }

        String message = event.getMessage();

        if (plugin.getMuteManager().isPlayerMuted(player)) {
            event.setCancelled(true);
            player.sendMessage(plugin.prefix() + "§c你不准说话喵！");
            return;
        }

        if (!plugin.isFeatureEnabled("chat-filter")) {
            return;
        }

        List<String> badWords = plugin.getChatConfig().getStringList("bad-words");
        int muteThreshold = plugin.getChatConfig().getInt("mute-threshold", 3);

        boolean containsBadWord = false;
        for (String badWord : badWords) {
            if (message.contains(badWord)) {
                containsBadWord = true;
                String replacement = "喵".repeat(badWord.length());
                message = message.replace(badWord, replacement);
            }
        }

        if (containsBadWord) {
            int violations = badWordCount.merge(player.getName(), 1, Integer::sum);
            if (violations >= muteThreshold) {
                long muteDurationMillis;
                switch (violations) {
                    case 3: muteDurationMillis = TimeUtils.hoursToMillis(1); break;
                    case 4: muteDurationMillis = TimeUtils.hoursToMillis(12); break;
                    case 5: muteDurationMillis = TimeUtils.daysToMillis(1); break;
                    default: muteDurationMillis = TimeUtils.daysToMillis(7); break;
                }
                String reason = "多次使用违禁词（第 " + violations + " 次触发）";
                plugin.getMuteManager().mutePlayer(new MuteEntry(player.getName(), "System", TimeUtils.calculateEndTime(muteDurationMillis), reason));
                player.sendMessage(plugin.prefix() + "§c你因多次使用违禁词被自动禁言 " + TimeUtils.formatDuration(muteDurationMillis) + "！");
                badWordCount.remove(player.getName());
            }

            player.sendMessage(plugin.prefix() + "§c警告：你的消息中包含违禁词，已被替换为「喵」。");
            SchedulerUtils.runTask(plugin, player, () ->
                    plugin.getWarnManager().warnPlayer(player.getName(), "System", "使用违禁词")
            );
        }

        if (!containsBadWord && MEOW_REPEAT.matcher(message).find()) {
            SchedulerUtils.runTask(plugin, () -> {
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.isOp()) {
                        org.leng.utils.Utils.sendMessage(admin,
                                Utils.clickableText("【正常】", "/allowmsg " + player.getName()),
                                Utils.clickableText("【违规】", "/warnmsg " + player.getName())
                        );
                    }
                }
            });
            event.setCancelled(true);
            return;
        }

        event.setMessage(message);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        badWordCount.remove(event.getPlayer().getName());
    }
}
