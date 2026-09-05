package org.leng.web;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import org.leng.Lengbanlist;
import org.leng.manager.BanManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 响应/请求工具方法,供各 controller 复用。
 */
public final class WebResponse {

    private WebResponse() {}

    public static void sendJson(HttpExchange exchange, int status, String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
        } finally {
            exchange.close();
        }
    }

    public static void sendError(HttpExchange exchange, int status, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        sendJson(exchange, status, error.toString());
    }

    public static void handleOptions(HttpExchange exchange) {
        try {
            exchange.sendResponseHeaders(204, -1);
        } catch (IOException ignored) {
        } finally {
            exchange.close();
        }
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        int maxBytes = 1024 * 1024;
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("请求体超过 1MB 上限");
                }
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    public static void applyCorsHeaders(HttpExchange exchange, Lengbanlist plugin) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.trim().isEmpty()) return;
        for (String allowed : plugin.getConfig().getStringList("web.allowed-origins")) {
            if (!allowed.trim().isEmpty() && origin.equalsIgnoreCase(allowed.trim())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                return;
            }
        }
        exchange.getResponseHeaders().set("Vary", "Origin");
    }

    /** 发送 BanMutationResult 失败响应,APPLIED 返回 false,其它结果返回 true 表示已发送错误 */
    public static boolean sendMutationFailure(HttpExchange exchange, BanManager.BanMutationResult result, String target) {
        if (result == BanManager.BanMutationResult.APPLIED) return false;
        switch (result) {
            case NOT_ACTIVE:
                sendError(exchange, 404, target + " 未被封禁或状态已变化");
                break;
            case STATE_CHANGED:
                sendError(exchange, 409, "数据状态已变化，请刷新后重试");
                break;
            case REJECTED_PRIVATE_OR_RESERVED_IP:
                sendError(exchange, 400, "私有或保留 IP 不允许执行此操作");
                break;
            default:
                sendError(exchange, 500, "数据库操作失败，操作未完成");
        }
        return true;
    }
}