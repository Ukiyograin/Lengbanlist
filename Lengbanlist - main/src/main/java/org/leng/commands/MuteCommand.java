package org.leng.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;
import org.leng.Lengbanlist;
import org.leng.manager.MuteManager;
import org.leng.models.Model;
import org.leng.utils.Utils;
import org.leng.object.MuteEntry;

import java.util.Arrays;

public class MuteCommand extends Command {
    private final Lengbanlist plugin;

    public MuteCommand(String name, Lengbanlist plugin) {
        super(name);
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String s, String[] args) {
        Model currentModel = plugin.getModelManager().getCurrentModel();
        MuteManager muteManager = plugin.getMuteManager();

        if (!sender.hasPermission("lengbanlist.mute")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }

        if (args.length < 2) {
            Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式：");
            Utils.sendMessage(sender, plugin.prefix() + "§b/lban mute <玩家名> <原因>");
            Utils.sendMessage(sender, plugin.prefix() + "§b/lban unmute <玩家名>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "mute":
                if (args.length < 3) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式：/lban mute <玩家名> <原因>");
                    return true;
                }
                String target = args[1];
                String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                MuteEntry muteEntry = new MuteEntry(target, sender.getName(), System.currentTimeMillis(), reason);
                muteManager.mutePlayer(muteEntry);
                Utils.sendMessage(sender, currentModel.addMute(target, reason));
                break;

            case "unmute":
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l错误的命令格式，正确格式：/lban unmute <玩家名>");
                    return true;
                }
                muteManager.unmutePlayer(args[1]);
                Utils.sendMessage(sender, currentModel.removeMute(args[1]));
                break;

            default:
                Utils.sendMessage(sender, plugin.prefix() + "§c未知的子命令。");
                break;
        }
        return true;
    }
}