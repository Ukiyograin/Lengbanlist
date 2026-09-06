package org.leng.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.IpMatcher;
import org.leng.utils.Utils;

import java.util.List;


public class BanManager {

    public enum BanMutationResult {
        APPLIED,
        NOT_ACTIVE,
        STATE_CHANGED,
        REJECTED_PRIVATE_OR_RESERVED_IP,
        DATABASE_ERROR;

        public boolean isApplied() {
            return this == APPLIED;
        }
    }

    private final Lengbanlist plugin;
    private final DatabaseManager db;

    public BanManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public BanMutationResult tryBanPlayer(BanEntry banEntry) {
        return tryBanPlayer(banEntry, false);
    }

    public BanMutationResult tryBanPlayer(BanEntry banEntry, boolean silent) {
        BanMutationResult writeResult = mapWriteResult(db.replaceActiveBan(banEntry));
        if (!writeResult.isApplied()) {
            return writeResult;
        }

        publishAppliedPlayerBan(banEntry, silent);
        return BanMutationResult.APPLIED;
    }

    void publishAppliedPlayerBan(BanEntry banEntry, boolean silent) {
        long durationMillis = banEntry.getEndTime() == Long.MAX_VALUE ? Long.MAX_VALUE : banEntry.getEndTime() - System.currentTimeMillis();
        int durationDays = durationMillis == Long.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1, Math.round(durationMillis / (double) (1000 * 60 * 60 * 24)));

        Model currentModel = plugin.getModelManager().getCurrentModel();
        String banResult = currentModel.addBan(banEntry.getTarget(), durationDays, banEntry.getReason());
        plugin.getAuditManager().log("封禁", banEntry.getStaff(), banEntry.getTarget(), banEntry.getReason());

