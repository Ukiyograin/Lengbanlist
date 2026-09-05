package org.leng.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 警告 controller。
 * 处理 /api/warn。
 */
public class WarnController extends WebController {

    public WarnController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/warn", this::handleWarn);
    }

    private void handleWarn(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "warn")) return;

        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String reason = json.has("reason") ? json.get("reason").getAsString() : "管理员操作";
            String staff = authManager.resolveActor(extractToken(exchange));
            final String finalStaff = staff;

            AtomicReference<String> outcome = new AtomicReference<>("ok");
            boolean completed = runSync(exchange, () -> {
                if (!plugin.getImmunityManager().canPunish(plugin.getImmunityManager().getWebOperatorWeight(), target)) {
                    outcome.set("403");
                    return;
                }
                plugin.getWarnManager().warnPlayer(target, finalStaff, reason);
            });
            if (!completed) return;
            if ("403".equals(outcome.get())) {
                WebResponse.sendError(exchange, 403, "目标权重高于操作者,无法执行");
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被警告");
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "警告失败: " + e.getMessage());
        }
    }
}