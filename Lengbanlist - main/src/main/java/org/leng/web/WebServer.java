package org.leng.web;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Web 管理面板入口：负责 HTTP server 生命周期、静态资源服务、controller 注册。
 * 业务端点全部由 {@link WebController} 子类承担。
 */
public class WebServer {

    private final Lengbanlist plugin;
    private HttpServer server;
    private ExecutorService executor;
    private AuthManager authManager;
    private boolean running;

    public WebServer(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public boolean start() {
        if (running) return true;
        try {
            String host = plugin.getConfig().getString("web.host", "0.0.0.0");
            int port = plugin.getConfig().getInt("web.port", 8080);
            String secret = plugin.getConfig().getString("web.jwt-secret", "change-this-to-a-random-secret-key");
            String username = plugin.getConfig().getString("web.admin-username", "admin");
            String password = plugin.getConfig().getString("web.admin-password", "lban123");
            if (!validateWebCredentials(secret, password)) {
                return false;
            }

            authManager = new AuthManager(secret, username, password);
            server = HttpServer.create(new InetSocketAddress(host, port), 64);
            executor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors() * 2));
            server.setExecutor(executor);

            registerControllers(server);
            // 静态资源兜底（必须最后注册：com.sun.net.httpserver 路径前缀匹配）
            server.createContext("/", this::handleStatic);

            server.start();
            running = true;
            String displayHost = host.equals("0.0.0.0") ? "本机IP" : host;
            plugin.getLogger().info("Web管理面板已启动: http://" + displayHost + ":" + port);
            if (host.equals("0.0.0.0") || host.equals("127.0.0.1")) {
                plugin.getLogger().info("绑定到 " + host + "，可从 http://localhost:" + port + " 访问");
            }
            plugin.getLogger().warning("安全提醒：Web 管理面板使用明文 HTTP，登录密码与登录令牌在网络上明文传输。请勿在不可信网络使用，并确保 web.host 未绑定到公网地址。");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Web管理面板启动失败: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            running = false;
            plugin.getLogger().info("Web管理面板已关闭");
        }
    }

    private boolean validateWebCredentials(String secret, String password) {
        boolean defaultSecret = "change-this-to-a-random-secret-key".equals(secret);
        boolean defaultPassword = "lban123".equals(password) || "admin123".equals(password);
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32 || defaultSecret) {
            plugin.getLogger().severe("Web管理面板启动失败：web.jwt-secret 必须改为至少 32 字节的随机密钥。");
            return false;
        }
        if (password == null || password.trim().isEmpty() || defaultPassword) {
            plugin.getLogger().severe("Web管理面板启动失败：web.admin-password 不能使用默认密码。");
            return false;
        }
        return true;
    }

    public boolean isRunning() {
        return running;
    }

    private void registerControllers(HttpServer server) {
        new AuthController(plugin, authManager).registerRoutes(server);
        new BanController(plugin, authManager).registerRoutes(server);
        new MuteController(plugin, authManager).registerRoutes(server);
        new WarnController(plugin, authManager).registerRoutes(server);
        new PlayerController(plugin, authManager).registerRoutes(server);
        new AuditController(plugin, authManager).registerRoutes(server);
        new ReportController(plugin, authManager).registerRoutes(server);
        new ThemeController(plugin, authManager).registerRoutes(server);
        new AdminController(plugin, authManager).registerRoutes(server);
    }

    // ============ 静态资源 ============

    private void handleStatic(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            try {
                exchange.sendResponseHeaders(204, -1);
            } catch (IOException ignored) {
            } finally {
                exchange.close();
            }
            return;
        }
        try {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/")) path = "/index.html";

            String resourcePath = "web" + path;
            InputStream stream = plugin.getResource(resourcePath);
            if (stream != null) {
                byte[] bytes = readAllBytes(stream);
                exchange.getResponseHeaders().set("Content-Type", getMimeType(path));
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }

            // 没有命中静态资源时返回 API 自描述（前端 SPA 兜底）
            JsonObject info = new JsonObject();
            info.addProperty("name", "Lengbanlist Web API");
            info.addProperty("version", plugin.getPluginVersion());
            info.addProperty("login", "POST /api/login 获取token");
            info.addProperty("usage", "在请求头加 Authorization: Bearer <token> 调用其他接口");
            sendJson(exchange, 200, info.toString());
        } catch (IOException e) {
            sendError(exchange, 500, "静态资源读取失败");
        }
    }

    private byte[] readAllBytes(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private String getMimeType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css")) return "text/css; charset=UTF-8";
        if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (path.endsWith(".cur")) return "image/x-win-bitmap";
        if (path.endsWith(".ani")) return "application/x-win-bitmap";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private void sendJson(HttpExchange exchange, int status, String json) {
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

    private void sendError(HttpExchange exchange, int status, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        sendJson(exchange, status, error.toString());
    }
}