        // 触发自定义事件,允许其他插件响应
        org.bukkit.Bukkit.getPluginManager().callEvent(new org.leng.api.events.LengbanlistBanEvent(banEntry, silent));

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
            SchedulerUtils.runTask(plugin, targetPlayer, () -> targetPlayer.kickPlayer(kickMessage));
        }

        if (!silent) {
            if (banResult != null && !banResult.isEmpty()) {
                Utils.broadcast(banResult);
            } else {
                String defaultMessage = String.format("§c玩家 %s 已被封禁！原因：%s，时长：%s", banEntry.getTarget(), banEntry.getReason(), TimeUtils.formatDuration(durationMillis));
                Utils.broadcast(defaultMessage);
            }
        }
    }

    public BanMutationResult tryBanIp(BanIpEntry banIpEntry) {
        return tryBanIp(banIpEntry, false);
    }

    public BanMutationResult tryBanIp(BanIpEntry banIpEntry, boolean silent) {
        if (isPrivateOrReservedIp(banIpEntry)) {
            return BanMutationResult.REJECTED_PRIVATE_OR_RESERVED_IP;
        }
        BanMutationResult writeResult = mapWriteResult(db.replaceActiveIpBan(banIpEntry));
        if (!writeResult.isApplied()) {
            return writeResult;
        }
        long durationMillis = banIpEntry.getEndTime() == Long.MAX_VALUE ? Long.MAX_VALUE : banIpEntry.getEndTime() - System.currentTimeMillis();
        int durationDays = durationMillis == Long.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1, Math.round(durationMillis / (double) (1000 * 60 * 60 * 24)));

        Model currentModel = plugin.getModelManager().getCurrentModel();
        String banIpResult = currentModel.addBanIp(banIpEntry.getIp(), durationDays, banIpEntry.getReason());
        plugin.getAuditManager().log("封禁IP", banIpEntry.getStaff(), banIpEntry.getIp(), banIpEntry.getReason());

        org.bukkit.Bukkit.getPluginManager().callEvent(new org.leng.api.events.LengbanlistBanIpEvent(banIpEntry, silent));

        if (!silent) {
            if (banIpResult != null && !banIpResult.isEmpty()) {
                Utils.broadcast(banIpResult);
            } else {
                String defaultMessage = String.format("§cIP %s 已被封禁！原因：%s，时长：%s", banIpEntry.getIp(), banIpEntry.getReason(), TimeUtils.formatDuration(durationMillis));
                Utils.broadcast(defaultMessage);
            }
        }
        return BanMutationResult.APPLIED;
    }

    public BanMutationResult tryUnbanPlayer(String target, String actor, boolean silent) {
        long now = System.currentTimeMillis();
        BanMutationResult writeResult = mapWriteResult(db.deactivateBanForUnban(target, now));
        if (writeResult.isApplied()) {
            Model currentModel = plugin.getModelManager().getCurrentModel();
            String unbanResult = currentModel.removeBan(target);
            plugin.getAuditManager().log("解封", actor, target, "");
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.leng.api.events.LengbanlistUnbanEvent(target, false, actor));
            if (!silent) {
                if (unbanResult != null && !unbanResult.isEmpty()) {
                    Utils.broadcast(unbanResult);
                } else {
                    Utils.broadcast(String.format("§a玩家 %s 已被解封", target));
                }
            }
            return BanMutationResult.APPLIED;
        }
        return writeResult;
    }

    public BanMutationResult tryUnbanIp(String ip, String actor, boolean silent) {
        long now = System.currentTimeMillis();
        BanMutationResult writeResult = mapWriteResult(db.deactivateIpBanForUnban(ip, now));
        if (writeResult.isApplied()) {
            Model currentModel = plugin.getModelManager().getCurrentModel();
            String unbanIpResult = currentModel.removeBanIp(ip);
            plugin.getAuditManager().log("解封IP", actor, ip, "");
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.leng.api.events.LengbanlistUnbanEvent(ip, true, actor));
            if (!silent) {
                if (unbanIpResult != null && !unbanIpResult.isEmpty()) {
                    Utils.broadcast(unbanIpResult);
                } else {
                    Utils.broadcast(String.format("§aIP %s 已被解封", ip));
                }
            }
            return BanMutationResult.APPLIED;
        }
        return writeResult;
    }

    public void kickOnlineIfBanned(String target, boolean isIp) {
        if (target == null || target.isEmpty()) return;
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (isIp) {
                java.net.InetSocketAddress addr = online.getAddress();
                if (addr == null || addr.getAddress() == null) continue;
                String ip = addr.getAddress().getHostAddress();
                if (ip == null) continue;
                BanIpEntry banIp = getMatchingIpBan(ip);
                if (banIp == null || banIp.getTime() <= System.currentTimeMillis()) continue;
                SchedulerUtils.runTask(plugin, online, () -> online.kickPlayer("您的 IP 已被封禁，原因：" + banIp.getReason() + "，封禁到：" + TimeUtils.timestampToReadable(banIp.getTime())));
            } else {
                if (!online.getName().equalsIgnoreCase(target)) continue;
                BanEntry ban = getBanEntry(target);
                if (ban == null || ban.getTime() <= System.currentTimeMillis()) continue;
                SchedulerUtils.runTask(plugin, online, () -> online.kickPlayer("您已被封禁，原因：" + ban.getReason() + "，封禁到：" + TimeUtils.timestampToReadable(ban.getTime())));
            }
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
        if (plugin.isFeatureEnabled("ban")) {
            BanEntry ban = getBanEntry(player.getName());
            if (ban != null) {
                long currentTime = System.currentTimeMillis();
                if (ban.getTime() <= currentTime) {
                    BanMutationResult result = tryUnbanPlayer(player.getName(), null, true);
                    if (result == BanMutationResult.DATABASE_ERROR) {
                        plugin.getLogger().warning("清理玩家过期封禁失败，玩家将被拦截: " + player.getName());
                    }
                } else {
                    SchedulerUtils.runTask(plugin, player, () -> player.kickPlayer("您仍处于封禁状态，原因：" + ban.getReason() + "，封禁到：" + TimeUtils.timestampToReadable(ban.getTime())));
                    return;
                }
            }
        }

        if (plugin.isFeatureEnabled("ban-ip") && player.getAddress() != null) {
            String ip = player.getAddress().getAddress().getHostAddress();
            BanIpEntry banIp = getMatchingIpBan(ip);
            if (banIp != null) {
                long currentTime = System.currentTimeMillis();
                if (banIp.getTime() <= currentTime) {
                    BanMutationResult result = tryUnbanIp(banIp.getIp(), null, true);
                    if (result == BanMutationResult.DATABASE_ERROR) {
                        plugin.getLogger().warning("清理过期 IP 封禁失败，玩家将被拦截: " + banIp.getIp());
                    }
                } else {
                    SchedulerUtils.runTask(plugin, player, () -> player.kickPlayer("您的 IP 仍处于封禁状态，原因：" + banIp.getReason() + "，封禁到：" + TimeUtils.timestampToReadable(banIp.getTime())));
                }
            }
        }
    }

    public BanEntry getBanEntry(String target) {
        return db.getBan(target);
    }

    public BanIpEntry getBanIpEntry(String ip) {
        return db.getIpBan(ip);
    }

    public BanMutationResult tryUpdateBan(BanEntry entry) {
        return mapWriteResult(db.replaceExistingActiveBan(entry));
    }

    public BanMutationResult tryUpdateIpBan(BanIpEntry entry) {
        if (isPrivateOrReservedIp(entry)) {
            return BanMutationResult.REJECTED_PRIVATE_OR_RESERVED_IP;
        }
        return mapWriteResult(db.replaceExistingActiveIpBan(entry));
    }

    private boolean isPrivateOrReservedIp(BanIpEntry entry) {
        if (!IpMatcher.isPrivateOrReserved(entry.getIp())) {
            return false;
        }
        plugin.getLogger().warning("已阻止封禁私有/保留 IP: " + entry.getIp() + "（staff: " + entry.getStaff() + "）");
        return true;
    }

    private BanMutationResult mapWriteResult(DatabaseManager.WriteResult writeResult) {
        if (writeResult == DatabaseManager.WriteResult.APPLIED) {
            return BanMutationResult.APPLIED;
        }
        if (writeResult == DatabaseManager.WriteResult.NO_CHANGE) {
            return BanMutationResult.NOT_ACTIVE;
        }
        return BanMutationResult.DATABASE_ERROR;
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

    public boolean isValidIpOrCidr(String value) {
        return IpMatcher.isValidIpOrCidr(value);
    }

    public BanIpEntry getMatchingIpBan(String ip) {
        if (ip == null) return null;
        for (BanIpEntry entry : getBanIpList()) {
            if (entry.getIp().equals(ip)) return entry;
            if (IpMatcher.cidrMatches(ip, entry.getIp())) return entry;
        }
        return null;
    }

    public boolean isIpBannedByCidr(String ip) {
        return getMatchingIpBan(ip) != null;
    }

    public boolean isBanned(String player, String reason) {
        BanEntry banEntry = getBanEntry(player);
        return banEntry != null && banEntry.getReason().contains(reason);
    }

}
