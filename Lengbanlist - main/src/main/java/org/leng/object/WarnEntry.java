package org.leng.object;

import java.util.Objects;
import java.util.UUID;

public record WarnEntry(
        String id,
        String player,
        String staff,
        long time,
        String reason,
        boolean revoked
) {
    public WarnEntry {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(staff, "Staff cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
    }

    public WarnEntry(String player, String staff, long time, String reason) {
        this(UUID.randomUUID().toString(), player, staff, time, reason, false);
    }

    public WarnEntry(String id, String player, String staff, long time, String reason) {
        this(id, player, staff, time, reason, false);
    }

    public WarnEntry withReason(String newReason) {
        return new WarnEntry(id, player, staff, time, newReason, revoked);
    }

    public WarnEntry withRevoked(boolean newRevoked) {
        return new WarnEntry(id, player, staff, time, reason, newRevoked);
    }

    public WarnEntry revoke() {
        return withRevoked(true);
    }

    public WarnEntry unrevoke() {
        return withRevoked(false);
    }

    // 兼容旧 getter 命名（保持现有调用方不破坏）
    public String getReason() { return reason; }
    public boolean isRevoked() { return revoked; }
    public String getPlayer() { return player; }
    public String getStaff() { return staff; }
    public long getTime() { return time; }
    public String getId() { return id; }
}