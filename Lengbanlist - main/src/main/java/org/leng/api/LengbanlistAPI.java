package org.leng.api;

import org.bukkit.Bukkit;
import org.leng.Lengbanlist;
import org.leng.manager.BanManager;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.object.WarnEntry;

import java.util.List;

/**
 * Lengbanlist 公共 API。
 * 其他插件可通过 LengbanlistAPI.get() 访问封禁/禁言/警告/举报能力，
 * 并通过 Bukkit PluginManager 监听 {@link LengbanlistBanEvent} 等自定义事件。
 *
 * 用法示例：
 * <pre>{@code
 * LengbanlistAPI api = LengbanlistAPI.get();
 * if (api != null && api.isPlayerBanned(target)) { ... }
 * api.banPlayer(target, "30d", "spam", "MyPlugin");
 * }</pre>
 */
public final class LengbanlistAPI {

    private static LengbanlistAPI instance;

    private final Lengbanlist plugin;

    LengbanlistAPI(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    /**
     * 获取 API 实例。插件未加载时返回 null。
     */
    public static LengbanlistAPI get() {
        return instance;
    }

    static void register(Lengbanlist plugin) {
        instance = new LengbanlistAPI(plugin);
    }

    static void unregister() {
        instance = null;
    }

    public Lengbanlist getPlugin() {
        return plugin;
    }

    // ============ 封禁 ============

    public boolean banPlayer(String target, String staff, long durationMillis, String reason) {
        return banPlayer(target, staff, durationMillis, reason, false);
    }

    public boolean banPlayer(String target, String staff, long durationMillis, String reason, boolean silent) {
        BanEntry entry = new BanEntry(target, staff, durationMillis, reason, false);
        BanManager.BanMutationResult result = plugin.getBanManager().tryBanPlayer(entry, silent);
        return result == BanManager.BanMutationResult.APPLIED;
    }

    public boolean banIp(String ip, String staff, long durationMillis, String reason) {
        return banIp(ip, staff, durationMillis, reason, false);
    }

    public boolean banIp(String ip, String staff, long durationMillis, String reason, boolean silent) {
        BanIpEntry entry = new BanIpEntry(ip, staff, durationMillis, reason, false);
        BanManager.BanMutationResult result = plugin.getBanManager().tryBanIp(entry, silent);
        return result == BanManager.BanMutationResult.APPLIED;
    }

    public boolean unbanPlayer(String target, String actor) {
        return plugin.getBanManager().tryUnbanPlayer(target, actor, false) == BanManager.BanMutationResult.APPLIED;
    }

    public boolean unbanIp(String ip, String actor) {
        return plugin.getBanManager().tryUnbanIp(ip, actor, false) == BanManager.BanMutationResult.APPLIED;
    }

    public boolean isPlayerBanned(String target) {
        return plugin.getBanManager().isPlayerBanned(target);
    }

    public boolean isIpBanned(String ip) {
        return plugin.getBanManager().isIpBanned(ip);
    }

    public BanEntry getBanEntry(String target) {
        return plugin.getBanManager().getBanEntry(target);
    }

    public BanIpEntry getBanIpEntry(String ip) {
        return plugin.getBanManager().getBanIpEntry(ip);
    }

    public List<BanEntry> getBanList() {
        return plugin.getBanManager().getBanList();
    }

    public List<BanIpEntry> getBanIpList() {
        return plugin.getBanManager().getBanIpList();
    }

    // ============ 禁言 ============

    public boolean mutePlayer(String target, String staff, long durationMillis, String reason) {
        MuteEntry entry = new MuteEntry(target, staff, durationMillis, reason);
        try {
            return plugin.getMuteManager().mutePlayer(entry) != null;
        } catch (Exception e) {
            plugin.getLogger().warning("API mutePlayer 失败: " + e.getMessage());
            return false;
        }
    }

    public boolean unmutePlayer(String target, String actor) {
        try {
            plugin.getMuteManager().unmutePlayer(target, actor);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("API unmutePlayer 失败: " + e.getMessage());
            return false;
        }
    }

    public boolean isPlayerMuted(String target) {
        return plugin.getMuteManager().isPlayerMuted(target);
    }

    public List<MuteEntry> getMuteList() {
        return plugin.getMuteManager().getMuteList();
    }

    // ============ 警告 ============

    public boolean warnPlayer(String player, String staff, String reason) {
        plugin.getWarnManager().warnPlayer(player, staff, reason);
        return true;
    }

    public boolean unwarnPlayer(String target, int warnId, String actor) {
        org.bukkit.command.CommandSender sender = Bukkit.getPlayerExact(actor);
        if (sender == null) {
            sender = Bukkit.getConsoleSender();
        }
        return plugin.getWarnManager().unwarnPlayer(target, warnId, sender);
    }

    public int countActiveWarnings(String target) {
        return plugin.getWarnManager().countActiveWarnings(target);
    }

    public List<WarnEntry> getAllWarnings(String target) {
        return plugin.getWarnManager().getAllWarnings(target);
    }

    public List<WarnEntry> getActiveWarnings(String target) {
        return plugin.getWarnManager().getActiveWarnings(target);
    }

    // ============ 举报 ============

    public void addReport(ReportEntry report) {
        plugin.getReportManager().addReport(report);
    }

    public ReportEntry getReport(String id) {
        return plugin.getReportManager().getReport(id);
    }

    public List<ReportEntry> getPendingReports() {
        return plugin.getReportManager().getPendingReports();
    }

    public int getPendingReportCount() {
        return plugin.getReportManager().getPendingReportCount();
    }
}