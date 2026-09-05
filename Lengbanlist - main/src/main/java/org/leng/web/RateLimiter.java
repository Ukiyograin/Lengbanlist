package org.leng.web;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 滑动窗口 IP 限流器,从 WebServer 内嵌类抽离。
 */
public class RateLimiter {

    public static final int MAX_REQUESTS = 60;
    public static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, AtomicLongArray> requests = new ConcurrentHashMap<>();

    public boolean isRateLimited(String ip) {
        if (requests.size() > 1024) cleanup();
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

    public void cleanup() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        requests.entrySet().removeIf(e -> e.getValue().get(0) < cutoff);
    }
}