package org.leng.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.utils.TimeUtils;

import java.util.List;

/** 封禁管理，统一处理玩家封禁、IP 封禁、解封、加入检查。通过 plugin 引用获取数据库和模型管理器。 */
public class BanManager {
    private final Lengbanlist plugin;
    private final DatabaseManager db;

    public BanManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void banPlayer(BanEntry banEntry) {
        long durationMillis = banEntry.getEndTime() - System.currentTimeMillis();
        int durationDays = (int) Math.max(1, Math.round(durationMillis / (double)(1000 * 60 * 60 * 24)));

        Model currentModel = plugin.getModelManager().getCurrentModel();
        String banResult = currentModel.addBan(banEntry.getTarget(), durationDays, banEntry.getReason());
        updateBan(banEntry);

        Player targetPlayer = Bukkit.getPlayer(banEntry.getTarget());
        if (targetPlayer != null) {
            String kickMessage = String.format(
                    "§c您已被封禁!\n" +
                            "§f原因: §e%s\n" +
                            "§f封禁时长: §a%s\n" +
                            "§f解封时间: §b%s",
                    banEntry.getReason(),
                    TimeUtils.formatDuration(durationMillis),
                    TimeUtils.timestampToReadable(banEntry.getEndTime())
            );
            targetPlayer.kickPlayer(kickMessage);
        }

        if (banResult != null && !banResult.isEmpty()) {
            Bukkit.broadcastMessage(banResult);
        } else {
            String defaultMessage = String.format("§c玩家 %s 已被封禁！原因：%s，时长：%s", banEntry.getTarget(), banEntry.getReason(), TimeUtils.formatDuration(durationMillis));
            Bukkit.broadcastMessage(defaultMessage);
            String modelName = plugin.getModelManager().getCurrentModelName();
            Bukkit.getLogger().warning("模型 [" + modelName + "] 封禁玩家 [" + banEntry.getTarget() + "] 时未返回消息，使用默认消息");
        }
    }

    public void banIp(BanIpEntry banIpEntry) {
        long durationMillis = banIpEntry.getEndTime() - System.currentTimeMillis();
        int durationDays = (int) Math.max(1, Math.round(durationMillis / (double)(1000 * 60 * 60 * 24)));

        Model currentModel = plugin.getModelManager().getCurrentModel();
        String banIpResult = currentModel.addBanIp(banIpEntry.getIp(), durationDays, banIpEntry.getReason());
        updateIpBan(banIpEntry);

        if (banIpResult != null && !banIpResult.isEmpty()) {
            Bukkit.broadcastMessage(banIpResult);
        } else {
            String defaultMessage = String.format("§cIP %s 已被封禁！原因：%s，时长：%s", banIpEntry.getIp(), banIpEntry.getReason(), TimeUtils.formatDuration(durationMillis));
            Bukkit.broadcastMessage(defaultMessage);
            String modelName = plugin.getModelManager().getCurrentModelName();
            Bukkit.getLogger().warning("模型 [" + modelName + "] 封禁 IP [" + banIpEntry.getIp() + "] 时未返回消息，使用默认消息");
        }
    }

    public void unbanPlayer(String target) {
        Model currentModel = plugin.getModelManager().getCurrentModel();
        String unbanResult = currentModel.removeBan(target);
        boolean removed = isPlayerBanned(target);
        db.deactivateBan(target);

        if (removed) {
            if (unbanResult != null && !unbanResult.isEmpty()) {
                Bukkit.broadcastMessage(unbanResult);
            } else {
                Bukkit.broadcastMessage(String.format("§a玩家 %s 已被解封", target));
                String modelName = plugin.getModelManager().getCurrentModelName();
                Bukkit.getLogger().warning("模型 [" + modelName + "] 解封玩家 [" + target + "] 时未返回消息，使用默认消息");
            }
        } else {
            String modelName = plugin.getModelManager().getCurrentModelName();
            Bukkit.getLogger().warning("通过模型 [" + modelName + "] 解封玩家 [" + target + "] 失败：玩家不在封禁列表中");
        }
    }

    public void unbanIp(String ip) {
        Model currentModel = plugin.getModelManager().getCurrentModel();
        String unbanIpResult = currentModel.removeBanIp(ip);
        boolean removed = isIpBanned(ip);
        db.deactivateIpBan(ip);

        if (removed) {
            if (unbanIpResult != null && !unbanIpResult.isEmpty()) {
                Bukkit.broadcastMessage(unbanIpResult);
            } else {
                Bukkit.broadcastMessage(String.format("§aIP %s 已被解封", ip));
                String modelName = plugin.getModelManager().getCurrentModelName();
                Bukkit.getLogger().warning("模型 [" + modelName + "] 解封 IP [" + ip + "] 时未返回消息，使用默认消息");
            }
        } else {
            String modelName = plugin.getModelManager().getCurrentModelName();
            Bukkit.getLogger().warning("通过模型 [" + modelName + "] 解封 IP [" + ip + "] 失败：IP不在封禁列表中");
        }
    }

    public boolean isPlayerBanned(String target) {
        return db.isPlayerBanned(target);
    }

    public boolean isIpBanned(String ip) {
        return db.isIpBanned(ip);
    }

    public List<BanEntry> getBanList() {
        return db.getBans();
    }

    public List<BanIpEntry> getBanIpList() {
        return db.getIpBans();
    }

    public void checkBanOnJoin(Player player) {
        BanEntry ban = getBanEntry(player.getName());
        if (ban != null) {
            long currentTime = System.currentTimeMillis();
            if (ban.getTime() <= currentTime) {
                unbanPlayer(player.getName());
            } else {
                player.kickPlayer("您仍处于封禁状态，原因：" + ban.getReason() + "，封禁到：" + TimeUtils.timestampToReadable(ban.getTime()));
            }
        }

        String ip = player.getAddress().getAddress().getHostAddress();
        BanIpEntry banIp = getBanIpEntry(ip);
        if (banIp != null) {
            long currentTime = System.currentTimeMillis();
            if (banIp.getTime() <= currentTime) {
                unbanIp(ip);
            } else {
                player.kickPlayer("您的 IP 仍处于封禁状态，原因：" + banIp.getReason() + "，封禁到：" + TimeUtils.timestampToReadable(banIp.getTime()));
            }
        }
    }

    public BanEntry getBanEntry(String target) {
        return db.getBan(target);
    }

    public BanIpEntry getBanIpEntry(String ip) {
        return db.getIpBan(ip);
    }

    public void updateBan(BanEntry entry) {
        db.upsertBan(entry);
    }

    public void updateIpBan(BanIpEntry entry) {
        db.upsertIpBan(entry);
    }

    public boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return ip.contains(":");
        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public boolean isBanned(String player, String reason) {
        BanEntry banEntry = getBanEntry(player);
        return banEntry != null && banEntry.getReason().contains(reason);
    }

    public void saveBanList() {}
    public void saveBanIpConfig() {}
}
