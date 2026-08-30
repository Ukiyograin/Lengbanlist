package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.Utils;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class KickCommand implements CommandExecutor, TabCompleter {
    private final Lengbanlist plugin;

    public KickCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("kick")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }


        if (!sender.hasPermission("lengbanlist.kick")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }


        boolean silent = false;
        if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
            silent = true;
            args = Arrays.copyOfRange(args, 1, args.length);
        }

        if (args.length < 1) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /kick [-s] <玩家> [原因]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Utils.sendMessage(sender, plugin.prefix() + "§c玩家 " + args[0] + " 不在线或不存在。");
            return true;
        }


        if (!plugin.getImmunityManager().canPunish(sender, target.getName())) {
            Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(target.getName()));
            return true;
        }


        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "§c你已被管理员踢出服务器";
        Model model = plugin.getModelManager().getCurrentModel();


        SchedulerUtils.runTask(plugin, target, () -> target.kickPlayer(model.getKickMessage(reason)));
        plugin.getAuditManager().log("踢出", Utils.getSenderName(sender), target.getName(), reason);
        if (!silent) {
            Utils.sendMessage(sender, plugin.prefix() + model.onKickSuccess(target.getName(), reason));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) completions.add(p.getName());
            }
        }
        return completions;
    }
}
