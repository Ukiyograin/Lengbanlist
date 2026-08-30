package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;
import org.leng.utils.IpMatcher;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;


public class MuteManager {
    private static final int MAX_RELOAD_ATTEMPTS = 3;

    private final Lengbanlist plugin;
    private final DatabaseManager db;
    private final Map<String, Long> muteCache = new ConcurrentHashMap<>();
    private final Map<String, Long> ipMuteCache = new ConcurrentHashMap<>();
    private final Object muteLock = new Object();
    private final Object reloadLock = new Object();
    private long mutationGeneration;

    public MuteManager(Lengbanlist plugin) throws SQLException {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        if (!reloadMuteCacheOrThrow()) {
            throw new SQLException("初始禁言缓存在并发变更中无法加载");
        }
    }

    /**
     * 禁言目标。若目标已被禁言且新禁言时长不同，则刷新时长并返回新时长；
     * 时长相同或目标是已被禁言的同网段 IP 时，返回 null（未发生变更）。
     * 玩家名匹配大小写不敏感（统一小写处理）。
     */
    public Long mutePlayer(MuteEntry muteEntry) {
        synchronized (muteLock) {
            String target = muteEntry.getTarget().toLowerCase();
            Long existing = existingActiveMute(target);
            if (existing != null) {
                if (existing.equals(muteEntry.getTime())) {
                    return null; // 时长未变化，避免重复广播/审计
                }
                db.upsertMute(muteEntry);
                muteCache.put(target, muteEntry.getTime());
                if (isIpTarget(target)) {
                    ipMuteCache.put(target, muteEntry.getTime());
                }
                mutationGeneration++;
                plugin.getAuditManager().log("修改禁言", muteEntry.getStaff(), muteEntry.getTarget(), muteEntry.getReason());
                return muteEntry.getTime();
            }
            db.upsertMute(muteEntry);
            muteCache.put(target, muteEntry.getTime());
            if (isIpTarget(target)) {
                ipMuteCache.put(target, muteEntry.getTime());
            }
            mutationGeneration++;
            plugin.getAuditManager().log("禁言", muteEntry.getStaff(), muteEntry.getTarget(), muteEntry.getReason());
            return muteEntry.getTime();
        }
    }

    /** 返回目标当前活跃禁言的结束时间，不存在返回 null。 */
    private Long existingActiveMute(String target) {
        Long cached = muteCache.get(target);
        if (cached != null) {
            if (cached == Long.MAX_VALUE || cached > System.currentTimeMillis()) {
                return cached;
            }
            muteCache.remove(target, cached);
            ipMuteCache.remove(target, cached);
            db.deleteMuteIfExpiresAt(target, cached);
            mutationGeneration++;
            return null;
        }
        if (IpMatcher.isIpv4(target) && hasEquivalentIpv4Mute(target)) {
            return Long.MAX_VALUE; // 同网段已有等价禁言，视为已禁言（不可叠加）
        }
        MuteEntry entry = db.getMute(target);
        if (entry == null) return null;
        if (entry.getTime() == Long.MAX_VALUE || entry.getTime() > System.currentTimeMillis()) {
            muteCache.put(target, entry.getTime());
            return entry.getTime();
        }
        db.deleteMuteIfExpiresAt(target, entry.getTime());
        mutationGeneration++;
        return null;
    }

    public void unmutePlayer(String target) {
        unmutePlayer(target, null);
    }

    public void unmutePlayer(String target, String actor) {
        boolean wasMuted;
        synchronized (muteLock) {
            List<String> storedTargets = storedTargetsFor(target);
            wasMuted = false;
            for (String storedTarget : storedTargets) {
                String cacheKey = storedTarget.toLowerCase();
                Long cached = muteCache.get(cacheKey);
                if (cached != null && isActive(cached)) {
                    wasMuted = true;
                } else {
                    MuteEntry storedEntry = db.getMute(cacheKey);
                    if (storedEntry != null && isActive(storedEntry.getTime())) {
                        wasMuted = true;
                    }
                }
                muteCache.remove(cacheKey);
                ipMuteCache.remove(storedTarget);
                // 在锁内删除 DB 行,防止并发 mutePlayer 刚插入的行被静默擦掉
                db.deleteMute(cacheKey);
            }
            mutationGeneration++;
            if (wasMuted) {
                plugin.getAuditManager().log("解除禁言", actor, target, "");
            }
        }
    }

    public void clearMuteCache() {
        synchronized (muteLock) {
            muteCache.clear();
            ipMuteCache.clear();
            mutationGeneration++;
        }
    }

