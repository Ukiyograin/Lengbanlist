package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.utils.IpMatcher;
import org.leng.utils.Utils;

import java.util.Arrays;

public class UnmuteCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public UnmuteCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("mute")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        if (!sender.hasPermission("lengbanlist.mute")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }
        boolean silent = false;
        if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
            silent = true;
            args = Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length < 1) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /" + label + " <玩家名>");
            return true;
        }
        String normalized = IpMatcher.normalizeIpOrCidr(args[0]);
        if (normalized != null) args[0] = normalized;
        // 仅当目标实际处于禁言状态时才广播 / 审计,避免未禁言玩家被全服假解禁
        boolean removed = plugin.getMuteManager().unmutePlayerIfMuted(args[0], Utils.getSenderName(sender));
        if (!removed) {
            Utils.sendMessage(sender, plugin.prefix() + "§e玩家 " + args[0] + " 当前并未被禁言");
            return true;
        }
        String message = ModelManager.getInstance().getCurrentModel().removeMute(args[0]);
        if (silent) {
            Utils.sendMessage(sender, message);
        } else {
            Utils.broadcast(message);
        }
        return true;
    }
}
