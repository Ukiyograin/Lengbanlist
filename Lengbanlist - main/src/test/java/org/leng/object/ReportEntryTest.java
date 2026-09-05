package org.leng.object;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportEntryTest {

    @Test
    void constructor_setsDefaultStatusAndTimestamp() {
        long before = System.currentTimeMillis();
        ReportEntry entry = new ReportEntry("alice", "bob", "spam", "id1");
        long after = System.currentTimeMillis();
        assertEquals("alice", entry.target());
        assertEquals("bob", entry.reporter());
        assertEquals("spam", entry.reason());
        assertEquals("id1", entry.id());
        assertEquals("未处理", entry.status());
        assertTrue(entry.timestamp() >= before && entry.timestamp() <= after);
    }

    @Test
    void withStatus_returnsNewInstance() {
        ReportEntry original = new ReportEntry("alice", "bob", "spam", "id1");
        ReportEntry updated = original.withStatus("已处理");
        assertEquals("未处理", original.status());
        assertEquals("已处理", updated.status());
        assertEquals(original.id(), updated.id());
    }

    @Test
    void serialize_roundTripsViaDeserialize() {
        ReportEntry original = new ReportEntry("alice", "bob", "spam", "id1", 1234567890L, "已处理");
        Map<String, Object> map = original.serialize();
        ReportEntry restored = ReportEntry.deserialize(map);
        assertEquals(original.target(), restored.target());
        assertEquals(original.reporter(), restored.reporter());
        assertEquals(original.reason(), restored.reason());
        assertEquals(original.id(), restored.id());
        assertEquals(original.status(), restored.status());
        assertEquals(original.timestamp(), restored.timestamp());
    }

    @Test
    void deserialize_handlesMissingTimestampAndStatus() {
        Map<String, Object> map = new HashMap<>();
        map.put("target", "alice");
        map.put("reporter", "bob");
        map.put("reason", "spam");
        map.put("id", "id1");
        long before = System.currentTimeMillis();
        ReportEntry entry = ReportEntry.deserialize(map);
        long after = System.currentTimeMillis();
        assertEquals("未处理", entry.status()); // 默认状态
        assertTrue(entry.timestamp() >= before && entry.timestamp() <= after);
    }
}