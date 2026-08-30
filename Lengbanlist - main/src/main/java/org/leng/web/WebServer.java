package org.leng.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.BanManager;
import org.leng.manager.ModelManager;
import org.leng.object.AuditEntry;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.object.WarnEntry;
import org.leng.utils.TimeUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

public class WebServer {

    private final Lengbanlist plugin;
    private final Gson gson = new Gson();
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

            server.createContext("/api/login", this::handleLogin);
            server.createContext("/api/logout", this::handleLogout);
            server.createContext("/api/players", this::handlePlayers);
            server.createContext("/api/online", this::handleOnline);
            server.createContext("/api/ban", this::handleBan);
            server.createContext("/api/unban", this::handleUnban);
            server.createContext("/api/kick", this::handleKick);
            server.createContext("/api/stats", this::handleStats);
            server.createContext("/api/history", this::handleHistory);
            server.createContext("/api/bans", this::handleBanList);
            server.createContext("/api/ipbans", this::handleIpBanList);
            server.createContext("/api/mutes", this::handleMuteList);
            server.createContext("/api/reports", this::handleReports);
            server.createContext("/api/mute", this::handleMute);
            server.createContext("/api/unmute", this::handleUnmute);
            server.createContext("/api/warn", this::handleWarn);
            server.createContext("/api/report/action", this::handleReportAction);
            server.createContext("/api/audit", this::handleAudit);
            server.createContext("/api/reload", this::handleReload);
            server.createContext("/api/broadcast", this::handleBroadcast);
            server.createContext("/", this::handleRoot);

