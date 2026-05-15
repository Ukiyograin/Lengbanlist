package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class Ayaka implements Model {
    @Override
    public String getName() {
        return "Ayaka";
    }

@Override
public void showHelp(CommandSender sender) {
    Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
    Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 绫华风格 §b║");
    Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
    Utils.sendMessage(sender, "§6§l◆ 处罚管理");
    Utils.sendMessage(sender, "§2✦ §b/lban add <玩家名> <天数> <原因> §7- §3添加封禁，维护秩序不容破坏。");
    Utils.sendMessage(sender, "§7  = §b/ban");
    Utils.sendMessage(sender, "§2✦ §b/lban remove <玩家名> §7- §3移除封禁，宽恕是美德。");
    Utils.sendMessage(sender, "§7  = §b/unban");
    Utils.sendMessage(sender, "§2✦ §b/ban-ip <IP地址> <天数> <原因> §7- §3封禁 IP 地址，维护秩序。");
    Utils.sendMessage(sender, "§2✦ §b/lban mute <玩家名> <原因> §7- §3禁言玩家，维护秩序。");
    Utils.sendMessage(sender, "§7  = §b/mute");
    Utils.sendMessage(sender, "§2✦ §b/lban unmute <玩家名> §7- §3解除禁言，给予机会重新开始。");
    Utils.sendMessage(sender, "§7  = §b/unmute");
    Utils.sendMessage(sender, "§2✦ §b/lban warn <玩家名> <原因> §7- §3警告玩家，三次警告自动封禁。");
    Utils.sendMessage(sender, "§7  = §b/warn");
    Utils.sendMessage(sender, "§2✦ §b/lban unwarn <玩家名> §7- §3移除玩家警告");
    Utils.sendMessage(sender, "§7  = §b/unwarn");
    Utils.sendMessage(sender, "§2✦ §b/kick <玩家名> <原因> §7- §3踢出不守规矩的玩家！");
    Utils.sendMessage(sender, "§2✦ §b/setban <玩家名/IP> <时间/forever/auto> <原因> §7- §3修改封禁时间，优雅而公正。");
    Utils.sendMessage(sender, "§6§l◆ 查询信息");
    Utils.sendMessage(sender, "§2✦ §b/lban check <玩家名/IP> §7- §3检查封禁状态，优雅而公正。");
    Utils.sendMessage(sender, "§2✦ §b/lban history <玩家名> §7- §3查看处罚记录，请过目。");
    Utils.sendMessage(sender, "§7  = §b/history");
    Utils.sendMessage(sender, "§2✦ §b/report <玩家名> <原因> §7- §3优雅地举报不守规矩的行为。");
    Utils.sendMessage(sender, "§2✦ §b/lban getip <玩家名> §7- §3查询玩家 IP 地址");
    Utils.sendMessage(sender, "§6§l◆ 杂项");
    Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看封禁名单，优雅而公正。");
    Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表");
    Utils.sendMessage(sender, "§7  = §b/listmute");
    Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数，让大家知晓规则。");
    Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，一切尽在掌控。");
    Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面");
    Utils.sendMessage(sender, "§2✦ §b/lban model <模型名称> §7- §3切换模型，体验不同的风格。");
    Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，确保一切完美无缺。");
    Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息");
    Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
    Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 绫华 Ayaka");
}

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d绫华的驱逐通知  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ 你被绫华踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d下次请遵守规则哦~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 绫华说：§a" + playerName + " §e已被踢出！\n" +
               "§b原因: §f" + reason + "\n" +
               "§b维护秩序，不容破坏！§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b绫华说：§a自动广播已经 " + (enabled ? "开启。" : "关闭。") + " 一切尽在掌控之中。";
    }

    @Override
    public String reloadConfig() {
        return "§b绫华说：§a配置重新加载完成。确保一切完美无缺。";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b绫华说：§a" + player + " 已被封禁 " + days + " 天，原因是：" + reason + "。维护秩序，不容破坏。";
    }

    @Override
    public String removeBan(String player) {
        return "§b绫华说：§a" + player + " 已从封禁名单中移除。宽恕是美德。";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b绫华说：§a" + player + " 已被禁言，原因是：" + reason + "。维护秩序，不容破坏。";
    }

    @Override
    public String removeMute(String player) {
        return "§b绫华说：§a" + player + " 的禁言已解除。给予机会，重新开始。";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b绫华说：§aIP " + ip + " 已被封禁 " + days + " 天，原因是：" + reason + "。维护秩序，不容破坏。";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b绫华说：§aIP " + ip + " 的封禁已解除。给予机会，重新开始。";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b绫华说：§a玩家 " + player + " 已被警告，原因是：" + reason + "。警告三次将被自动封禁。";
    }

    @Override
    public String removeWarn(String player) {
        return "§b绫华说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b绫华说：§a" + player + " 殿下的记录如白雪般纯净，绫华深感欣慰。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b绫华说：§a" + player + " 殿下的处罚记录如下，请过目：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b绫华说：§7望阁下以此为戒，优雅地遵守规则，方显贵族风范。");
        return sb.toString().trim();
    }
}