package org.leng.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;
import org.leng.utils.TimeUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 禁言/解禁 controller。
 * 处理 /api/mute、/api/unmute、/api/mutes。
 */
public class MuteController extends WebController {

    public MuteController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/mute", this::handleMute);
        server.createContext("/api/unmute", this::handleUnmute);
        server.createContext("/api/mutes", this::handleMuteList);
    }

    private void handleMute(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "mute")) return;

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
                WebResponse.sendError(exchange, 403, "目标权重高于操作者,无法执行");
                return;
            }
            if ("noop".equals(outcome.get())) {
                WebResponse.sendError(exchange, 409, "该目标已有相同时长的禁言记录,未重复禁言");
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被禁言,时长: " + TimeUtils.formatDuration(durationMs));
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "禁言失败: " + e.getMessage());
        }
    }

    private void handleUnmute(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "mute")) return;

        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
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
                WebResponse.sendError(exchange, 403, "目标权重高于操作者,无法执行");
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被解除禁言");
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "解除禁言失败: " + e.getMessage());
        }
    }

    private void handleMuteList(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            List<MuteEntry> mutes = plugin.getMuteManager().getMuteList();
            JsonArray arr = new JsonArray();
            for (MuteEntry m : mutes) {
                JsonObject o = new JsonObject();
                o.addProperty("target", m.target());
                o.addProperty("staff", m.staff());
                o.addProperty("end_time", m.time());
                o.addProperty("reason", m.reason());
                o.addProperty("active", m.time() > System.currentTimeMillis());
                o.addProperty("remaining", TimeUtils.formatDuration(m.time() - System.currentTimeMillis()));
                arr.add(o);
            }
            JsonObject result = new JsonObject();
            result.add("mutes", arr);
            result.addProperty("total", arr.size());
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 500, "查询禁言列表失败");
        }
    }
}