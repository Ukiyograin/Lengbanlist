package org.leng.web;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 滑动窗口 IP 限流器，从 WebServer 内嵌类抽离。
 * 限流阈值可在 reload() 中更新（默认 60 次 / 60 秒）。
 */
public class RateLimiter {

    public static final int DEFAULT_MAX_REQUESTS = 60;
    public static final long DEFAULT_WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, AtomicLongArray> requests = new ConcurrentHashMap<>();
    private final AtomicInteger maxRequests = new AtomicInteger(DEFAULT_MAX_REQUESTS);
    private final AtomicLong windowMs = new AtomicLong(DEFAULT_WINDOW_MS);

    public boolean isRateLimited(String ip) {
        if (requests.size() > 1024) cleanup();
        long now = System.currentTimeMillis();
        long window = windowMs.get();
        AtomicLongArray window0 = requests.compute(ip, (key, val) -> {
            if (val == null || now - val.get(0) > window) {
                return new AtomicLongArray(new long[]{now, 1});
            }
            val.getAndIncrement(1);
            return val;
        });
        return window0.get(1) > maxRequests.get();
    }

    public void cleanup() {
        long cutoff = System.currentTimeMillis() - windowMs.get();
        requests.entrySet().removeIf(e -> e.getValue().get(0) < cutoff);
    }

    /** /lban reload 时刷新阈值,允许运维按需调整 */
    public void reload(int maxRequests, long windowMs) {
        if (maxRequests > 0) this.maxRequests.set(maxRequests);
        if (windowMs > 0) this.windowMs.set(windowMs);
    }
}