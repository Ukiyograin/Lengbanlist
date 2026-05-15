package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class HuTao implements Model {
    @Override
    public String getName() {
        return "HuTao";
    }

@Override
public void showHelp(CommandSender sender) {
    Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
    Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 胡桃风格 §b║");
    Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
    Utils.sendMessage(sender, "§6§l◆ 处罚管理");
    Utils.sendMessage(sender, "§2✦ §b/lban add <玩家名> <天数> <原因> §7- §3添加封禁，不守规矩就封了！");
    Utils.sendMessage(sender, "§7  = §b/ban");
    Utils.sendMessage(sender, "§2✦ §b/lban remove <玩家名> §7- §3移除封禁，知错能改就放过他们吧！");
    Utils.sendMessage(sender, "§7  = §b/unban");
    Utils.sendMessage(sender, "§2✦ §b/ban-ip <IP地址> <天数> <原因> §7- §3封禁 IP 地址，别再捣乱了！");
    Utils.sendMessage(sender, "§2✦ §b/lban mute <玩家名> <原因> §7- §3禁言玩家，让他们安静一会儿！");
    Utils.sendMessage(sender, "§7  = §b/mute");
    Utils.sendMessage(sender, "§2✦ §b/lban unmute <玩家名> §7- §3解除禁言，让他们继续说话吧！");
    Utils.sendMessage(sender, "§7  = §b/unmute");
    Utils.sendMessage(sender, "§2✦ §b/lban warn <玩家名> <原因> §7- §3警告玩家，三次警告自动封禁！");
    Utils.sendMessage(sender, "§7  = §b/warn");
    Utils.sendMessage(sender, "§2✦ §b/lban unwarn <玩家名> §7- §3移除玩家警告");
    Utils.sendMessage(sender, "§7  = §b/unwarn");
    Utils.sendMessage(sender, "§2✦ §b/kick <玩家名> <原因> §7- §3踢出捣乱的玩家！");
    Utils.sendMessage(sender, "§2✦ §b/setban <玩家名/IP> <时间/forever/auto> <原因> §7- §3修改封禁时间，不守规矩就别怪胡桃无情！");
    Utils.sendMessage(sender, "§6§l◆ 查询信息");
    Utils.sendMessage(sender, "§2✦ §b/lban check <玩家名/IP> §7- §3检查封禁状态，看看谁在捣乱！");
    Utils.sendMessage(sender, "§2✦ §b/lban history <玩家名> §7- §3翻翻案底，让胡桃瞧瞧！");
    Utils.sendMessage(sender, "§7  = §b/history");
    Utils.sendMessage(sender, "§2✦ §b/report <玩家名> <原因> §7- §3发现捣乱的家伙？快举报给胡桃！");
    Utils.sendMessage(sender, "§2✦ §b/lban getip <玩家名> §7- §3查询玩家 IP 地址，看看谁在捣乱！");
    Utils.sendMessage(sender, "§6§l◆ 杂项");
    Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看封禁名单，这些家伙真是麻烦！");
    Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表，看看谁被胡桃禁言了！");
    Utils.sendMessage(sender, "§7  = §b/listmute");
    Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数，让大家都知道！");
    Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，想听就听不想听就关！");
    Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面！");
    Utils.sendMessage(sender, "§2✦ §b/lban model <模型名称> §7- §3切换模型，试试别的风格吧！");
    Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，说不定能发现新东西！");
    Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息");
    Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
    Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 胡桃 HuTao");
}

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d胡桃的驱逐通知  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ 你被胡桃踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d下次请遵守规则哦~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 胡桃说：§a" + playerName + " §e已被踢出！\n" +
               "§b原因: §f" + reason + "\n" +
               "§b维护往生堂的和平！§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b胡桃说：§a自动广播已经 " + (enabled ? "开启！" : "关闭！") + " 想听就听，不想听就关！";
    }

    @Override
    public String reloadConfig() {
        return "§b胡桃说：§a配置重新加载完成！说不定能发现新东西！";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b胡桃说：§a" + player + " 已被封禁 " + days + " 天，原因是：" + reason + "！不守规矩，就别怪胡桃无情！";
    }

    @Override
    public String removeBan(String player) {
        return "§b胡桃说：§a" + player + " 已从封禁名单中移除。知错能改，就放过他们吧！";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b胡桃说：§a" + player + " 已被禁言，原因是：" + reason + "！让他们安静一会儿吧！";
    }

    @Override
    public String removeMute(String player) {
        return "§b胡桃说：§a" + player + " 的禁言已解除，可以继续说话了！";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b胡桃说：§aIP " + ip + " 已被封禁 " + days + " 天，原因是：" + reason + "！别再捣乱了！";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b胡桃说：§aIP " + ip + " 的封禁已解除，放过他们吧！";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b胡桃说：§a玩家 " + player + " 已被警告，原因是：" + reason + "！警告三次将被自动封禁！";
    }

    @Override
    public String removeWarn(String player) {
        return "§b胡桃说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b胡桃说：§a" + player + " 是个乖孩子，没有任何案底哦！往生堂给他点赞~";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b胡桃说：§a让胡桃翻翻 ").append(player).append(" 的案底……哎呀，还挺有故事的嘛！\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b胡桃说：§7看完了吧？记得遵纪守法哦，不然往生堂随时欢迎~");
        return sb.toString().trim();
    }
}