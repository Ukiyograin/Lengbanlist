package org.leng.object;

import java.util.Objects;

public record MuteEntry(
        String target,
        String staff,
        long time,
        String reason
) {
    public MuteEntry {
        Objects.requireNonNull(target, "Target cannot be null");
        Objects.requireNonNull(staff, "Staff cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
    }

    public long getEndTime() { return time; }
    public long getTime() { return time; }
    public String getTarget() { return target; }
    public String getStaff() { return staff; }
    public String getReason() { return reason; }

    @Override
    public String toString() {
        return target + ":" + staff + ":" + time + ":" + reason;
    }
}