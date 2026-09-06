package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.manager.WarnManager;
import org.leng.utils.IpMatcher;
import org.leng.utils.Utils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class WarnCommand extends Command implements CommandExecutor, TabCompleter {
    private final Lengbanlist plugin;

    public WarnCommand(Lengbanlist plugin) {
        super("warn");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String s, String[] args) {
        if (!plugin.isFeatureEnabled("warn")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }


        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!sender.isOp() && !player.hasPermission("lengbanlist.warn")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return false;
            }
        }


        boolean silent = false;
        if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
            silent = true;
            args = Arrays.copyOfRange(args, 1, args.length);
        }

        if (args.length < 2) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /warn [-s] <玩家名/IP> <原因>");
            return false;
        }

        String target = args[0];
        String rawReason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String reason = resolvePresetReason(rawReason);
        WarnManager warnManager = plugin.getWarnManager();


        boolean isIp = target.contains(".");


        if (!isIp && !plugin.getImmunityManager().canPunish(sender, target)) {
            Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(target));
            return false;
        }

        if (isIp) {
            if (!IpMatcher.isValidIpOrCidrOrWildcard(target)) {
                Utils.sendMessage(sender, plugin.prefix() + "§c无效的IP地址");
                return false;
            }

            String normalized = IpMatcher.normalizeIpOrCidr(target);
            if (normalized != null) target = normalized;
            warnManager.warnPlayer(target, Utils.getSenderName(sender), reason);
            if (!silent) {
                Utils.sendMessage(sender, ModelManager.getInstance().getCurrentModel().addWarn(target, reason));
            }
            return true;
        }

        warnManager.warnPlayer(target, Utils.getSenderName(sender), reason);
        if (!silent) {
            Utils.sendMessage(sender, ModelManager.getInstance().getCurrentModel().addWarn(target, reason));
        }

        // 临近 LBAC 自动封禁阈值时提醒管理员,避免反复 /warn 直接自循环触发封禁
        // legacyWarnBased: 4→14天, 5→永久,4 次为临界
        try {
            int activeCount = warnManager.countActiveWarnings(target);
            if (activeCount >= 4) {
                Utils.sendMessage(sender, plugin.prefix() + "§c⚠ 目标 " + target + " 当前已有 " + activeCount + " 条警告，下次违规将自动永久封禁（LBAC 升级）。");
            } else if (activeCount == 3) {
                Utils.sendMessage(sender, plugin.prefix() + "§e⚠ 目标 " + target + " 当前已有 " + activeCount + " 条警告，再多 1 条将触发 14 天封禁（LBAC 升级）。");
            }
        } catch (Exception e) {
            // 阈值检查失败不影响主流程,静默
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) completions.add(p.getName());
            }
            return completions;
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
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
