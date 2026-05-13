package org.leng.object;

import java.util.UUID;

public class WarnEntry {
    private final String id; // 添加一个唯一的标识符
    private final String player;
    private final String staff;
    private final long time;
    private String reason;
    private boolean revoked;

    public WarnEntry(String player, String staff, long time, String reason) {
        this(UUID.randomUUID().toString(), player, staff, time, reason);
    }

    public WarnEntry(String id, String player, String staff, long time, String reason) {
        this.id = id;
        this.player = player;
        this.staff = staff;
        this.time = time;
        this.reason = reason;
        this.revoked = false;
    }

    public String getPlayer() {
        return player;
    }

    public String getStaff() {
        return staff;
    }

    public long getTime() {
        return time;
    }

    public String getReason() {
        return reason;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public String getId() {
        return id;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public void revoke() {
        this.revoked = true;
    }
}
