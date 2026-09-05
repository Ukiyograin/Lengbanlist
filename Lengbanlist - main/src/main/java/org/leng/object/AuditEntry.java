package org.leng.object;

public record AuditEntry(
        long id,
        long timestamp,
        String actor,
        String action,
        String target,
        String reason,
        boolean success,
        String prevHash
) {
    public AuditEntry {
        actor = actor == null ? "" : actor;
        action = action == null ? "" : action;
        target = target == null ? "" : target;
        reason = reason == null ? "" : reason;
        prevHash = prevHash == null ? "" : prevHash;
    }

    public AuditEntry(long timestamp, String actor, String action, String target, String reason, boolean success) {
        this(0, timestamp, actor, action, target, reason, success, "");
    }

    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getReason() { return reason; }
    public long getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public boolean isSuccess() { return success; }
    public String getPrevHash() { return prevHash; }
}