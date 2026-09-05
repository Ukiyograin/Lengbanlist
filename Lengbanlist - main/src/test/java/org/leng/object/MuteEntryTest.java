package org.leng.object;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MuteEntryTest {

    @Test
    void constructor_storesFields() {
        MuteEntry entry = new MuteEntry("alice", "admin", 1000L, "spam");
        assertEquals("alice", entry.target());
        assertEquals("admin", entry.staff());
        assertEquals(1000L, entry.time());
        assertEquals(1000L, entry.getEndTime());
        assertEquals("spam", entry.reason());
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThrows(NullPointerException.class, () -> new MuteEntry(null, "s", 0L, "r"));
        assertThrows(NullPointerException.class, () -> new MuteEntry("t", null, 0L, "r"));
        assertThrows(NullPointerException.class, () -> new MuteEntry("t", "s", 0L, null));
    }

    @Test
    void toString_includesAllFields() {
        MuteEntry entry = new MuteEntry("alice", "admin", 1000L, "spam");
        assertEquals("alice:admin:1000:spam", entry.toString());
    }
}