package org.leng.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.utils.TimeUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 概览信息 controller：面板首页的服务器统计卡 + 最近封禁。
 * 处理 GET /api/stats。
 *
 * <p>注意：此端点曾在 D1 WebServer 拆分时被误删，前端 index.html 仍在调用，
 * 导致"插件版本/数据库状态/数据库类型"等统计卡无法显示。本类为恢复实现。
 */
public class StatsController extends WebController {

    public StatsController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/stats", this::handleStats);
    }

    private void handleStats(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 GET");
            return;
        }
        if (!requireAuth(exchange)) return;

        // 在线人数/最大人数必须在主线程读（Bukkit API 非线程安全）
        AtomicInteger onlineRef = new AtomicInteger(0);
        AtomicInteger maxRef = new AtomicInteger(0);
        boolean completed = runSync(exchange, () -> {
            onlineRef.set(plugin.getServer().getOnlinePlayers().size());
            maxRef.set(plugin.getServer().getMaxPlayers());
        });
        if (!completed) return;

        List<BanEntry> bans = plugin.getBanManager().getBanList();
        List<BanIpEntry> ipBans = plugin.getBanManager().getBanIpList();
        long now = System.currentTimeMillis();
        int activeBans = 0;
        for (BanEntry b : bans) {
            if (b.isActive() && b.getTime() > now) activeBans++;
        }

        JsonObject stats = new JsonObject();
        stats.addProperty("plugin_version", plugin.getPluginVersion());
        stats.addProperty("online_players", onlineRef.get());
        stats.addProperty("max_players", maxRef.get());
        stats.addProperty("database_status", plugin.getDatabaseManager().isHealthy() ? "正常" : "异常");
        stats.addProperty("database_type", plugin.getDatabaseManager().getDatabaseProductName());
        stats.addProperty("total_bans", bans.size() + ipBans.size());
        stats.addProperty("active_bans", activeBans);
        stats.addProperty("ip_bans", ipBans.size());
        stats.addProperty("mutes", plugin.getMuteManager().getMuteList().size());
        stats.addProperty("warnings", plugin.getWarnManager().getWarnedPlayers().size());
        stats.addProperty("pending_reports", plugin.getReportManager().getPendingReportCount());

        JsonArray recentBans = new JsonArray();
        for (BanEntry entry : plugin.getDatabaseManager().getRecentBans(5)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("target", entry.getTarget());
            obj.addProperty("staff", entry.getStaff());
            obj.addProperty("reason", entry.getReason());
            obj.addProperty("end_time", TimeUtils.timestampToReadable(entry.getTime()));
            obj.addProperty("remaining", TimeUtils.getRemainingTime(entry.getTime()));
            obj.addProperty("active", entry.isActive() && entry.getTime() > now);
            obj.addProperty("auto", entry.isAuto());
            recentBans.add(obj);
        }
        stats.add("recent_bans", recentBans);

        WebResponse.sendJson(exchange, 200, stats.toString());
    }
}