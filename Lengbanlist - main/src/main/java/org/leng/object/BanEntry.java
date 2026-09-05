package org.leng.object;

import java.util.Objects;

public record BanEntry(
        String target,
        String staff,
        long time,
        String reason,
        boolean isAuto,
        boolean active
) {
    public BanEntry {
        Objects.requireNonNull(target, "Target cannot be null");
        Objects.requireNonNull(staff, "Staff cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
    }

    public BanEntry(String target, String staff, long time, String reason, boolean isAuto) {
        this(target, staff, time, reason, isAuto, true);
    }

    public BanEntry withEndTime(long newTime) {
        return new BanEntry(target, staff, newTime, reason, isAuto, active);
    }

    public BanEntry withReason(String newReason) {
        return new BanEntry(target, staff, time, newReason, isAuto, active);
    }

    public BanEntry withAuto(boolean newAuto) {
        return new BanEntry(target, staff, time, reason, newAuto, active);
    }

    public BanEntry withActive(boolean newActive) {
        return new BanEntry(target, staff, time, reason, isAuto, newActive);
    }

    public BanEntry withStaff(String newStaff) {
        return new BanEntry(target, newStaff, time, reason, isAuto, active);
    }

    public BanEntry withTarget(String newTarget) {
        return new BanEntry(newTarget, staff, time, reason, isAuto, active);
    }

    public long getRemainingTime() {
        return Math.max(0, time - System.currentTimeMillis());
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > time;
    }

    @Override
    public String toString() {
        return String.join(":",
            target, staff, String.valueOf(time), reason,
            String.valueOf(isAuto), String.valueOf(active));
    }

    public static BanEntry fromString(String entry) {
        String[] parts = entry.split(":");
        if (parts.length < 5) {
            throw new IllegalArgumentException("Invalid ban entry format");
        }
        return new BanEntry(
            parts[0], parts[1], Long.parseLong(parts[2]), parts[3],
            Boolean.parseBoolean(parts[4]),
            parts.length >= 6 ? Boolean.parseBoolean(parts[5]) : true
        );
    }

    // 兼容旧 getter / setter 命名
    public String getTarget() { return target; }
    public String getStaff() { return staff; }
    public long getTime() { return time; }
    public long getEndTime() { return time; }
    public String getReason() { return reason; }
    public boolean isAuto() { return isAuto; }
    public boolean isActive() { return active; }
}