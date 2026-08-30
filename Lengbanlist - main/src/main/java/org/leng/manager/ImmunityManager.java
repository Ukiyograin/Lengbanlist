package org.leng.manager;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;

import java.util.OptionalInt;
import java.util.UUID;

public class ImmunityManager {
    private final Lengbanlist plugin;
    private Boolean luckPermsPresent;

    public ImmunityManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public boolean canPunish(CommandSender staff, String targetName) {
        if (!plugin.isFeatureEnabled("immunity")) {
            return true;
        }
        return getStaffWeight(staff) > getTargetWeight(targetName);
    }

    public boolean canPunish(int operatorWeight, String targetName) {
        if (!plugin.isFeatureEnabled("immunity")) {
            return true;
        }
        return operatorWeight > getTargetWeight(targetName);
    }

    public boolean canPunishTarget(int operatorWeight, String target) {
        if (!plugin.isFeatureEnabled("immunity")) {
            return true;
        }
        if (target != null && target.contains(".")) {
            return operatorWeight > getIpTargetWeight(target);
        }
        return operatorWeight > getTargetWeight(target);
    }

    private int getIpTargetWeight(String ip) {
        int highest = Integer.MIN_VALUE;
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getAddress() == null || online.getAddress().getAddress() == null) {
                continue;
            }
            String playerIp = online.getAddress().getAddress().getHostAddress();
            if (playerIp == null || !playerIp.equals(ip)) {
                continue;
            }
            int weight = resolveWeight(online);
            if (weight > highest) {
                highest = weight;
            }
        }
        return highest;
    }

    public int getWebOperatorWeight() {
        return plugin.getConfig().getInt("web.operator-weight", Integer.MAX_VALUE);
    }

    public int getStaffWeight(CommandSender staff) {
        if (!(staff instanceof Player)) {
            return Integer.MAX_VALUE;
        }
        Player player = (Player) staff;
        if (player.isOp()) {
            return Integer.MAX_VALUE;
        }
        return resolveWeight(player);
    }

    public int getTargetWeight(String targetName) {
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            return Integer.MIN_VALUE;
        }
        return resolveWeight(target);
    }

    private int resolveWeight(Player player) {
        if (isLuckPermsPresent()) {
            int luckPermsWeight = getLuckPermsWeight(player);
            if (luckPermsWeight != -1) {
                return luckPermsWeight;
            }
        }
        for (int i = 99; i >= 1; i--) {
            if (player.hasPermission("lengbanlist.weight." + i)) {
                return i;
            }
        }
        return plugin.getConfig().getInt("immunity.default-weight", 0);
    }

    private boolean isLuckPermsPresent() {
        if (luckPermsPresent == null) {
            luckPermsPresent = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
        }
        return luckPermsPresent;
    }

    private int getLuckPermsWeight(Player player) {
        try {
            Object api = Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) {
                return -1;
            }
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            if (cachedData == null) {
                return -1;
            }
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            if (metaData == null) {
                return -1;
            }
            Object primaryGroup = metaData.getClass().getMethod("getPrimaryGroup").invoke(metaData);
            if (primaryGroup == null) {
                return -1;
            }
            Object groupManager = api.getClass().getMethod("getGroupManager").invoke(api);
            Object group = groupManager.getClass().getMethod("getGroup", String.class).invoke(groupManager, primaryGroup);
            if (group == null) {
                return -1;
            }
            Object weight = group.getClass().getMethod("getWeight").invoke(group);
            if (weight instanceof OptionalInt) {
                OptionalInt optionalWeight = (OptionalInt) weight;
                if (optionalWeight.isPresent()) {
                    return optionalWeight.getAsInt();
                }
            }
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
