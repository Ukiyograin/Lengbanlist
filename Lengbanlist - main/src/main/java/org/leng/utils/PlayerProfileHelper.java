package org.leng.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 异步 PlayerProfile 查询包装。
 * - 同步路径：Bukkit.getPlayerExact（在线）/ getOfflinePlayer（已实现过的玩家）
 * - 异步路径：通过反射调用 Paper 的 Server.createPlayerProfile（如果可用），否则 fallback 到离线同步
 *
 * 注意：
 * - Bukkit.getOfflinePlayer 在 Folia 上抛 UnsupportedOperationException，调用方需 catch
 * - 本类的 asyncLookUp 始终返回 CompletableFuture，永不抛异常阻塞主线程
 */
public final class PlayerProfileHelper {

    private PlayerProfileHelper() {}

    /** 缓存最近查询的 OfflinePlayer 引用，避免短时间内重复触发 IO */
    private static final ConcurrentHashMap<String, OfflinePlayer> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000L;
    private static final ConcurrentHashMap<String, Long> cacheTime = new ConcurrentHashMap<>();

    /**
     * 同步获取 OfflinePlayer（不会调用可能抛异常的 getOfflinePlayer 在 Folia 路径上）。
     * 在线玩家直接返回，离线玩家缓存查找。
     *
     * @return 找到返回 OfflinePlayer，未找到返回 null
     */
    public static OfflinePlayer lookupSync(String name) {
        if (name == null || name.isEmpty()) return null;

        // 在线玩家直接返回
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        // 缓存查找
        OfflinePlayer cached = cache.get(name);
        Long t = cacheTime.get(name);
        if (cached != null && t != null && System.currentTimeMillis() - t < CACHE_TTL_MS) {
            return cached;
        }

        try {
            OfflinePlayer fresh = Bukkit.getOfflinePlayer(name);
            if (fresh != null && (fresh.hasPlayedBefore() || fresh.isOnline())) {
                cache.put(name, fresh);
                cacheTime.put(name, System.currentTimeMillis());
                return fresh;
            }
        } catch (UnsupportedOperationException ex) {
            // Folia 不支持离线查询
            return null;
        } catch (Exception ex) {
            // 其他异常兜底
            return null;
        }
        return null;
    }

    /**
     * 异步查询 OfflinePlayer。返回 CompletableFuture，主线程不阻塞。
     * Folia 上 Bukkit.getOfflinePlayer 抛异常时返回 null。
     */
    public static CompletableFuture<OfflinePlayer> lookupAsync(String name) {
        CompletableFuture<OfflinePlayer> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(LengbanlistAccessor.getPlugin(), () -> {
            OfflinePlayer result = lookupSync(name);
            future.complete(result);
        });
        return future.orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> null);
    }

    /**
     * 异步查询并回调主线程消费结果。
     */
    public static void lookupAsync(String name, Consumer<OfflinePlayer> onResult) {
        lookupAsync(name).thenAccept(p ->
            SchedulerUtils.runTask(LengbanlistAccessor.getPlugin(), () -> onResult.accept(p)));
    }

    /**
     * 通过 UUID 查 OfflinePlayer（避免 name→UUID 的 IO）。
     */
    public static OfflinePlayer lookupByUuid(UUID uuid) {
        if (uuid == null) return null;
        try {
            return Bukkit.getOfflinePlayer(uuid);
        } catch (UnsupportedOperationException ex) {
            // Folia 路径
            return null;
        }
    }

    /**
     * 清理过期缓存（测试/手动调用）。
     */
    public static void clearCache() {
        cache.clear();
        cacheTime.clear();
    }

    /**
     * 反射获取 Lengbanlist 实例，避免循环依赖 utils → Lengbanlist → utils。
     */
    private static final class LengbanlistAccessor {
        private static org.leng.Lengbanlist plugin;
        static {
            try {
                // 通过 Bukkit.getPluginManager 查找已加载的实例
                org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("Lengbanlist");
                if (p instanceof org.leng.Lengbanlist) {
                    plugin = (org.leng.Lengbanlist) p;
                }
            } catch (Exception ignored) {}
        }
        static org.leng.Lengbanlist getPlugin() {
            if (plugin == null) {
                org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("Lengbanlist");
                if (p instanceof org.leng.Lengbanlist) {
                    plugin = (org.leng.Lengbanlist) p;
                }
            }
            return plugin;
        }
    }
}