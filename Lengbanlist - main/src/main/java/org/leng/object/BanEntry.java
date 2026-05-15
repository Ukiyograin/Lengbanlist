package org.leng.object;

import java.util.Objects;

public class BanEntry {
    private String target;
    private String staff;
    private long time;
    private String reason;
    private boolean isAuto;
    private boolean active;

    public BanEntry(String target, String staff, long time, String reason, boolean isAuto) {
        this(target, staff, time, reason, isAuto, true);
    }

    public BanEntry(String target, String staff, long time, String reason, boolean isAuto, boolean active) {
        this.target = Objects.requireNonNull(target, "Target cannot be null");
        this.staff = Objects.requireNonNull(staff, "Staff cannot be null");
        this.time = time;
        this.reason = Objects.requireNonNull(reason, "Reason cannot be null");
        this.isAuto = isAuto;
        this.active = active;
    }

    // Getters
    public String getTarget() {
        return target;
    }

    public String getStaff() {
        return staff;
    }

    public long getTime() {
        return time;
    }

    // 添加 getEndTime() 方法作为 getTime() 的别名
    public long getEndTime() {
        return time;
    }

    public String getReason() {
        return reason;
    }

    public boolean isAuto() {
        return isAuto;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    // Setters
    public void setTarget(String target) {
        this.target = target;
    }

    public void setStaff(String staff) {
        this.staff = staff;
    }

    public void setTime(long time) {
        this.time = time;
    }

    // 添加 setEndTime() 方法作为 setTime() 的别名
    public void setEndTime(long time) {
        this.time = time;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public void setAuto(boolean isAuto) {
        this.isAuto = isAuto;
    }

    // 计算剩余封禁时间（毫秒）
    public long getRemainingTime() {
        return Math.max(0, time - System.currentTimeMillis());
    }

    // 检查是否已过期
    public boolean isExpired() {
        return System.currentTimeMillis() > time;
    }

    @Override
    public String toString() {
        return String.join(":",
            target,
            staff,
            String.valueOf(time),
            reason,
            String.valueOf(isAuto),
            String.valueOf(active)
        );
    }

    public static BanEntry fromString(String entry) {
        String[] parts = entry.split(":");
        if (parts.length < 5) {
            throw new IllegalArgumentException("Invalid ban entry format");
        }
        return new BanEntry(
            parts[0],
            parts[1],
            Long.parseLong(parts[2]),
            parts[3],
            Boolean.parseBoolean(parts[4]),
            parts.length >= 6 ? Boolean.parseBoolean(parts[5]) : true
        );
    }
}