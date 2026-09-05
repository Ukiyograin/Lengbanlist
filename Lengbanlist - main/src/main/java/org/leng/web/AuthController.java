package org.leng.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;

import java.io.IOException;

/**
 * 登录/登出 controller。
 */
public class AuthController extends WebController {

    public AuthController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/login", this::handleLogin);
        server.createContext("/api/logout", this::handleLogout);
    }

    private void handleLogin(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!checkRateLimit(exchange)) return;
        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
            String token = authManager.login(json.get("username").getAsString(), json.get("password").getAsString());
            if (token == null) {
                WebResponse.sendError(exchange, 401, "用户名或密码错误");
                return;
            }
            WebResponse.sendJson(exchange, 200, gson.toJson(new LoginResponse(token, json.get("username").getAsString())));
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "请求格式错误");
        }
    }

    private void handleLogout(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;
        String token = extractToken(exchange);
        authManager.revokeToken(token);
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("message", "已退出登录");
        WebResponse.sendJson(exchange, 200, result.toString());
    }

    private static class LoginResponse {
        private final String token;
        private final String username;
        LoginResponse(String token, String username) {
            this.token = token;
            this.username = username;
        }
    }
}