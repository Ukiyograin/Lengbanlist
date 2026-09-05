package org.leng.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.manager.BanManager;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.utils.TimeUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 封禁/解封 controller。
 * 处理 /api/ban、/api/unban。
 */
public class BanController extends WebController {

    public BanController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/ban", this::handleBan);
        server.createContext("/api/unban", this::handleUnban);
    }

    private void handleBan(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
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

            // 校验 IP 合法性,避免非法字符串以"IP 封禁"名义入库
            if (target.contains(".") && !org.leng.utils.IpMatcher.isValidIpOrCidrOrWildcard(target)) {
                WebResponse.sendError(exchange, 400, "无效的 IP 或 CIDR 格式");
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
                WebResponse.sendError(exchange, 403, "目标权重高于操作者,无法执行");
                return;
            }
            if (WebResponse.sendMutationFailure(exchange, mutationResult.get(), target)) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被封禁,时长: " + TimeUtils.formatDuration(durationMs));
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "封禁失败: " + e.getMessage());
        }
    }

    private void handleUnban(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String staff = authManager.resolveActor(extractToken(exchange));
            boolean isIp = target.contains(".") && org.leng.utils.IpMatcher.isValidIpOrCidrOrWildcard(target);

            if (!requireFeature(exchange, isIp ? "unban-ip" : "unban")) return;

            AtomicReference<BanManager.BanMutationResult> result = new AtomicReference<>();
            boolean completed = runSync(exchange, () -> {
                if (isIp) {
                    result.set(plugin.getBanManager().tryUnbanIp(target, staff, false));
                } else {
                    result.set(plugin.getBanManager().tryUnbanPlayer(target, staff, false));
                }
            });
            if (!completed) return;
            if (WebResponse.sendMutationFailure(exchange, result.get(), target)) return;

            JsonObject resp = new JsonObject();
            resp.addProperty("success", true);
            resp.addProperty("message", target + " 已被解封");
            WebResponse.sendJson(exchange, 200, resp.toString());
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "解封失败: " + e.getMessage());
        }
    }
}