package org.leng.object;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BanIpEntryTest {

    @Test
    void constructor_acceptsAllArgs() {
        BanIpEntry entry = new BanIpEntry("10.0.0.1", "admin", 1000L, "spam", true, false);
        assertEquals("10.0.0.1", entry.getIp());
        assertEquals("admin", entry.getStaff());
        assertEquals(1000L, entry.getEndTime());
        assertTrue(entry.isAuto());
        assertFalse(entry.isActive());
    }

    @Test
    void withEndTime_returnsNewInstance() {
        BanIpEntry original = new BanIpEntry("10.0.0.1", "admin", 1000L, "spam", false);
        BanIpEntry extended = original.withEndTime(2000L);
        assertEquals(1000L, original.getEndTime());
        assertEquals(2000L, extended.getEndTime());
    }

    @Test
    void withReason_returnsNewInstance() {
        BanIpEntry original = new BanIpEntry("10.0.0.1", "admin", 0L, "old", false);
        BanIpEntry updated = original.withReason("new");
        assertEquals("old", original.getReason());
        assertEquals("new", updated.getReason());
    }

    @Test
    void withAuto_and_withActive_work() {
        BanIpEntry original = new BanIpEntry("10.0.0.1", "admin", 0L, "r", false, true);
        assertTrue(original.withAuto(true).isAuto());
        assertFalse(original.withActive(false).isActive());
        // 原对象不变
        assertFalse(original.isAuto());
        assertTrue(original.isActive());
    }

    @Test
    void isExpired_handlesPastAndFuture() {
        BanIpEntry past = new BanIpEntry("10.0.0.1", "admin", 1L, "r", false);
        BanIpEntry future = new BanIpEntry("10.0.0.1", "admin", Long.MAX_VALUE, "r", false);
        assertTrue(past.isExpired());
        assertFalse(future.isExpired());
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThrows(NullPointerException.class, () -> new BanIpEntry(null, "s", 0L, "r", false));
        assertThrows(NullPointerException.class, () -> new BanIpEntry("ip", null, 0L, "r", false));
        assertThrows(NullPointerException.class, () -> new BanIpEntry("ip", "s", 0L, null, false));
    }
}