    public boolean reloadMuteCache() {
        try {
            boolean reloaded = reloadMuteCacheOrThrow();
            if (!reloaded) {
                plugin.getLogger().warning("刷新禁言缓存失败：加载期间缓存持续变更");
            }
            return reloaded;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "刷新禁言缓存失败，将保留现有缓存", e);
            return false;
        }
    }

    private boolean reloadMuteCacheOrThrow() throws SQLException {
        synchronized (reloadLock) {
            return loadStableMuteSnapshot();
        }
    }

    private boolean loadStableMuteSnapshot() throws SQLException {
        for (int attempt = 0; attempt < MAX_RELOAD_ATTEMPTS; attempt++) {
            long generationBeforeLoad;
            synchronized (muteLock) {
                generationBeforeLoad = mutationGeneration;
            }

            Map<String, Long> loadedMutes = new HashMap<>();
            Map<String, Long> loadedIpMutes = new HashMap<>();
            long now = System.currentTimeMillis();
            for (MuteEntry entry : db.loadMutesForCache()) {
                if (entry.getTime() == Long.MAX_VALUE || entry.getTime() > now) {
                    String targetKey = entry.getTarget().toLowerCase();
                    loadedMutes.put(targetKey, entry.getTime());
                    if (isIpTarget(targetKey)) {
                        loadedIpMutes.put(targetKey, entry.getTime());
                    }
                } else {
                    db.deleteMuteIfExpiresAt(entry.getTarget().toLowerCase(), entry.getTime());
                }
            }

            synchronized (muteLock) {
                if (mutationGeneration != generationBeforeLoad) {
                    continue;
                }
                muteCache.clear();
                muteCache.putAll(loadedMutes);
                ipMuteCache.clear();
                ipMuteCache.putAll(loadedIpMutes);
                mutationGeneration++;
                return true;
            }
        }
        return false;
    }

    public List<MuteEntry> getMuteList() {
        return db.getMutes();
    }

    public boolean isPlayerMuted(String playerName) {
        if (playerName == null) {
            return false;
        }
        String normalized = playerName.toLowerCase();
        synchronized (muteLock) {
            Long cached = muteCache.get(normalized);
            if (cached != null) {
                if (cached == Long.MAX_VALUE || cached > System.currentTimeMillis()) {
                    return true;
                }
                muteCache.remove(normalized, cached);
                db.deleteMuteIfExpiresAt(normalized, cached);
                mutationGeneration++;
                return false;
            }
            MuteEntry entry = db.getMute(normalized);
            if (entry == null) return false;
            if (entry.getTime() == Long.MAX_VALUE || entry.getTime() > System.currentTimeMillis()) {
                muteCache.put(normalized, entry.getTime());
                return true;
            }
            db.deleteMuteIfExpiresAt(normalized, entry.getTime());
            mutationGeneration++;
            return false;
        }
    }

    public boolean isIpMuted(String ip) {
        synchronized (muteLock) {
            if (ip == null) return false;
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : ipMuteCache.entrySet()) {
                String target = entry.getKey();
                Long time = entry.getValue();
                boolean matches = IpMatcher.isIpv4(target)
                        ? sameIpv4(ip, target)
                        : IpMatcher.cidrMatches(ip, target);
                if (matches) {
                    if (time == Long.MAX_VALUE || time > now) {
                        return true;
                    }
                    ipMuteCache.remove(target, time);
                    muteCache.remove(target, time);
                    db.deleteMuteIfExpiresAt(target, time);
                    mutationGeneration++;
                }
            }
            return false;
        }
    }

    private static boolean isIpTarget(String target) {
        return IpMatcher.isIpv4(target) || IpMatcher.isCidr(target);
    }

    private boolean hasEquivalentIpv4Mute(String target) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : ipMuteCache.entrySet()) {
            String storedTarget = entry.getKey();
            Long time = entry.getValue();
            if (!IpMatcher.isIpv4(storedTarget) || !sameIpv4(target, storedTarget)) {
                continue;
            }
            if (time == Long.MAX_VALUE || time > now) {
                return true;
            }
            ipMuteCache.remove(storedTarget, time);
            muteCache.remove(storedTarget, time);
            db.deleteMuteIfExpiresAt(storedTarget, time);
            mutationGeneration++;
        }
        return false;
    }

    private List<String> storedTargetsFor(String target) {
        List<String> storedTargets = new ArrayList<>();
        if (IpMatcher.isIpv4(target)) {
            for (String storedTarget : ipMuteCache.keySet()) {
                if (sameIpv4(target, storedTarget)) {
                    storedTargets.add(storedTarget);
                }
            }
        } else if (IpMatcher.isCidr(target)) {
            for (String storedTarget : ipMuteCache.keySet()) {
                if (IpMatcher.isCidr(storedTarget) && IpMatcher.cidrMatches(target, storedTarget)) {
                    storedTargets.add(storedTarget);
                }
            }
        }
        if (storedTargets.isEmpty()) {
            storedTargets.add(target);
        }
        return storedTargets;
    }

    private static boolean sameIpv4(String first, String second) {
        return IpMatcher.isIpv4(first)
                && IpMatcher.isIpv4(second)
                && IpMatcher.ipToLong(first) == IpMatcher.ipToLong(second);
    }

    private static boolean isActive(long endTime) {
        return endTime == Long.MAX_VALUE || endTime > System.currentTimeMillis();
    }

    public boolean isPlayerMuted(org.bukkit.entity.Player player) {
        if (isPlayerMuted(player.getName())) return true;
        if (player.getAddress() != null) {
            String ip = player.getAddress().getAddress().getHostAddress();
            return isIpMuted(ip);
        }
        return false;
    }
}
