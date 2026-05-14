package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.leng.Lengbanlist;
import org.leng.utils.SaveIP;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;
import org.leng.object.WarnEntry;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.List;

public class CheckCommand extends Command implements CommandExecutor {
    private final Lengbanlist plugin;

    public CheckCommand(Lengbanlist plugin) {
        super("check");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!plugin.isFeatureEnabled("check")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        if (!sender.hasPermission("lengbanlist.check")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }

        if (args.length < 1) {
            Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式：/check <玩家名/IP>");
            return true;
        }

        String target = args[0];
        if (target.contains(".")) {
            // 查询 IP 信息
            checkIpInfo(sender, target);
        } else {
            // 查询玩家信息
            checkPlayerInfo(sender, target);
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }

    private void checkPlayerInfo(CommandSender sender, String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        if (!player.hasPlayedBefore() && !player.isOnline()) {
            Utils.sendMessage(sender, plugin.prefix() + "§c未找到玩家：" + playerName);
            return;
        }

        String uuid = player.getUniqueId().toString();
        long lastLogin = player.getLastPlayed();
        String lastLoginTime = lastLogin == 0 ? "从未登录" : TimeUtils.timestampToReadable(lastLogin);
        boolean isMuted = plugin.getMuteManager().isPlayerMuted(playerName);
        boolean isBanned = plugin.getBanManager().isPlayerBanned(playerName);
        boolean isOp = player.isOp();
        List<WarnEntry> warnings = plugin.getWarnManager().getActiveWarnings(playerName);

        // 特殊处理DEV作者（通过UUID判定）
        String specialTag = "a5dc2127-d472-4c87-90b6-0b9fff386236".equals(uuid) ? "§c[DEV] " : "";

        Utils.sendMessage(sender, plugin.prefix() + "§a玩家信息：");
        Utils.sendMessage(sender, plugin.prefix() + "§b玩家名: " + specialTag + playerName);
        Utils.sendMessage(sender, plugin.prefix() + "§bUUID: " + uuid);
        Utils.sendMessage(sender, plugin.prefix() + "§b最后登录时间: " + lastLoginTime);
        Utils.sendMessage(sender, plugin.prefix() + "§b是否禁言: " + (isMuted ? "是" : "否"));
        Utils.sendMessage(sender, plugin.prefix() + "§b是否封禁: " + (isBanned ? "是" : "否"));
        Utils.sendMessage(sender, plugin.prefix() + "§b是否是OP: " + (isOp ? "是" : "否"));

        // 如果是DEV作者，显示赞助信息
        if ("a5dc2127-d472-4c87-90b6-0b9fff386236".equals(uuid)) {
            showSponsorInfo(sender);
        }

        if (warnings.isEmpty()) {
            Utils.sendMessage(sender, plugin.prefix() + "§b警告: 无警告");
        } else {
            Utils.sendMessage(sender, plugin.prefix() + "§b警告记录:");
            for (WarnEntry warn : warnings) {
                Utils.sendMessage(sender, plugin.prefix() + "§7- 时间: " + TimeUtils.timestampToReadable(warn.getTime()) +
                        " §7判断者: " + warn.getStaff() +
                        " §7原因: " + warn.getReason());
            }
        }
    }

    private void checkIpInfo(CommandSender sender, String ip) {
        boolean isBanned = plugin.getBanManager().isIpBanned(ip);
        List<String> associatedPlayers = getPlayersAssociatedWithIp(ip);

        Utils.sendMessage(sender, plugin.prefix() + "§aIP信息：");
        Utils.sendMessage(sender, plugin.prefix() + "§bIP: " + ip);
        Utils.sendMessage(sender, plugin.prefix() + "§b是否封禁: " + (isBanned ? "是" : "否"));
        Utils.sendMessage(sender, plugin.prefix() + "§b关联玩家: " + (associatedPlayers.isEmpty() ? "无" : String.join(", ", associatedPlayers)));
    }

    private List<String> getPlayersAssociatedWithIp(String ip) {
        return SaveIP.getPlayersByIp(ip);
    }

    private void showSponsorInfo(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            // 创建可点击的赞助按钮
            TextComponent sponsorButton = new TextComponent(plugin.prefix() + "§6支持作者，让他更有动力开发插件！§b[§a点击赞助§b]");
            sponsorButton.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§a点击支持作者§bawa").create()));
            sponsorButton.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://afdian.com/a/lengmc"));

            // 发送赞助按钮
            player.spigot().sendMessage(sponsorButton);

            // 发送赞助方案描述信息
            Utils.sendMessage(player, plugin.prefix() + "§b请我喝杯奶茶：￥20.00 CNY/月 - 加入感谢名单，优先反馈");
            Utils.sendMessage(player, plugin.prefix() + "§bBETA权限组：￥50.00 CNY/月 - 解锁高级功能，优先支持");
            Utils.sendMessage(player, plugin.prefix() + "§b一次性打赏：任意金额 - 表达你的支持");
        } else {
            // 如果不是玩家（例如控制台），发送普通消息
            Utils.sendMessage(sender, plugin.prefix() + "§6支持作者，让他更有动力开发插件！§b[§a点击赞助§b] §c(https://afdian.com/a/lengmc)");
            Utils.sendMessage(sender, plugin.prefix() + "§b请我喝杯奶茶：￥20.00 CNY/月 - 加入感谢名单，优先反馈");
            Utils.sendMessage(sender, plugin.prefix() + "§bBETA权限组：￥50.00 CNY/月 - 解锁高级功能，优先支持");
            Utils.sendMessage(sender, plugin.prefix() + "§b一次性打赏：任意金额 - 表达你的支持");
        }
    }
}
