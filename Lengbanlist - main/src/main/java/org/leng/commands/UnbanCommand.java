package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.BanManager;
import org.leng.manager.BanMutationFeedback;
import org.leng.utils.IpMatcher;
import org.leng.utils.Utils;

public class UnbanCommand extends Command implements CommandExecutor {

    private final Lengbanlist plugin;

    public UnbanCommand(Lengbanlist plugin) {
        super("unban");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String s, String[] args) {
        if (!plugin.isFeatureEnabled("unban")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }


        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!sender.isOp() && !(player.hasPermission("lengbanlist.unban"))) {
                Utils.sendMessage(sender, "§c你没有权限使用此命令。");
                return false;
            }
        }


        boolean silent = false;
        if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
            silent = true;
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }

        if (args.length < 1) {
            Utils.sendMessage(sender, "§c用法错误喵: /unban [-s] <玩家名/IP>");
            return false;
        }

        if (args[0].contains(".")) {
            String normalized = IpMatcher.normalizeIpOrCidr(args[0]);
            if (normalized == null) {
                Utils.sendMessage(sender, "§c无效的IP地址");
                return false;
            }
            BanManager.BanMutationResult result = plugin.getBanManager()
                    .tryUnbanIp(normalized, Utils.getSenderName(sender), silent);
            if (!result.isApplied()) {
                BanMutationFeedback.sendFailure(sender, result, normalized, true);
            }
        } else {
            BanManager.BanMutationResult result = plugin.getBanManager()
                    .tryUnbanPlayer(args[0], Utils.getSenderName(sender), silent);
            if (!result.isApplied()) {
                BanMutationFeedback.sendFailure(sender, result, args[0], false);
            }
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }

}
