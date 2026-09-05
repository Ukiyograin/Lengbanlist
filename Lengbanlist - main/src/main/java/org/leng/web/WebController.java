package org.leng.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controller 抽象基类,封装鉴权/限流/主线程调度等通用能力。
 */
public abstract class WebController {

    protected final Lengbanlist plugin;
    protected final Gson gson = new Gson();
    protected final AuthManager authManager;
    protected final RateLimiter rateLimiter = new RateLimiter();

    protected WebController(Lengbanlist plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    public abstract void registerRoutes(HttpServer server);

    protected boolean checkRateLimit(HttpExchange exchange) {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (rateLimiter.isRateLimited(ip)) {
            WebResponse.sendError(exchange, 429, "请求过于频繁,请稍后再试");
            return false;
        }
        return true;
    }

    protected String extractToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        return null;
    }

    protected boolean requireAuth(HttpExchange exchange) {
        if (!checkRateLimit(exchange)) return false;
        String token = extractToken(exchange);
        if (token == null || !authManager.validateToken(token)) {
            WebResponse.sendError(exchange, 401, "未授权");
            return false;
        }
        return true;
    }

    protected boolean requireFeature(HttpExchange exchange, String feature) {
        if (!plugin.isFeatureEnabled(feature)) {
            WebResponse.sendError(exchange, 403, "此功能已被管理员禁用");
            return false;
        }
        return true;
    }

    protected boolean runSync(HttpExchange exchange, Runnable task) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean timedOut = new AtomicBoolean(false);
        org.leng.utils.SchedulerUtils.SchedulerTask ignored = org.leng.utils.SchedulerUtils.runTask(plugin, () -> {
            try {
                if (timedOut.get()) return;
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
                WebResponse.sendError(exchange, 504, "主线程繁忙,操作可能已在后台执行,请在管理列表确认结果后再决定是否重试,避免重复提交");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timedOut.set(true);
            WebResponse.sendError(exchange, 500, "操作被中断");
            return false;
        }
        if (error.get() != null) {
            WebResponse.sendError(exchange, 500, "操作失败");
            return false;
        }
        return true;
    }
}