            server.start();
            running = true;
            String displayHost = host.equals("0.0.0.0") ? "本机IP" : host;
            plugin.getLogger().info("Web管理面板已启动: http://" + displayHost + ":" + port);
            if (host.equals("0.0.0.0")) {
                plugin.getLogger().info("绑定到 0.0.0.0，可从 http://本机IP:" + port + " 访问（如 http://localhost:" + port + "）");
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


    private static class AuthManager {

        private final String secret;
        private final String username;
        private final String passwordHash;
        private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();
        private static final long TOKEN_EXP_MS = 86400000L;

        AuthManager(String secret, String username, String password) {
            this.secret = secret;
            this.username = username;
            this.passwordHash = sha256(password);
        }

        String login(String user, String pass) {
            if (!username.equals(user) || !sha256(pass).equals(passwordHash)) return null;
            return createToken(username);
        }

        boolean validateToken(String token) {
            if (token == null || revokedTokens.contains(token)) return false;
            return parseToken(token) != null;
        }

        void revokeToken(String token) {
            if (token != null && !token.isEmpty()) {
                revokedTokens.add(token);
            }
        }

        String getUsernameFromToken(String token) {
            JsonObject payload = parseToken(token);
            return payload != null ? payload.get("sub").getAsString() : null;
        }

        /** 解析当前会话操作者：正常返回登录用户名；默认 admin 等非玩家身份统一记为 CONSOLE。 */
        String resolveActor(String token) {
            String name = getUsernameFromToken(token);
            if (name == null || name.trim().isEmpty()) {
                return "CONSOLE";
            }
            return "admin".equals(name) ? "CONSOLE" : name.trim();
        }

        private String createToken(String subject) {
            JsonObject header = new JsonObject();
            header.addProperty("alg", "HS256");
            header.addProperty("typ", "JWT");

            JsonObject payload = new JsonObject();
            long now = System.currentTimeMillis() / 1000;
            payload.addProperty("sub", subject);
            payload.addProperty("iat", now);
            payload.addProperty("exp", now + TOKEN_EXP_MS / 1000);

            String encodedHeader = b64url(header.toString());
            String encodedPayload = b64url(payload.toString());
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = hmacSha256(signingInput, secret);

            return signingInput + "." + signature;
        }

        private JsonObject parseToken(String token) {
            try {
                String[] parts = token.split("\\.");
                if (parts.length != 3) return null;
                if (!hmacSha256(parts[0] + "." + parts[1], secret).equals(parts[2])) return null;

                String json = new String(Base64.getUrlDecoder().decode(parts[1]));
                JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
                if (System.currentTimeMillis() / 1000 > payload.get("exp").getAsLong()) return null;
                return payload;
            } catch (Exception e) {
                return null;
            }
        }

        private static String hmacSha256(String data, String key) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return b64url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static String sha256(String s) {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) hex.append(String.format("%02x", b));
                return hex.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static String b64url(String data) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
        }

        private static String b64url(byte[] data) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        }

    }


    private static class RateLimiter {

        private final ConcurrentHashMap<String, AtomicLongArray> requests = new ConcurrentHashMap<>();
        private static final int MAX_REQUESTS = 60;
        private static final long WINDOW_MS = 60000L;

        boolean isRateLimited(String ip) {
            if (requests.size() > 1024) {
                cleanup();
            }
            long now = System.currentTimeMillis();
            AtomicLongArray window = requests.compute(ip, (key, val) -> {
                if (val == null || now - val.get(0) > WINDOW_MS) {
                    return new AtomicLongArray(new long[]{now, 1});
                }
                val.getAndIncrement(1);
                return val;
            });
            return window.get(1) > MAX_REQUESTS;
        }

        void cleanup() {
            long cutoff = System.currentTimeMillis() - WINDOW_MS;
            requests.entrySet().removeIf(e -> e.getValue().get(0) < cutoff);
        }

    }

    private final RateLimiter rateLimiter = new RateLimiter();

    private boolean checkRateLimit(HttpExchange exchange) {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (rateLimiter.isRateLimited(ip)) {
            sendError(exchange, 429, "请求过于频繁，请稍后再试");
            return false;
        }
        return true;
    }


    private String extractToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        return null;
    }

    private boolean requireFeature(HttpExchange exchange, String feature) {
        if (!plugin.isFeatureEnabled(feature)) {
            sendError(exchange, 403, "此功能已被管理员禁用");
            return false;
        }
        return true;
    }

    private boolean runSync(HttpExchange exchange, Runnable task) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean timedOut = new AtomicBoolean(false);
        org.leng.utils.SchedulerUtils.SchedulerTask scheduled = org.leng.utils.SchedulerUtils.runTask(plugin, () -> {
            try {
                // 主线程仍可能在 timeout 之后执行 task.run(); 用标志位让它做一个无害的 no-op
                if (timedOut.get()) {
                    return;
                }
                task.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                timedOut.set(true);
                // 主线程任务一旦排队无法真正取消,只能依靠上面的标志位做 no-op
                sendError(exchange, 504, "主线程繁忙，操作可能已在后台执行，请在管理列表确认结果后再决定是否重试，避免重复提交");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timedOut.set(true);
            sendError(exchange, 500, "操作被中断");
            return false;
        }
        if (error.get() != null) {
            sendError(exchange, 500, "操作失败");
            return false;
        }
        return true;
    }

    private boolean requireAuth(HttpExchange exchange) {
        if (!checkRateLimit(exchange)) return false;
        String token = extractToken(exchange);
        if (token == null || !authManager.validateToken(token)) {
            sendError(exchange, 401, "未授权");
            return false;
        }
        return true;
    }

    private void sendError(HttpExchange exchange, int status, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        sendJson(exchange, status, error.toString());
    }

    private void sendJson(HttpExchange exchange, int status, String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            applyCorsHeaders(exchange);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException e) {
            plugin.getLogger().warning("Web响应写入失败: " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private void applyCorsHeaders(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.trim().isEmpty()) {
            return;
        }
        // 移除以 Host 反射 Origin 的"宽松同源"逻辑,只允许 config 中显式声明的 origin。
        // 否则非浏览器客户端通过伪造 Host 头即可绕过 CORS,即便有 JWT 兜底也是 misconfig。
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

    private void handleOptions(HttpExchange exchange) {
        applyCorsHeaders(exchange);
        try {
            exchange.sendResponseHeaders(204, -1);
        } catch (IOException e) {

        } finally {
            exchange.close();
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        int maxBytes = 1024 * 1024;
        try (InputStream is = exchange.getRequestBody(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("请求体超过 1MB 限制");
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            try {
                params.put(URLDecoder.decode(pair[0], "UTF-8"),
                        pair.length > 1 ? URLDecoder.decode(pair[1], "UTF-8") : "");
            } catch (UnsupportedEncodingException e) {

            }
        }
        return params;
    }


    private void handleLogin(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!checkRateLimit(exchange)) return;
        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String token = authManager.login(json.get("username").getAsString(), json.get("password").getAsString());
            if (token == null) {
                sendError(exchange, 401, "用户名或密码错误");
                return;
            }
            sendJson(exchange, 200, gson.toJson(new LoginResponse(token, json.get("username").getAsString())));
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "请求格式错误");
        }
    }

    private static class LoginResponse {

        private final String token;
        private final String username;

        LoginResponse(String token, String username) {
            this.token = token;
            this.username = username;
        }

    }

    private void handleLogout(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!checkRateLimit(exchange)) return;
        String token = extractToken(exchange);
        if (token != null) {
            authManager.revokeToken(token);
        }
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("message", "已注销，令牌已吊销");
        sendJson(exchange, 200, result.toString());
    }

    private void handlePlayers(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String q = params.get("q");
        if (q == null || q.isEmpty()) {
            sendError(exchange, 400, "缺少查询参数 q");
            return;
        }

        try {
            JsonObject result = new JsonObject();
            result.addProperty("query", q);

            if (q.contains(".")) {
                result.addProperty("type", "ip");
                List<String> players = plugin.getDatabaseManager().getPlayersByIpFromHistory(q);
                result.add("players", gson.toJsonTree(players));
            } else {
                result.addProperty("type", "player");
                result.addProperty("player", q);

                Set<String> associated = plugin.getIpAssociationManager().getAllAssociatedPlayerNames(q);
                result.add("associated_players", gson.toJsonTree(new ArrayList<>(associated)));

                List<String[]> ipHistory = plugin.getIpAssociationManager().getPlayerIps(q);
                JsonArray ips = new JsonArray();
                for (String[] record : ipHistory) {
                    JsonObject ipObj = new JsonObject();
                    ipObj.addProperty("ip", record[0]);
                    ipObj.addProperty("first_seen", TimeUtils.timestampToReadable(Long.parseLong(record[1])));
                    ipObj.addProperty("last_seen", TimeUtils.timestampToReadable(Long.parseLong(record[2])));
                    ips.add(ipObj);
                }
                result.add("ips", ips);
            }
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendError(exchange, 500, "查询失败");
        }
    }

    private void handleOnline(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
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
        sendJson(exchange, 200, result.toString());
    }

    private void handleKick(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "kick")) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
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
                // 审计写入必须在 runSync 内与踢人同步,防止 504 后审计缺失
                plugin.getAuditManager().log("踢出", finalStaff, target, reason);
            });
            if (!completed) return;
            if ("404".equals(outcome.get())) {
                sendError(exchange, 404, "玩家 " + target + " 不在线");
                return;
            }
            if ("403".equals(outcome.get())) {
                sendError(exchange, 403, "目标权重高于操作者，无法执行");
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被踢出");
            sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "踢出失败: " + e.getMessage());
        }
    }

    private void handleHistory(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String player = params.get("player");
        if (player == null || player.isEmpty()) {
            sendError(exchange, 400, "缺少参数 player");
            return;
        }

        try {
            JsonObject result = new JsonObject();
            result.addProperty("player", player);

            JsonArray bans = new JsonArray();
            for (BanEntry entry : plugin.getDatabaseManager().getBansByPlayer(player)) {
                JsonObject obj = new JsonObject();
                obj.addProperty("target", entry.getTarget());
                obj.addProperty("staff", entry.getStaff());
                obj.addProperty("reason", entry.getReason());
                obj.addProperty("end_time", TimeUtils.timestampToReadable(entry.getTime()));
                obj.addProperty("active", entry.isActive());
                obj.addProperty("auto", entry.isAuto());
                bans.add(obj);
            }
            result.add("bans", bans);

            JsonArray mutes = new JsonArray();
            for (MuteEntry entry : plugin.getDatabaseManager().getMutesByPlayer(player)) {
                JsonObject obj = new JsonObject();
                obj.addProperty("staff", entry.getStaff());
                obj.addProperty("reason", entry.getReason());
                obj.addProperty("end_time", TimeUtils.timestampToReadable(entry.getTime()));
                mutes.add(obj);
            }
            result.add("mutes", mutes);

            JsonArray warnings = new JsonArray();
            for (WarnEntry entry : plugin.getWarnManager().getAllWarnings(player)) {
                JsonObject obj = new JsonObject();
                obj.addProperty("staff", entry.getStaff());
                obj.addProperty("reason", entry.getReason());
                obj.addProperty("warn_time", TimeUtils.timestampToReadable(entry.getTime()));
                obj.addProperty("revoked", entry.isRevoked());
                warnings.add(obj);
            }
            result.add("warnings", warnings);

            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendError(exchange, 500, "查询历史失败");
        }
    }

    private void handleBan(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String duration = json.has("duration") ? json.get("duration").getAsString() : "7d";
            String reason = json.has("reason") ? json.get("reason").getAsString() : "管理员操作";

            String staff = authManager.resolveActor(extractToken(exchange));
            final String finalStaff = staff;

            long durationMs = TimeUtils.parseTime(duration);
            if (durationMs <= 0) durationMs = TimeUtils.daysToMillis(7);
            long endTime = TimeUtils.calculateEndTime(durationMs);

            String feature = target.contains(".") ? "ban-ip" : "ban";
            if (!requireFeature(exchange, feature)) return;

            // 校验 IP 合法性，避免非法字符串以"IP 封禁"名义入库
            if (target.contains(".") && !org.leng.utils.IpMatcher.isValidIpOrCidrOrWildcard(target)) {
                sendError(exchange, 400, "无效的 IP 或 CIDR 格式");
                return;
            }

            AtomicReference<Boolean> permissionDenied = new AtomicReference<>(false);
            AtomicReference<BanManager.BanMutationResult> mutationResult =
                    new AtomicReference<>(BanManager.BanMutationResult.DATABASE_ERROR);
            boolean completed = runSync(exchange, () -> {
                if (!plugin.getImmunityManager().canPunishTarget(plugin.getImmunityManager().getWebOperatorWeight(), target)) {
                    permissionDenied.set(true);
                    return;
                }
                if (target.contains(".")) {
                    mutationResult.set(plugin.getBanManager().tryBanIp(
                            new BanIpEntry(target, finalStaff, endTime, reason, false), false));
                } else {
                    mutationResult.set(plugin.getBanManager().tryBanPlayer(
                            new BanEntry(target, finalStaff, endTime, reason, false), false));
                }
            });
            if (!completed) return;
            if (permissionDenied.get()) {
                sendError(exchange, 403, "目标权重高于操作者，无法执行");
                return;
            }
            if (sendMutationFailure(exchange, mutationResult.get(), target)) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被封禁，时长: " + TimeUtils.formatDuration(durationMs));
            sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "封禁失败: " + e.getMessage());
        }
    }

    private void handleUnban(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "unban")) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String actor = authManager.resolveActor(extractToken(exchange));
            final String finalActor = actor;

            if (target.contains(".") && !org.leng.utils.IpMatcher.isValidIpOrCidrOrWildcard(target)) {
                sendError(exchange, 400, "无效的 IP 或 CIDR 格式");
                return;
            }

            AtomicReference<Boolean> permissionDenied = new AtomicReference<>(false);
            AtomicReference<BanManager.BanMutationResult> mutationResult =
                    new AtomicReference<>(BanManager.BanMutationResult.DATABASE_ERROR);
            boolean completed = runSync(exchange, () -> {
                if (!plugin.getImmunityManager().canPunishTarget(plugin.getImmunityManager().getWebOperatorWeight(), target)) {
                    permissionDenied.set(true);
                    return;
                }
                if (target.contains(".")) {
                    mutationResult.set(plugin.getBanManager().tryUnbanIp(target, finalActor, false));
                } else {
                    mutationResult.set(plugin.getBanManager().tryUnbanPlayer(target, finalActor, false));
                }
            });
            if (!completed) return;
            if (permissionDenied.get()) {
                sendError(exchange, 403, "目标权重高于操作者，无法执行");
                return;
            }
            if (sendMutationFailure(exchange, mutationResult.get(), target)) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被解封");
            sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "解封失败: " + e.getMessage());
        }
    }

    private boolean sendMutationFailure(HttpExchange exchange, BanManager.BanMutationResult result, String target) {
        if (result == BanManager.BanMutationResult.APPLIED) return false;
        if (result == BanManager.BanMutationResult.NOT_ACTIVE) {
            sendError(exchange, 404, target + " 未被封禁或状态已变化");
        } else if (result == BanManager.BanMutationResult.STATE_CHANGED) {
            sendError(exchange, 409, "数据状态已变化，请刷新后重试");
        } else if (result == BanManager.BanMutationResult.REJECTED_PRIVATE_OR_RESERVED_IP) {
            sendError(exchange, 400, "私有或保留 IP 不允许执行此操作");
        } else {
            sendError(exchange, 500, "数据库操作失败，操作未完成");
        }
        return true;
    }

    private void handleStats(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        AtomicReference<Integer> onlineRef = new AtomicReference<>(0);
        AtomicReference<Integer> maxRef = new AtomicReference<>(0);
        boolean completed = runSync(exchange, () -> {
            onlineRef.set(plugin.getServer().getOnlinePlayers().size());
            maxRef.set(plugin.getServer().getMaxPlayers());
        });
        if (!completed) return;

        JsonObject stats = new JsonObject();
        List<BanEntry> bans = plugin.getBanManager().getBanList();
        List<BanIpEntry> ipBans = plugin.getBanManager().getBanIpList();
        stats.addProperty("plugin_version", plugin.getPluginVersion());
        stats.addProperty("online_players", onlineRef.get());
        stats.addProperty("max_players", maxRef.get());
        stats.addProperty("database_status", plugin.getDatabaseManager().isHealthy() ? "正常" : "异常");
        stats.addProperty("database_type", getDatabaseType());
        stats.addProperty("total_bans", bans.size() + ipBans.size());
        stats.addProperty("active_bans", bans.size());
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
            obj.addProperty("active", entry.isActive() && entry.getTime() > System.currentTimeMillis());
            obj.addProperty("auto", entry.isAuto());
            recentBans.add(obj);
        }
        stats.add("recent_bans", recentBans);

        sendJson(exchange, 200, stats.toString());
    }

    private String getDatabaseType() {
        return plugin.getDatabaseManager().getDatabaseProductName();
    }

    private void handleBanList(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        JsonArray bans = new JsonArray();
        for (BanEntry entry : plugin.getBanManager().getBanList()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("target", entry.getTarget());
            obj.addProperty("staff", entry.getStaff());
            obj.addProperty("reason", entry.getReason());
            obj.addProperty("end_time", TimeUtils.timestampToReadable(entry.getTime()));
            obj.addProperty("remaining", TimeUtils.getRemainingTime(entry.getTime()));
            obj.addProperty("auto", entry.isAuto());
            bans.add(obj);
        }
        JsonObject result = new JsonObject();
        result.add("bans", bans);
        result.addProperty("total", bans.size());
        sendJson(exchange, 200, result.toString());
    }

    private void handleIpBanList(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        JsonArray bans = new JsonArray();
        for (BanIpEntry entry : plugin.getBanManager().getBanIpList()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("ip", entry.getIp());
            obj.addProperty("staff", entry.getStaff());
            obj.addProperty("reason", entry.getReason());
            obj.addProperty("end_time", TimeUtils.timestampToReadable(entry.getTime()));
            obj.addProperty("remaining", TimeUtils.getRemainingTime(entry.getTime()));
            obj.addProperty("auto", entry.isAuto());
            bans.add(obj);
        }
        JsonObject result = new JsonObject();
        result.add("bans", bans);
        result.addProperty("total", bans.size());
        sendJson(exchange, 200, result.toString());
    }

    private void handleMuteList(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        JsonArray mutes = new JsonArray();
        for (MuteEntry entry : plugin.getMuteManager().getMuteList()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("target", entry.getTarget());
            obj.addProperty("staff", entry.getStaff());
            obj.addProperty("reason", entry.getReason());
            obj.addProperty("time", TimeUtils.timestampToReadable(entry.getTime()));
            mutes.add(obj);
        }
        JsonObject result = new JsonObject();
        result.add("mutes", mutes);
        result.addProperty("total", mutes.size());
        sendJson(exchange, 200, result.toString());
    }

    private void handleReports(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        JsonArray reports = new JsonArray();
        for (ReportEntry entry : plugin.getReportManager().getPendingReports()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", entry.getId());
            obj.addProperty("target", entry.getTarget());
            obj.addProperty("reporter", entry.getReporter());
            obj.addProperty("reason", entry.getReason());
            obj.addProperty("status", entry.getStatus());
            obj.addProperty("timestamp", TimeUtils.timestampToReadable(entry.getTimestamp()));
            reports.add(obj);
        }
        JsonObject result = new JsonObject();
        result.add("reports", reports);
        result.addProperty("total", reports.size());
        sendJson(exchange, 200, result.toString());
    }

    private void handleMute(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "mute")) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String duration = json.has("duration") ? json.get("duration").getAsString() : "7d";
            String reason = json.has("reason") ? json.get("reason").getAsString() : "管理员操作";
            String staff = authManager.resolveActor(extractToken(exchange));
            final String finalStaff = staff;

            long durationMs = TimeUtils.parseTime(duration);
            if (durationMs <= 0) durationMs = TimeUtils.daysToMillis(7);
            long endTime = TimeUtils.calculateEndTime(durationMs);

            AtomicReference<String> outcome = new AtomicReference<>("ok");
            boolean completed = runSync(exchange, () -> {
                if (!plugin.getImmunityManager().canPunish(plugin.getImmunityManager().getWebOperatorWeight(), target)) {
                    outcome.set("403");
                    return;
                }
                Long newEnd = plugin.getMuteManager().mutePlayer(new MuteEntry(target, finalStaff, endTime, reason));
                if (newEnd == null) {
                    outcome.set("noop");
                }
            });
            if (!completed) return;
            if ("403".equals(outcome.get())) {
                sendError(exchange, 403, "目标权重高于操作者，无法执行");
                return;
            }
            if ("noop".equals(outcome.get())) {
                sendError(exchange, 409, "该目标已有相同时长的禁言记录，未重复禁言");
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被禁言，时长: " + TimeUtils.formatDuration(durationMs));
            sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "禁言失败: " + e.getMessage());
        }
    }

    private void handleUnmute(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "mute")) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            final String finalActor = authManager.resolveActor(extractToken(exchange));

            AtomicReference<Boolean> permissionDenied = new AtomicReference<>(false);
            boolean completed = runSync(exchange, () -> {
                if (!plugin.getImmunityManager().canPunishTarget(plugin.getImmunityManager().getWebOperatorWeight(), target)) {
                    permissionDenied.set(true);
                    return;
                }
                plugin.getMuteManager().unmutePlayer(target, finalActor);
            });
            if (!completed) return;
            if (permissionDenied.get()) {
                sendError(exchange, 403, "目标权重高于操作者，无法执行");
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被解除禁言");
            sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "解除禁言失败: " + e.getMessage());
        }
    }

    private void handleWarn(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "warn")) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
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
                sendError(exchange, 403, "目标权重高于操作者，无法执行");
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被警告");
            sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "警告失败: " + e.getMessage());
        }
    }

    private void handleReportAction(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "admin")) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String id = json.get("id").getAsString();
            String action = json.get("action").getAsString();

            ReportEntry report = plugin.getReportManager().getReport(id);
            if (report == null) {
                sendError(exchange, 404, "举报不存在");
                return;
            }

            if ("accept".equalsIgnoreCase(action)) {
                report.setStatus("受理中");
                plugin.getReportManager().updateReport(report);
                plugin.getAuditManager().log("受理举报", authManager.resolveActor(extractToken(exchange)), report.getTarget(), "编号: " + id + " - " + report.getReason());
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "举报 " + id + " 已受理");
                sendJson(exchange, 200, result.toString());
            } else if ("close".equalsIgnoreCase(action)) {
                report.setStatus("已关闭");
                plugin.getReportManager().updateReport(report);
                plugin.getAuditManager().log("关闭举报", authManager.resolveActor(extractToken(exchange)), report.getTarget(), "编号: " + id + " - " + report.getReason());
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "举报 " + id + " 已关闭");
                sendJson(exchange, 200, result.toString());
            } else {
                sendError(exchange, 400, "未知操作: " + action);
            }
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "操作失败: " + e.getMessage());
        }
    }

    private void handleReload(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "reload")) return;

        try {
            boolean completed = runSync(exchange, () -> {
                plugin.reloadConfig();

                ModelManager.getInstance().reloadModel();

                File broadcastFile = new File(plugin.getDataFolder(), "broadcast.yml");
                if (broadcastFile.exists()) {
                    try {
                        plugin.getBroadcastFC().load(broadcastFile);
                    } catch (Exception e) {
                        plugin.getLogger().warning("重载broadcast.yml失败: " + e.getMessage());
                    }
                }
                File chatConfigFile = new File(plugin.getDataFolder(), "chatconfig.yml");
                if (chatConfigFile.exists()) {
                    try {
                        plugin.getChatConfig().load(chatConfigFile);
                    } catch (Exception e) {
                        plugin.getLogger().warning("重载chatconfig.yml失败: " + e.getMessage());
                    }
                }
                plugin.registerFeatureCommands();
            });
            if (!completed) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "配置已重新加载");
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendError(exchange, 500, "重载失败: " + e.getMessage());
            return;
        }
        // 在当前请求已响应后重启 Web 服务，避免 stop(0) 自杀式中断正在处理的请求
        org.leng.utils.SchedulerUtils.runTask(plugin, () -> {
            boolean restarted = plugin.reloadWebServer();
            if (!restarted) {
                plugin.getLogger().severe("Web 管理面板重启失败：配置项校验未通过（web.jwt-secret/web.admin-password 等），面板已下线，请修正配置后再次 /api/reload 或重启服务器。");
            }
        });
    }

    private void handleBroadcast(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "broadcast")) return;

        try {
            String defaultMessage = plugin.getBroadcastFC().getString("default-message");
            int banCount = plugin.getBanManager().getBanList().size();
            int banIpCount = plugin.getBanManager().getBanIpList().size();
            int totalBans = banCount + banIpCount;

            defaultMessage = defaultMessage
                    .replace("%s", String.valueOf(banCount))
                    .replace("%i", String.valueOf(banIpCount))
                    .replace("%t", String.valueOf(totalBans));

            String message = defaultMessage;
            boolean completed = runSync(exchange, () -> plugin.getServer().broadcastMessage(plugin.prefix() + " " + message));
            if (!completed) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "已广播封禁人数");
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendError(exchange, 500, "广播失败: " + e.getMessage());
        }
    }

    private void handleAudit(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "audit")) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String filter = params.get("player");
        int limit;
        try {
            limit = Integer.parseInt(params.getOrDefault("limit", "50"));
        } catch (NumberFormatException e) {
            limit = 50;
        }
        if (limit < 1) limit = 1;
        if (limit > 200) limit = 200;

        JsonArray logs = new JsonArray();
        for (AuditEntry entry : plugin.getAuditManager().getLogs(filter == null ? "" : filter, limit)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("timestamp", TimeUtils.timestampToReadable(entry.getTimestamp()));
            obj.addProperty("actor", entry.getActor());
            obj.addProperty("action", entry.getAction());
            obj.addProperty("target", entry.getTarget());
            obj.addProperty("reason", entry.getReason());
            obj.addProperty("success", entry.isSuccess());
            logs.add(obj);
        }
        JsonObject result = new JsonObject();
        result.add("logs", logs);
        result.addProperty("total", logs.size());
        sendJson(exchange, 200, result.toString());
    }

    private void handleRoot(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        try {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/")) path = "/index.html";

            String resourcePath = "web" + path;
            java.io.InputStream stream = plugin.getResource(resourcePath);
            if (stream != null) {
                byte[] bytes = readAllBytes(stream);
                exchange.getResponseHeaders().set("Content-Type", getMimeType(path));
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }

            if (path.equals("/index.html")) {
                JsonObject info = new JsonObject();
                info.addProperty("name", "Lengbanlist Web API");
                info.addProperty("version", plugin.getPluginVersion());
                info.addProperty("login", "POST /api/login 获取token");
                info.addProperty("usage", "在请求头加 Authorization: Bearer <token> 调用其他接口");
                sendJson(exchange, 200, info.toString());
                return;
            }
        } catch (IOException e) {
        }
        sendError(exchange, 404, "Not Found");
    }

    private byte[] readAllBytes(InputStream stream) throws IOException {
        try (InputStream input = stream; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
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
        if (path.endsWith(".ani")) return "application/x-navi-animation";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

}
