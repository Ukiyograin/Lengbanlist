package org.leng.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.object.AuditEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志 controller：/api/audit
 */
public class AuditController extends WebController {

    public AuditController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/audit", this::handleAudit);
    }

    private void handleAudit(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        Map<String, String> params = parseQuery(exchange);
        int limit;
        try {
            limit = Math.min(500, Math.max(1, Integer.parseInt(params.getOrDefault("limit", "100"))));
        } catch (NumberFormatException e) {
            limit = 100;
        }

        String actor = params.get("actor");
        String action = params.get("action");

        // actor 过滤走专用方法走索引（如果实现里有），否则用通用查询再客户端过滤
        List<AuditEntry> entries;
        if (actor != null && !actor.isEmpty()) {
            entries = plugin.getAuditManager().getLogsByActor(actor, limit);
        } else {
            entries = plugin.getAuditManager().getLogs("", limit);
        }
        JsonArray logs = new JsonArray();
        for (AuditEntry e : entries) {
            if (action != null && !action.isEmpty() && !e.action().contains(action)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id());
            o.addProperty("timestamp", e.timestamp());
            o.addProperty("actor", e.actor());
            o.addProperty("action", e.action());
            o.addProperty("target", e.target());
            o.addProperty("reason", e.reason());
            o.addProperty("success", e.success());
            logs.add(o);
        }
        JsonObject result = new JsonObject();
        result.add("logs", logs);
        result.addProperty("total", logs.size());
        WebResponse.sendJson(exchange, 200, result.toString());
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