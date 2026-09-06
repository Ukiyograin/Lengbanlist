package org.leng.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.manager.ThemeManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * 主题 controller：背景图片配置、上传、按钮显隐。
 * 处理 /api/theme、/api/theme/upload、/api/theme/file/<filename>。
 */
public class ThemeController extends WebController {

    public ThemeController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/theme", this::handleTheme);
        server.createContext("/api/theme/upload", this::handleThemeUpload);
        // /api/theme/file 必须注册在 /api/theme 之后（前者是更具体的路径）
        server.createContext("/api/theme/file", this::handleThemeFile);
    }

    private void handleTheme(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        ThemeManager theme = plugin.getThemeManager();
        try {
            if ("GET".equals(exchange.getRequestMethod())) {
                JsonObject result = new JsonObject();
                result.addProperty("background_type", theme.getBackgroundType());
                result.addProperty("background_url", theme.getBackgroundUrl());
                result.addProperty("background_file", theme.getBackgroundFile());
                result.addProperty("served_url", servedBackgroundUrl(theme));
                JsonArray hidden = new JsonArray();
                for (String b : theme.getHiddenButtons()) hidden.add(b);
                result.add("hidden_buttons", hidden);
                JsonArray allButtons = new JsonArray();
                for (String b : ThemeManager.ALL_BUTTONS) allButtons.add(b);
                result.add("all_buttons", allButtons);
                WebResponse.sendJson(exchange, 200, result.toString());
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
                if (json.has("background_url")) {
                    theme.setBackgroundUrl(json.get("background_url").getAsString());
                }
                // 应用已上传的背景文件（配合 /api/theme/upload 使用）
                if (json.has("background_file")) {
                    String file = json.get("background_file").getAsString();
                    theme.setBackgroundFile(file);
                }
                if (json.has("reset_background") && json.get("reset_background").getAsBoolean()) {
                    theme.resetBackground();
                }
                if (json.has("hidden_buttons")) {
                    Set<String> buttons = new HashSet<>();
                    for (var el : json.getAsJsonArray("hidden_buttons")) {
                        buttons.add(el.getAsString());
                    }
                    theme.setHiddenButtons(buttons);
                }
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "主题已更新");
                WebResponse.sendJson(exchange, 200, result.toString());
                return;
            }
            WebResponse.sendError(exchange, 405, "仅支持 GET/POST");
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "请求格式错误: " + e.getMessage());
        }
    }

    private void handleThemeUpload(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }

        // 从 Content-Disposition 头提取原始文件名
        String originalFilename = null;
        String disposition = exchange.getRequestHeaders().getFirst("Content-Disposition");
        if (disposition != null) {
            int idx = disposition.indexOf("filename=");
            if (idx > 0) {
                originalFilename = disposition.substring(idx + 9).trim().replace("\"", "");
            }
        }

        try {
            int maxBytes = (int) ThemeManager.MAX_UPLOAD_BYTES;
            byte[] buf = new byte[8192];
            int total = 0;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream is = exchange.getRequestBody()) {
                int n;
                while ((n = is.read(buf)) > 0) {
                    total += n;
                    if (total > maxBytes) {
                        WebResponse.sendError(exchange, 413, "文件超过 5MB 上限");
                        return;
                    }
                    out.write(buf, 0, n);
                }
            }
            String saved = plugin.getThemeManager().saveBackgroundUpload(out.toByteArray(), originalFilename);
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("filename", saved);
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            WebResponse.sendError(exchange, 400, "上传失败: " + e.getMessage());
        }
    }

    /** 服务已上传的背景图。/api/theme/file/<filename> */
    private void handleThemeFile(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getPath();
            String filename = path.substring("/api/theme/file/".length());
            // 防路径穿越:只允许 [a-zA-Z0-9.-]+
            if (filename.isEmpty() || !filename.matches("[a-zA-Z0-9.\\-]+")) {
                WebResponse.sendError(exchange, 400, "非法文件名");
                return;
            }
            ThemeManager theme = plugin.getThemeManager();
            java.io.File file = new java.io.File(theme.getWebAssetsDir(), filename);
            String assetsRoot = theme.getWebAssetsDir().getAbsolutePath();
            if (!file.exists() || !file.getAbsolutePath().startsWith(assetsRoot)) {
                WebResponse.sendError(exchange, 404, "文件不存在");
                return;
            }
            String contentType = filename.endsWith(".png") ? "image/png"
                    : filename.endsWith(".webp") ? "image/webp"
                    : filename.endsWith(".gif") ? "image/gif"
                    : "image/jpeg";
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException e) {
            WebResponse.sendError(exchange, 500, "读取失败");
        } finally {
            exchange.close();
        }
    }

    private String servedBackgroundUrl(ThemeManager theme) {
        switch (theme.getBackgroundType()) {
            case "url":
                return theme.getBackgroundUrl();
            case "upload":
                return "/api/theme/file/" + theme.getBackgroundFile();
            default:
                return "";
        }
    }
}