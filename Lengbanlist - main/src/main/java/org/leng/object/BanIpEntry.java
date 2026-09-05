package org.leng.object;

import java.util.Objects;

public record BanIpEntry(
        String ip,
        String staff,
        long time,
        String reason,
        boolean isAuto,
        boolean active
) {
    public BanIpEntry {
        Objects.requireNonNull(ip, "IP cannot be null");
        Objects.requireNonNull(staff, "Staff cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
    }

    public BanIpEntry(String ip, String staff, long time, String reason, boolean isAuto) {
        this(ip, staff, time, reason, isAuto, true);
    }

    public BanIpEntry withEndTime(long newTime) {
        return new BanIpEntry(ip, staff, newTime, reason, isAuto, active);
    }

    public BanIpEntry withReason(String newReason) {
        return new BanIpEntry(ip, staff, time, newReason, isAuto, active);
    }

    public BanIpEntry withAuto(boolean newAuto) {
        return new BanIpEntry(ip, staff, time, reason, newAuto, active);
    }

    public BanIpEntry withActive(boolean newActive) {
        return new BanIpEntry(ip, staff, time, reason, isAuto, newActive);
    }

    public long getRemainingTime() {
        return Math.max(0, time - System.currentTimeMillis());
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > time;
    }

    @Override
    public String toString() {
        return ip + ":" + staff + ":" + time + ":" + reason + ":" + isAuto + ":" + active;
    }

    // 兼容旧 getter 命名
    public String getIp() { return ip; }
    public String getStaff() { return staff; }
    public long getTime() { return time; }
    public long getEndTime() { return time; }
    public String getReason() { return reason; }
    public boolean isAuto() { return isAuto; }
    public boolean isActive() { return active; }
}