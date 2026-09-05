package org.leng.object;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BanEntryTest {

    @Test
    void constructor_acceptsAllArgsAndExposesViaGetters() {
        BanEntry entry = new BanEntry("alice", "admin", 1234567890L, "spam", true, false);
        assertEquals("alice", entry.getTarget());
        assertEquals("admin", entry.getStaff());
        assertEquals(1234567890L, entry.getTime());
        assertEquals(1234567890L, entry.getEndTime());
        assertEquals("spam", entry.getReason());
        assertTrue(entry.isAuto());
        assertFalse(entry.isActive());
    }

    @Test
    void constructor_defaultsActiveToTrue() {
        BanEntry entry = new BanEntry("alice", "admin", 0L, "spam", false);
        assertTrue(entry.isActive());
    }

    @Test
    void withEndTime_returnsNewInstance() {
        BanEntry original = new BanEntry("alice", "admin", 1000L, "spam", false);
        BanEntry extended = original.withEndTime(2000L);
        assertEquals(1000L, original.getEndTime()); // 不变
        assertEquals(2000L, extended.getEndTime());
        assertEquals(original.getTarget(), extended.getTarget());
    }

    @Test
    void withReason_returnsNewInstance() {
        BanEntry original = new BanEntry("alice", "admin", 0L, "old", false);
        BanEntry updated = original.withReason("new");
        assertEquals("old", original.getReason());
        assertEquals("new", updated.getReason());
    }

    @Test
    void withAuto_returnsNewInstance() {
        BanEntry original = new BanEntry("alice", "admin", 0L, "r", false);
        BanEntry updated = original.withAuto(true);
        assertFalse(original.isAuto());
        assertTrue(updated.isAuto());
    }

    @Test
    void withActive_returnsNewInstance() {
        BanEntry original = new BanEntry("alice", "admin", 0L, "r", false, true);
        BanEntry deactivated = original.withActive(false);
        assertTrue(original.isActive());
        assertFalse(deactivated.isActive());
    }

    @Test
    void isExpired_checksCurrentTime() {
        BanEntry past = new BanEntry("alice", "admin", 1L, "r", false); // 1970+1ms
        BanEntry future = new BanEntry("alice", "admin", Long.MAX_VALUE, "r", false);
        assertTrue(past.isExpired());
        assertFalse(future.isExpired());
    }

    @Test
    void getRemainingTime_saturatesAtZero() {
        BanEntry past = new BanEntry("alice", "admin", 1L, "r", false);
        assertEquals(0L, past.getRemainingTime());
    }

    @Test
    void fromString_parsesValidFormat() {
        BanEntry entry = BanEntry.fromString("alice:admin:1234567890:spam:true:false");
        assertEquals("alice", entry.getTarget());
        assertEquals(1234567890L, entry.getTime());
        assertTrue(entry.isAuto());
        assertFalse(entry.isActive());
    }

    @Test
    void fromString_rejectsInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> BanEntry.fromString("only:three:parts"));
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThrows(NullPointerException.class, () -> new BanEntry(null, "s", 0L, "r", false));
        assertThrows(NullPointerException.class, () -> new BanEntry("t", null, 0L, "r", false));
        assertThrows(NullPointerException.class, () -> new BanEntry("t", "s", 0L, null, false));
    }
}