package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.BanManager;
import org.leng.manager.BanMutationFeedback;
import org.leng.manager.EscalationManager.EscalationResult;
import org.leng.object.BanEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class BanCommand implements CommandExecutor, TabCompleter {

    private final Lengbanlist plugin;

    public BanCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("ban")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }


        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!sender.isOp() && !player.hasPermission("lengbanlist.ban")) {
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
            sendUsage(sender);
            return false;
        }

        String target = args[0];
        String timeArg = args[1];
        String rawReason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        String reason = resolvePresetReason(rawReason);


        if (!plugin.getImmunityManager().canPunish(sender, target)) {
            Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(target));
            return false;
        }

        if (plugin.getBanManager().isPlayerBanned(target)) {
            Utils.sendMessage(sender, "§c玩家 " + target + " 已经被封禁");
            return false;
        }

        long banDuration;
        boolean isAuto = false;
        EscalationResult escalationResult = null;

        if (timeArg.equalsIgnoreCase("auto")) {
            isAuto = true;
            escalationResult = plugin.getEscalationManager().resolveBan(target);
            banDuration = escalationResult.durationMillis;
        } else {
            banDuration = TimeUtils.parseDurationToMillis(timeArg);
            if (banDuration <= 0) {
                showTimeFormatError(sender);
                return false;
            }

        }

        long banEndTime = TimeUtils.calculateEndTime(banDuration);

        BanEntry entry = new BanEntry(
                target,
                Utils.getSenderName(sender),
                banEndTime,
                reason,
                isAuto
        );

        BanManager.BanMutationResult result = plugin.getBanManager().tryBanPlayer(entry, silent);
        if (!result.isApplied()) {
            BanMutationFeedback.sendFailure(sender, result, target, false);
            return true;
        }

        if (escalationResult != null && escalationResult.offenseCount > 0) {
            Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().onEscalatedBan(
                    target, escalationResult.offenseCount, TimeUtils.formatDuration(banDuration, TimeUtils.isEnglishLocale())));
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        Utils.sendMessage(sender, "§c用法错误喵: /ban <玩家> <时间/auto> <原因>");
        Utils.sendMessage(sender, "§c时间单位喵: s(秒), m(分), h(时), d(天), w(周), M(月), y(年)");
        Utils.sendMessage(sender, "§c使用 auto 自动计算封禁时间喵（基于警告次数）");
        Utils.sendMessage(sender, "§7在第一位加上 -s 可静默执行（不向全服广播）喵");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        int offset = (args.length > 0 && args[0].equalsIgnoreCase("-s")) ? 1 : 0;
        if (args.length - offset == 1) {
            String prefix = args[offset].toLowerCase();
            List<String> completions = new ArrayList<>();
            if (offset == 0 && "-s".startsWith(prefix)) completions.add("-s");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) completions.add(p.getName());
            }
            return completions;
        }
        if (args.length - offset == 3) {
            String prefix = args[offset + 2].toLowerCase();
            List<String> presets = plugin.getConfig().getStringList("preset-reasons");

            if (presets.isEmpty() && plugin.getConfig().isConfigurationSection("preset-reasons")) {
                presets = new ArrayList<>(plugin.getConfig().getConfigurationSection("preset-reasons").getKeys(false));
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
        Utils.sendMessage(sender, "§c - forever: 永久封禁");
        Utils.sendMessage(sender, "§c - auto: 自动计算封禁时间");
    }

    private String resolvePresetReason(String input) {
        if (input == null || !plugin.getConfig().isConfigurationSection("preset-reasons")) return input;
        String value = plugin.getConfig().getString("preset-reasons." + input.toLowerCase());
        return value != null ? value : input;
    }

}
