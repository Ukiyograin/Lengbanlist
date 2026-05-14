package org.leng.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.leng.Lengbanlist;
import org.leng.manager.MuteManager;
import org.leng.manager.WarnManager;
import org.leng.commands.LengbanlistCommand;
import org.leng.commands.WarnCommand;
import org.leng.object.MuteEntry;
import org.leng.utils.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatListener implements Listener {
    private final Lengbanlist plugin;

    // 用于记录玩家违禁词使用次数
    private final Map<String, Integer> badWordCount = new HashMap<>();

    public ChatListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // 检查是否在聊天向导模式
        if (player.hasMetadata("lengbanlist-action")) {
            event.setCancelled(true);
            final String wizardMessage = event.getMessage();
            new BukkitRunnable() {
                @Override
                public void run() {
                    org.bukkit.command.CommandExecutor executor = plugin.getCommand("lban").getExecutor();
                    if (executor instanceof LengbanlistCommand) {
                        ((LengbanlistCommand) executor).handleChatWizard(player, wizardMessage);
                    }
                }
            }.runTask(plugin);
            return;
        }

        String message = event.getMessage();

        // 检查是否被禁言
        if (plugin.getMuteManager().isPlayerMuted(player.getName())) {
            event.setCancelled(true); // 取消发言事件
            player.sendMessage(plugin.prefix() + "§c你不准说话喵！");
            return;
        }

        // 获取违禁词列表和禁言阈值
        List<String> badWords = plugin.getChatConfig().getStringList("bad-words");
        int muteThreshold = plugin.getChatConfig().getInt("mute-threshold", 3);

        // 检查违禁词并进行喵化处理
        boolean containsBadWord = false;
        for (String badWord : badWords) {
            if (message.contains(badWord)) {
                containsBadWord = true;
                // 根据字数替换为“喵”
                String replacement = "喵".repeat(badWord.length());
                message = message.replace(badWord, replacement);
            }
        }

        if (containsBadWord) {
            // 增加违禁词使用次数
            badWordCount.put(player.getName(), badWordCount.getOrDefault(player.getName(), 0) + 1);

            // 检查是否达到禁言阈值
            if (badWordCount.get(player.getName()) >= muteThreshold) {
                // 自动禁言
                String reason = "多次使用违禁词";
                plugin.getMuteManager().mutePlayer(new MuteEntry(player.getName(), "System", System.currentTimeMillis(), reason));
                player.sendMessage(plugin.prefix() + "§c你因多次使用违禁词被自动禁言！");
                badWordCount.remove(player.getName()); // 重置计数
            }

            // 向玩家发送警告
            player.sendMessage(plugin.prefix() + "§c警告：你的消息中包含违禁词，已被替换为“喵”。");
            // 调用 WarnCommand 来警告玩家一次
            new BukkitRunnable() {
                @Override
                public void run() {
                    WarnCommand warnCommand = new WarnCommand(plugin);
                    warnCommand.onCommand(player, null, "warn", new String[]{player.getName(), "使用违禁词"});
                }
            }.runTask(plugin);
        }

        // 检测疑似多个类似的词语
        if (!containsBadWord && message.matches(".*\\b(\\w*喵\\w*){2,}.*")) {
            // 向在线管理员发送检测请求
            String adminMessage = plugin.prefix() + "请检测该句是否违规：" + message + " 【正常】【违规】";
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.isOp()) {
                    admin.spigot().sendMessage(
                            Utils.clickableText("【正常】", "/allowmsg " + player.getName()),
                            Utils.clickableText("【违规】", "/warnmsg " + player.getName())
                    );
                }
            }
            event.setCancelled(true); // 暂时取消消息发送
            return;
        }

        // 修改消息内容
        event.setMessage(message);
    }
}