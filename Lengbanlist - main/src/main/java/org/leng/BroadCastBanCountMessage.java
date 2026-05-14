package org.leng;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;

public class BroadCastBanCountMessage extends BukkitRunnable {
    @Override
    public void run() {
        if (!Lengbanlist.getInstance().isEnabled()) {
            this.cancel();
            return;
        }
        
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String defaultMessage = Lengbanlist.getInstance().getBroadcastFC().getString("default-message");

                int banCount = Lengbanlist.getInstance().getBanManager().getBanList().size();
                int banIpCount = Lengbanlist.getInstance().getBanManager().getBanIpList().size();

                int totalBans = banCount + banIpCount;

                String replacedMessage = defaultMessage
                        .replace("%s", String.valueOf(banCount)) 
                        .replace("%i", String.valueOf(banIpCount)) 
                        .replace("%t", String.valueOf(totalBans)); 

                TextComponent mainMessage = new TextComponent(ChatColor.translateAlternateColorCodes('&', replacedMessage));
                mainMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("§a绳§b之§c于§d法§e！").create())); 

                // 创建点击组件
                TextComponent clickableComponent = new TextComponent("§f【§b点§c击§d查§e看§f】"); 
                clickableComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lban list"));
                clickableComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("§a看看封禁列表§bawa").create())); 

                // 发送消息
                player.spigot().sendMessage(mainMessage, clickableComponent);
            }
        } catch (Exception e) {
            Lengbanlist.getInstance().getLogger().warning("广播任务执行出错: " + e.getMessage());
        }
    }
}