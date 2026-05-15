package org.leng.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.leng.Lengbanlist;
import org.leng.commands.LengbanlistCommand;
import org.leng.commands.WarnCommand;
import org.leng.object.MuteEntry;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 聊天事件监听，处理聊天向导、禁言检查、违禁词过滤、管理员审查。 */
public class ChatListener implements Listener {
    private final Lengbanlist plugin;
    private final Map<String, Integer> badWordCount = new HashMap<>();

    public ChatListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!plugin.isFeatureEnabled("chat-filter")) {
            return;
        }

        if (player.hasMetadata("lengbanlist-action")) {
            event.setCancelled(true);
            String wizardMessage = event.getMessage();
            SchedulerUtils.runTask(plugin, () -> {
                org.bukkit.command.CommandExecutor executor = plugin.getCommand("lban").getExecutor();
                if (executor instanceof LengbanlistCommand) {
                    ((LengbanlistCommand) executor).handleChatWizard(player, wizardMessage);
                }
            });
            return;
        }

        String message = event.getMessage();

        if (plugin.getMuteManager().isPlayerMuted(player.getName())) {
            event.setCancelled(true);
            player.sendMessage(plugin.prefix() + "§c你不准说话喵！");
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
            badWordCount.put(player.getName(), badWordCount.getOrDefault(player.getName(), 0) + 1);

            if (badWordCount.get(player.getName()) >= muteThreshold) {
                String reason = "多次使用违禁词";
                plugin.getMuteManager().mutePlayer(new MuteEntry(player.getName(), "System", System.currentTimeMillis(), reason));
                player.sendMessage(plugin.prefix() + "§c你因多次使用违禁词被自动禁言！");
                badWordCount.remove(player.getName());
            }

            player.sendMessage(plugin.prefix() + "§c警告：你的消息中包含违禁词，已被替换为「喵」。");
            SchedulerUtils.runTask(plugin, () -> {
                WarnCommand warnCommand = new WarnCommand(plugin);
                warnCommand.onCommand(player, null, "warn", new String[]{player.getName(), "使用违禁词"});
            });
        }

        if (!containsBadWord && message.matches(".*\\b(\\w*喵\\w*){2,}.*")) {
            String adminMessage = plugin.prefix() + "请检测该句是否违规：" + message + " 【正常】【违规】";
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.isOp()) {
                    admin.spigot().sendMessage(
                            Utils.clickableText("【正常】", "/allowmsg " + player.getName()),
                            Utils.clickableText("【违规】", "/warnmsg " + player.getName())
                    );
                }
            }
            event.setCancelled(true);
            return;
        }

        event.setMessage(message);
    }
}
