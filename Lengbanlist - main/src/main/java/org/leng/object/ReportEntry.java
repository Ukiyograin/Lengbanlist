package org.leng.object;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.HashMap;
import java.util.Map;

@SerializableAs("ReportEntry")
public record ReportEntry(
        String target,
        String reporter,
        String reason,
        String id,
        long timestamp,
        String status
) implements ConfigurationSerializable {

    public ReportEntry(String target, String reporter, String reason, String id) {
        this(target, reporter, reason, id, System.currentTimeMillis(), "未处理");
    }

    public ReportEntry withStatus(String newStatus) {
        return new ReportEntry(target, reporter, reason, id, timestamp, newStatus);
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("target", target);
        map.put("reporter", reporter);
        map.put("reason", reason);
        map.put("id", id);
        map.put("status", status);
        map.put("timestamp", timestamp);
        return map;
    }

    public static ReportEntry deserialize(Map<String, Object> map) {
        Object timestampValue = map.get("timestamp");
        long timestamp = timestampValue instanceof Number ? ((Number) timestampValue).longValue() : System.currentTimeMillis();
        Object statusValue = map.get("status");
        String status = statusValue == null ? "未处理" : String.valueOf(statusValue);

        return new ReportEntry(
                stringValue(map.get("target")),
                stringValue(map.get("reporter")),
                stringValue(map.get("reason")),
                stringValue(map.get("id")),
                timestamp,
                status
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    // 兼容旧 getter 命名
    public String getStatus() { return status; }
    public String getTarget() { return target; }
    public String getReporter() { return reporter; }
    public String getReason() { return reason; }
    public String getId() { return id; }
    public long getTimestamp() { return timestamp; }
}