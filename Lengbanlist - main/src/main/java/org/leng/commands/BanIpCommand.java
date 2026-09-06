package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.BanManager;
import org.leng.manager.BanMutationFeedback;
import org.leng.manager.EscalationManager.EscalationResult;
import org.leng.utils.IpMatcher;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class BanIpCommand extends Command implements CommandExecutor, TabCompleter {

    private final Lengbanlist plugin;

    public BanIpCommand(Lengbanlist plugin) {
        super("ban-ip");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String s, String[] args) {
        if (!plugin.isFeatureEnabled("ban-ip")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }


        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!sender.isOp() && !player.hasPermission("lengbanlist.banip")) {
                Utils.sendMessage(sender, "§c你没有权限使用此命令。");
                return false;
            }
        }


        boolean silent = false;
        if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
            silent = true;
            args = Arrays.copyOfRange(args, 1, args.length);
        }

        if (args.length < 3) {
            Utils.sendMessage(sender, "§c用法错误喵: /ban-ip <IP> <时间/auto> <原因>");
            Utils.sendMessage(sender, "§c时间单位喵: s(秒), m(分), h(时), d(天), w(周), M(月), y(年)");
            Utils.sendMessage(sender, "§c使用 auto 自动计算封禁时间喵");
            Utils.sendMessage(sender, "§7在第一位加上 -s 可静默执行（不向全服广播）喵");
            return false;
        }


        if (!isValidIp(args[0])) {
            Utils.sendMessage(sender, "§c无效的IP地址或不允许封禁此IP");
            return false;
        }

        // IP 路径之前漏了 canPunish 检查,低权限 OP 能 ban 高权限家庭 IP,补齐与 BanCommand 对齐
        if (!plugin.getImmunityManager().canPunish(sender, args[0])) {
            Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(args[0]));
            return false;
        }

        if (plugin.getBanManager().isIpBanned(args[0])) {
            Utils.sendMessage(sender, "§cIP " + args[0] + " 已经被封禁");
            return false;
        }


        boolean isAuto = args[1].equalsIgnoreCase("auto");
        long banDuration;
        EscalationResult escalationResult = null;

        if (isAuto) {
            escalationResult = plugin.getEscalationManager().resolveIpBan(args[0]);
            banDuration = escalationResult.durationMillis;
        } else {
            banDuration = TimeUtils.parseDurationToMillis(args[1]);
            if (banDuration <= 0) {
                showTimeFormatError(sender);
                return false;
            }
        }

        long banEndTime = TimeUtils.calculateEndTime(banDuration);
        String rawReason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        String reason = resolvePresetReason(rawReason);


        BanManager.BanMutationResult result = plugin.getBanManager().tryBanIp(
                new org.leng.object.BanIpEntry(args[0], Utils.getSenderName(sender), banEndTime, reason, isAuto),
                silent
        );
        if (!result.isApplied()) {
            BanMutationFeedback.sendFailure(sender, result, args[0], true);
            return true;
        }

        if (escalationResult != null && escalationResult.offenseCount > 0) {
            Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().onEscalatedBan(
                    args[0], escalationResult.offenseCount, TimeUtils.formatDuration(banDuration, TimeUtils.isEnglishLocale())));
        }
        return true;
    }

    private boolean isValidIp(String ip) {
        return IpMatcher.isValidIpOrCidrOrWildcard(ip);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        int offset = (args.length > 0 && args[0].equalsIgnoreCase("-s")) ? 1 : 0;
        if (args.length - offset == 1) {
            List<String> completions = new ArrayList<>();
            if (offset == 0 && "-s".startsWith(args[0].toLowerCase())) completions.add("-s");
            return completions;
        }
        if (args.length - offset == 3) {
            String prefix = args[offset + 2].toLowerCase();
            List<String> presets = new ArrayList<>();
            if (plugin.getConfig().isConfigurationSection("preset-reasons")) {
                presets.addAll(plugin.getConfig().getConfigurationSection("preset-reasons").getKeys(false));
            }
            List<String> completions = new ArrayList<>();
            for (String key : presets) {
                if (key.toLowerCase().startsWith(prefix)) completions.add(key);
            }
            return completions;
        }
        return null;
    }

    private void showTimeFormatError(CommandSender sender) {
        Utils.sendMessage(sender, "§c时间格式错误喵，请使用以下格式:");
        Utils.sendMessage(sender, "§c - 10s: 秒 (10 秒)");
        Utils.sendMessage(sender, "§c - 5m: 分钟 (5 分钟)");
        Utils.sendMessage(sender, "§c - 2h: 小时 (2 小时)");
        Utils.sendMessage(sender, "§c - 7d: 天 (7 天)");
        Utils.sendMessage(sender, "§c - 1w: 周 (1 周，等于 7 天)");
        Utils.sendMessage(sender, "§c - 1M: 月 (1 月，按 30 天计算)");
        Utils.sendMessage(sender, "§c - 1y: 年 (1 年，按 365 天计算)");
        Utils.sendMessage(sender, "§c - auto: 自动计算封禁时间");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }

    private String resolvePresetReason(String input) {
        if (input == null || !plugin.getConfig().isConfigurationSection("preset-reasons")) return input;
        String value = plugin.getConfig().getString("preset-reasons." + input.toLowerCase());
        return value != null ? value : input;
    }

}
