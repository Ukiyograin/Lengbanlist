package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class Herta implements Model {
    @Override
    public String getName() {
        return "Herta";
    }

@Override
public void showHelp(CommandSender sender) {
    Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
    Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 希儿风格 §b║");
    Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
    Utils.sendMessage(sender, "§6§l◆ 处罚管理");
    Utils.sendMessage(sender, "§2✦ §b/lban add <玩家名> <天数> <原因> §7- §3添加封禁，正义不容挑战！");
    Utils.sendMessage(sender, "§7  = §b/ban");
    Utils.sendMessage(sender, "§2✦ §b/lban remove <玩家名> §7- §3移除封禁，给予机会重新开始。");
    Utils.sendMessage(sender, "§7  = §b/unban");
    Utils.sendMessage(sender, "§2✦ §b/ban-ip <IP地址> <天数> <原因> §7- §3封禁 IP 地址，维护正义。");
    Utils.sendMessage(sender, "§2✦ §b/lban mute <玩家名> <原因> §7- §3禁言玩家，维护正义。");
    Utils.sendMessage(sender, "§7  = §b/mute");
    Utils.sendMessage(sender, "§2✦ §b/lban unmute <玩家名> §7- §3解除禁言，给予机会。");
    Utils.sendMessage(sender, "§7  = §b/unmute");
    Utils.sendMessage(sender, "§2✦ §b/lban warn <玩家名> <原因> §7- §3警告玩家，三次警告自动封禁！");
    Utils.sendMessage(sender, "§7  = §b/warn");
    Utils.sendMessage(sender, "§2✦ §b/lban unwarn <玩家名> §7- §3移除玩家警告");
    Utils.sendMessage(sender, "§7  = §b/unwarn");
    Utils.sendMessage(sender, "§2✦ §b/kick <玩家名> <原因> §7- §3踢出不听话的家伙！");
    Utils.sendMessage(sender, "§2✦ §b/setban <玩家名/IP> <时间/forever/auto> <原因> §7- §3修改封禁时间，调皮捣蛋可是要额外收费的~");
    Utils.sendMessage(sender, "§6§l◆ 查询信息");
    Utils.sendMessage(sender, "§2✦ §b/lban check <玩家名/IP> §7- §3检查封禁状态，希儿的正义不容挑战！");
    Utils.sendMessage(sender, "§2✦ §b/lban history <玩家名> §7- §3翻翻黑历史，让希儿瞧瞧~");
    Utils.sendMessage(sender, "§7  = §b/history");
    Utils.sendMessage(sender, "§2✦ §b/report <玩家名> <原因> §7- §3维护正义，举报违规者！");
    Utils.sendMessage(sender, "§2✦ §b/lban getip <玩家名> §7- §3查询玩家 IP 地址，找出违规者。");
    Utils.sendMessage(sender, "§6§l◆ 杂项");
    Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看封禁名单，希儿的正义不容挑战！");
    Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表");
    Utils.sendMessage(sender, "§7  = §b/listmute");
    Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数，让违规者无处可逃！");
    Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，掌控一切！");
    Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面");
    Utils.sendMessage(sender, "§2✦ §b/lban model <模型名称> §7- §3切换模型，体验不同的风格。");
    Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，确保一切正常运行。");
    Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息");
    Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
    Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 希儿 Herta");
}

    @Override
    public String getKickMessage(String reason) {
        return "§5╔══════════════════════════╗\n" +
               "§5║   §d希儿的驱逐通知  §5║\n" +
               "§5╠══════════════════════════╣\n" +
               "§d☠️ 你被希儿踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d想回来记得找希儿哦~\n" +
               "§5╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 希儿说：§a" + playerName + " §e已被踢出！\n" +
               "§5原因: §f" + reason + "\n" +
               "§b调皮捣蛋可是要额外收费的~ §5(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b希儿说：§a自动广播已经 " + (enabled ? "开启！" : "关闭！") + " 正义需要维护！";
    }

    @Override
    public String reloadConfig() {
        return "§b希儿说：§a配置重新加载完成！一切正常运行。";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b希儿说：§a玩家 " + player + " 已被封禁 " + days + " 天，原因是：" + reason + "。正义不容挑战！";
    }

    @Override
    public String removeBan(String player) {
        return "§b希儿说：§a玩家 " + player + " 已从封禁名单中移除。给予机会，重新开始。";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b希儿说：§a玩家 " + player + " 已被禁言，原因是：" + reason + "。正义不容挑战！";
    }

    @Override
    public String removeMute(String player) {
        return "§b希儿说：§a玩家 " + player + " 的禁言已解除，可以继续说话了。";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b希儿说：§aIP " + ip + " 已被封禁 " + days + " 天，原因是：" + reason + "。正义不容挑战！";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b希儿说：§aIP " + ip + " 的封禁已解除。给予机会，重新开始。";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b希儿说：§a玩家 " + player + " 已被警告，原因是：" + reason + "。警告三次将被自动封禁！";
    }

    @Override
    public String removeWarn(String player) {
        return "§b希儿说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b希儿说：§a" + player + " 的档案干干净净~看来是个遵纪守法的好孩子呢！";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b希儿说：§a让希儿翻翻 ").append(player).append(" 的黑历史……哇哦，有点东西嘛！\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b希儿说：§7调皮捣蛋可是要额外收费的~下次不许再犯哦！");
        return sb.toString().trim();
    }
}