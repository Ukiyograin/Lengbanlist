package org.leng.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.WarnEntry;
import org.leng.utils.TimeUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 玩家管理 controller：玩家列表/在线列表/踢出/历史记录。
 * /api/players /api/online /api/kick /api/history
 */
public class PlayerController extends WebController {

    public PlayerController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/players", this::handlePlayers);
        server.createContext("/api/online", this::handleOnline);
        server.createContext("/api/kick", this::handleKick);
        server.createContext("/api/history", this::handleHistory);
    }

    private void handlePlayers(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;
        try {
            List<BanEntry> bans = plugin.getBanManager().getBanList();
            List<BanIpEntry> ipBans = plugin.getBanManager().getBanIpList();
            List<MuteEntry> mutes = plugin.getMuteManager().getMuteList();

            JsonArray banArr = new JsonArray();
            for (BanEntry e : bans) {
                JsonObject o = new JsonObject();
                o.addProperty("target", e.target());
                o.addProperty("staff", e.staff());
                o.addProperty("end_time", e.time());
                o.addProperty("reason", e.reason());
                o.addProperty("active", e.isActive());
                banArr.add(o);
            }
            JsonArray ipBanArr = new JsonArray();
            for (BanIpEntry e : ipBans) {
                JsonObject o = new JsonObject();
                o.addProperty("ip", e.ip());
                o.addProperty("staff", e.staff());
                o.addProperty("end_time", e.time());
                o.addProperty("reason", e.reason());
                o.addProperty("active", e.isActive());
                ipBanArr.add(o);
            }
            JsonArray muteArr = new JsonArray();
            for (MuteEntry e : mutes) {
                JsonObject o = new JsonObject();
                o.addProperty("target", e.target());
                o.addProperty("staff", e.staff());
                o.addProperty("end_time", e.time());
                o.addProperty("reason", e.reason());
                muteArr.add(o);
            }
            JsonObject result = new JsonObject();
            result.add("bans", banArr);
            result.add("ip_bans", ipBanArr);
            result.add("mutes", muteArr);
            result.addProperty("total", bans.size() + ipBans.size() + mutes.size());
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 500, "查询失败");
        }
    }

    private void handleOnline(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        AtomicReference<JsonArray> playersRef = new AtomicReference<>(new JsonArray());
        boolean completed = runSync(exchange, () -> {
            JsonArray players = new JsonArray();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", player.getName());
                obj.addProperty("uuid", player.getUniqueId().toString());
                obj.addProperty("ping", player.getPing());
                players.add(obj);
            }
            playersRef.set(players);
        });
        if (!completed) return;

        JsonArray players = playersRef.get();
        JsonObject result = new JsonObject();
        result.add("players", players);
        result.addProperty("total", players.size());
        WebResponse.sendJson(exchange, 200, result.toString());
    }

    private void handleKick(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "kick")) return;

        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String reason = json.has("reason") ? json.get("reason").getAsString() : "管理员操作";
            final String finalReason = reason;
            AtomicReference<String> outcome = new AtomicReference<>("ok");
            String staff = authManager.resolveActor(extractToken(exchange));
            final String finalStaff = staff;
            boolean completed = runSync(exchange, () -> {
                Player player = plugin.getServer().getPlayerExact(target);
                if (player == null) {
                    outcome.set("404");
                    return;
                }
                if (!plugin.getImmunityManager().canPunish(plugin.getImmunityManager().getWebOperatorWeight(), target)) {
                    outcome.set("403");
                    return;
                }
                player.kickPlayer(finalReason);
                plugin.getAuditManager().log("踢出", finalStaff, target, reason);
            });
            if (!completed) return;
            if ("404".equals(outcome.get())) {
                WebResponse.sendError(exchange, 404, "玩家 " + target + " 不在线");
                return;
            }
            if ("403".equals(outcome.get())) {
                WebResponse.sendError(exchange, 403, "目标权重高于操作者,无法执行");
                return;
            }
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被踢出");
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "踢出失败: " + e.getMessage());
        }
    }

    private void handleHistory(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            Map<String, String> params = parseQuery(exchange);
            String target = params.get("target");
            if (target == null || target.isEmpty()) {
                WebResponse.sendError(exchange, 400, "缺少 target 参数");
                return;
            }
            List<BanEntry> banHistory = plugin.getDatabaseManager().getBansByPlayer(target);
            List<WarnEntry> warnings = plugin.getWarnManager().getAllWarnings(target);

            JsonArray banArr = new JsonArray();
            for (BanEntry b : banHistory) {
                JsonObject o = new JsonObject();
                o.addProperty("type", "ban");
                o.addProperty("staff", b.staff());
                o.addProperty("end_time", b.time());
                o.addProperty("reason", b.reason());
                o.addProperty("active", b.active());
                o.addProperty("duration", TimeUtils.formatDuration(b.time() - System.currentTimeMillis()));
                banArr.add(o);
            }
            JsonArray warnArr = new JsonArray();
            for (WarnEntry w : warnings) {
                JsonObject o = new JsonObject();
                o.addProperty("type", "warn");
                o.addProperty("id", w.id());
                o.addProperty("staff", w.staff());
                o.addProperty("time", w.time());
                o.addProperty("reason", w.reason());
                o.addProperty("revoked", w.revoked());
                warnArr.add(o);
            }
            JsonObject result = new JsonObject();
            result.addProperty("target", target);
            result.add("bans", banArr);
            result.add("warnings", warnArr);
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 500, "查询失败");
        }
    }

    private static Map<String, String> parseQuery(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                try {
                    params.put(java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                            java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
                } catch (java.io.UnsupportedEncodingException ignored) {}
            }
        }
        return params;
    }
}