package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class Furina implements Model {
    @Override
    public String getName() {
        return "Furina";
    }

@Override
public void showHelp(CommandSender sender) {
    Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
    Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 芙宁娜风格 §b║");
    Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
    Utils.sendMessage(sender, "§6§l◆ 处罚管理");
    Utils.sendMessage(sender, "§2✦ §b/lban add <玩家名> <天数> <原因> §7- §3加入黑名单！");
    Utils.sendMessage(sender, "§7  = §b/ban");
    Utils.sendMessage(sender, "§2✦ §b/lban remove <玩家名> §7- §3从黑名单中移除");
    Utils.sendMessage(sender, "§7  = §b/unban");
    Utils.sendMessage(sender, "§2✦ §b/ban-ip <IP地址> <天数> <原因> §7- §3封禁 IP 地址，别再划水啦！");
    Utils.sendMessage(sender, "§2✦ §b/lban mute <玩家名> <原因> §7- §3禁言玩家，让他们安静一会儿！");
    Utils.sendMessage(sender, "§7  = §b/mute");
    Utils.sendMessage(sender, "§2✦ §b/lban unmute <玩家名> §7- §3解除禁言，可以继续说话啦！");
    Utils.sendMessage(sender, "§7  = §b/unmute");
    Utils.sendMessage(sender, "§2✦ §b/lban warn <玩家名> <原因> §7- §3警告玩家，三次警告自动封禁！");
    Utils.sendMessage(sender, "§7  = §b/warn");
    Utils.sendMessage(sender, "§2✦ §b/lban unwarn <玩家名> §7- §3移除玩家警告");
    Utils.sendMessage(sender, "§7  = §b/unwarn");
    Utils.sendMessage(sender, "§2✦ §b/kick <玩家名> <原因> §7- §3踢出划水的家伙！");
    Utils.sendMessage(sender, "§2✦ §b/setban <玩家名/IP> <时间/forever/auto> <原因> §7- §3修改封禁时间");
    Utils.sendMessage(sender, "§6§l◆ 查询信息");
    Utils.sendMessage(sender, "§2✦ §b/lban check <玩家名/IP> §7- §3检查封禁状态");
    Utils.sendMessage(sender, "§2✦ §b/lban history <玩家名> §7- §3查阅审判记录，本水神亲自过目！");
    Utils.sendMessage(sender, "§7  = §b/history");
    Utils.sendMessage(sender, "§2✦ §b/report <玩家名> <原因> §7- §3向本水神举报违规者！");
    Utils.sendMessage(sender, "§2✦ §b/lban getip <玩家名> §7- §3查询玩家 IP 地址");
    Utils.sendMessage(sender, "§6§l◆ 杂项");
    Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看黑名单");
    Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表");
    Utils.sendMessage(sender, "§7  = §b/listmute");
    Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数");
    Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，水酱们要注意啦！");
    Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面");
    Utils.sendMessage(sender, "§2✦ §b/lban model <模型名称> §7- §3切换模型");
    Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，水神的大脑又清晰啦！");
    Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息");
    Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
    Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 芙宁娜 Furina");
}

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d芙宁娜的驱逐通知  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ 你被芙宁娜踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d下次请遵守规则哦~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 芙宁娜说：§a" + playerName + " §e已被踢出！\n" +
               "§b原因: §f" + reason + "\n" +
               "§b维护秩序，不容破坏！§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b芙宁娜说：§a自动广播已经 " + (enabled ? "开启啦！" : "关闭啦！") + " 水酱们要注意啦！";
    }

    @Override
    public String reloadConfig() {
        return "§b芙宁娜说：§a配置重新加载完成！水神的大脑又清晰啦！";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b芙宁娜说：§a" + player + " 已被加入黑名单！封禁 " + days + " 天，原因是：" + reason + "。划水可不是好习惯哦！";
    }

    @Override
    public String removeBan(String player) {
        return "§b芙宁娜说：§a" + player + " 已从黑名单中移除啦！知错能改，善莫大焉！";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b芙宁娜说：§a" + player + " 已被禁言，原因是：" + reason + "！让他们安静一会儿吧！";
    }

    @Override
    public String removeMute(String player) {
        return "§b芙宁娜说：§a" + player + " 的禁言已解除，可以继续说话啦！";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b芙宁娜说：§aIP " + ip + " 已被封禁 " + days + " 天，原因是：" + reason + "。别再划水啦！";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b芙宁娜说：§aIP " + ip + " 的封禁已解除，给他们一个机会！";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b芙宁娜说：§a玩家 " + player + " 已被警告，原因是：" + reason + "！警告三次将被自动封禁！";
    }

    @Override
    public String removeWarn(String player) {
        return "§b芙宁娜说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b芙宁娜说：§a本水神查阅了 " + player + " 的记录，此人品行端正，毫无污点！值得嘉奖~";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b芙宁娜说：§a咳咳，本水神在此宣判——以下是 ").append(player).append(" 的审判记录：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b芙宁娜说：§7审判结束！希望此人能改过自新，否则下次审判就不止于此了~");
        return sb.toString().trim();
    }
}