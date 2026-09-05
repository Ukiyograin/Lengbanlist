package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.leng.Lengbanlist;
import org.leng.object.BanIpEntry;
import org.leng.utils.IpMatcher;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

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
            Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式：/check <玩家名/IP>");
            return true;
        }

        String target = args[0];
        if (target.contains(".")) {

            checkIpInfo(sender, target);
        } else {

            checkPlayerInfo(sender, target);
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }

    private void checkPlayerInfo(CommandSender sender, String playerName) {
        Player online = plugin.getServer().getPlayerExact(playerName);
        OfflinePlayer player;
        if (online != null) {
            player = online;
        } else {
            player = org.leng.utils.PlayerProfileHelper.lookupSync(playerName);
            if (player == null) {
                Utils.sendMessage(sender, plugin.prefix() + "§c未找到玩家：" + playerName + "（离线查询不可用，请在 Folia 服务端查询在线玩家）");
                return;
            }
        }

        String uuid = player.getUniqueId().toString();
        long lastLogin = player.getLastPlayed();
        String lastLoginTime = lastLogin == 0 ? "从未登录" : TimeUtils.timestampToReadable(lastLogin);
        boolean isMuted = plugin.getMuteManager().isPlayerMuted(playerName);
        boolean isBanned = plugin.getBanManager().isPlayerBanned(playerName);
        boolean isOp = player.isOp();

        String specialTag = "a5dc2127-d472-4c87-90b6-0b9fff386236".equals(uuid) ? "§c[DEV] " : "";

        Utils.sendMessage(sender, plugin.prefix() + "§a玩家信息：");
        Utils.sendMessage(sender, plugin.prefix() + "§b玩家名: " + specialTag + playerName);
        Utils.sendMessage(sender, plugin.prefix() + "§bUUID: " + uuid);
        Utils.sendMessage(sender, plugin.prefix() + "§b最后登录时间: " + lastLoginTime);
        Utils.sendMessage(sender, plugin.prefix() + "§b是否禁言: " + (isMuted ? "是" : "否"));
        Utils.sendMessage(sender, plugin.prefix() + "§b是否封禁: " + (isBanned ? "是" : "否"));
        Utils.sendMessage(sender, plugin.prefix() + "§b是否是OP: " + (isOp ? "是" : "否"));

        if (plugin.isFeatureEnabled("ip-association")) {
            Utils.sendMessage(sender, "§7--- §cIP关联信息 §7---");
            List<String[]> ipHistory = plugin.getIpAssociationManager().getPlayerIps(playerName);
            if (ipHistory.isEmpty()) {
                Utils.sendMessage(sender, plugin.prefix() + "§e暂无 IP 记录");
            } else {
                for (String[] record : ipHistory) {
                    String ip = record[0];
                    String firstSeen = TimeUtils.timestampToReadable(Long.parseLong(record[1]));
                    List<String> associatedPlayers = plugin.getDatabaseManager().getPlayersByIpFromHistory(ip);
                    StringBuilder line = new StringBuilder();
                    line.append(" §7- §f").append(ip).append(" §7(首次: ").append(firstSeen).append(")");
                    if (associatedPlayers.size() > 1) {
                        line.append(" §c关联: §f");
                        for (String ap : associatedPlayers) {
                            if (!ap.equalsIgnoreCase(playerName)) {
                                line.append(ap).append(" ");
                            }
                        }
                    }
                    Utils.sendMessage(sender, plugin.prefix() + line.toString());
                }
            }
        }

        if ("a5dc2127-d472-4c87-90b6-0b9fff386236".equals(uuid)) {
            showSponsorInfo(sender);
        }

    }

    private void checkIpInfo(CommandSender sender, String ip) {
        String normalized = IpMatcher.normalizeIpOrCidr(ip);
        if (normalized != null) ip = normalized;

        boolean isBanned = plugin.getBanManager().isIpBanned(ip);
        if (IpMatcher.isIpv4(ip) && plugin.getBanManager().isIpBannedByCidr(ip)) {
            isBanned = true;
        }
        List<String> associatedPlayers = plugin.getDatabaseManager().getPlayersByIpFromHistory(ip);

        Utils.sendMessage(sender, plugin.prefix() + "§aIP信息：");
        Utils.sendMessage(sender, plugin.prefix() + "§bIP: " + ip);
        Utils.sendMessage(sender, plugin.prefix() + "§b是否封禁: " + (isBanned ? "是" : "否"));

        if (IpMatcher.isIpv4(ip)) {
            for (BanIpEntry entry : plugin.getBanManager().getBanIpList()) {
                if (IpMatcher.isCidr(entry.getIp()) && IpMatcher.cidrMatches(ip, entry.getIp())) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c命中网段封禁: §f" + entry.getIp() + " §7原因: " + entry.getReason() + " §7解封时间: " + TimeUtils.timestampToReadable(entry.getTime()));
                }
            }
        }

        Utils.sendMessage(sender, plugin.prefix() + "§b关联玩家: " + (associatedPlayers.isEmpty() ? "无" : String.join(", ", associatedPlayers)));
    }

    private void showSponsorInfo(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;


            TextComponent sponsorButton = new TextComponent(plugin.prefix() + "§6支持作者，让他更有动力开发插件！§b[§a点击赞助§b]");
            sponsorButton.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§a点击支持作者§bawa").create()));
            sponsorButton.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://afdian.com/a/lengmc"));


            player.spigot().sendMessage(sponsorButton);


            Utils.sendMessage(player, plugin.prefix() + "§b请我喝杯奶茶：￥20.00 CNY/月 - 加入感谢名单，优先反馈");
            Utils.sendMessage(player, plugin.prefix() + "§bBETA权限组：￥50.00 CNY/月 - 解锁高级功能，优先支持");
            Utils.sendMessage(player, plugin.prefix() + "§b一次性打赏：任意金额 - 表达你的支持");
        } else {

            Utils.sendMessage(sender, plugin.prefix() + "§6支持作者，让他更有动力开发插件！§b[§a点击赞助§b] §c(https://afdian.com/a/lengmc)");
            Utils.sendMessage(sender, plugin.prefix() + "§b请我喝杯奶茶：￥20.00 CNY/月 - 加入感谢名单，优先反馈");
            Utils.sendMessage(sender, plugin.prefix() + "§bBETA权限组：￥50.00 CNY/月 - 解锁高级功能，优先支持");
            Utils.sendMessage(sender, plugin.prefix() + "§b一次性打赏：任意金额 - 表达你的支持");
        }
    }
}
