package org.leng.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TimeUtilsTest {

    // ============ 单位转换 ============

    @Test
    void unitConversions_areExact() {
        assertEquals(1000L, TimeUtils.secondsToMillis(1));
        assertEquals(60_000L, TimeUtils.minutesToMillis(1));
        assertEquals(3_600_000L, TimeUtils.hoursToMillis(1));
        assertEquals(86_400_000L, TimeUtils.daysToMillis(1));
        assertEquals(7L * 86_400_000L, TimeUtils.weeksToMillis(1));
        assertEquals(30L * 86_400_000L, TimeUtils.monthsToMillis(1));
        assertEquals(365L * 86_400_000L, TimeUtils.yearsToMillis(1));
    }

    // ============ parseDurationToMillis ============

    @Test
    void parseDuration_acceptsAllUnits() {
        assertEquals(5_000L, TimeUtils.parseDurationToMillis("5s"));
        assertEquals(5 * 60_000L, TimeUtils.parseDurationToMillis("5m"));
        assertEquals(5 * 3_600_000L, TimeUtils.parseDurationToMillis("5h"));
        assertEquals(5L * 86_400_000L, TimeUtils.parseDurationToMillis("5d"));
        assertEquals(5L * 7 * 86_400_000L, TimeUtils.parseDurationToMillis("5w"));
        assertEquals(5L * 30 * 86_400_000L, TimeUtils.parseDurationToMillis("5M"));
        assertEquals(5L * 365 * 86_400_000L, TimeUtils.parseDurationToMillis("5y"));
    }

    @Test
    void parseDuration_acceptsCaseInsensitiveUnits() {
        assertEquals(5_000L, TimeUtils.parseDurationToMillis("5S"));
        assertEquals(5 * 3_600_000L, TimeUtils.parseDurationToMillis("5H"));
        assertEquals(5L * 86_400_000L, TimeUtils.parseDurationToMillis("5D"));
    }

    @Test
    void parseDuration_acceptsForeverKeywords() {
        assertEquals(Long.MAX_VALUE, TimeUtils.parseDurationToMillis("forever"));
        assertEquals(Long.MAX_VALUE, TimeUtils.parseDurationToMillis("perm"));
        assertEquals(Long.MAX_VALUE, TimeUtils.parseDurationToMillis("permanent"));
        assertEquals(Long.MAX_VALUE, TimeUtils.parseDurationToMillis("FOREVER"));
    }

    @Test
    void parseDuration_returnsNegativeForInvalid() {
        assertEquals(-1L, TimeUtils.parseDurationToMillis(null));
        assertEquals(-1L, TimeUtils.parseDurationToMillis(""));
        assertEquals(-1L, TimeUtils.parseDurationToMillis("abc"));
        assertEquals(-1L, TimeUtils.parseDurationToMillis("5")); // 缺单位 → parseLong("") 抛异常
        assertEquals(-1L, TimeUtils.parseDurationToMillis("5x")); // 'x' 未知单位 → switch default
        // 负数实际上会被接受,产生负 duration;calculateEndTime 会兜底为当前时间
        // 这里只验证确实走 switch 计算(便于发现重构时行为变更)
        assertEquals(-432000000L, TimeUtils.parseDurationToMillis("-5d"));
    }

    // ============ isValidTimeFormat ============

    @Test
    void isValidTimeFormat_acceptsValidFormats() {
        assertTrue(TimeUtils.isValidTimeFormat("30s"));
        assertTrue(TimeUtils.isValidTimeFormat("5m"));
        assertTrue(TimeUtils.isValidTimeFormat("7d"));
        assertTrue(TimeUtils.isValidTimeFormat("forever"));
        assertTrue(TimeUtils.isValidTimeFormat("auto"));
    }

    @Test
    void isValidTimeFormat_rejectsInvalid() {
        assertFalse(TimeUtils.isValidTimeFormat(null));
        assertFalse(TimeUtils.isValidTimeFormat(""));
        assertFalse(TimeUtils.isValidTimeFormat("5"));
        assertFalse(TimeUtils.isValidTimeFormat("5x"));
        assertFalse(TimeUtils.isValidTimeFormat("abc"));
        assertFalse(TimeUtils.isValidTimeFormat("-5d"));
    }

    // ============ calculateEndTime ============

    @Test
    void calculateEndTime_handlesMaxValue() {
        assertEquals(Long.MAX_VALUE, TimeUtils.calculateEndTime(Long.MAX_VALUE));
    }

    @Test
    void calculateEndTime_handlesZeroOrNegative() {
        long now = TimeUtils.currentTime();
        assertTrue(TimeUtils.calculateEndTime(0) >= now - 100); // 允许 ±100ms
        assertTrue(TimeUtils.calculateEndTime(-100) >= now - 200);
    }

    @Test
    void calculateEndTime_addsDuration() {
        long before = TimeUtils.currentTime();
        long end = TimeUtils.calculateEndTime(TimeUnit.HOURS.toMillis(1));
        long after = TimeUtils.currentTime();
        // end should be between before+1h and after+1h
        assertTrue(end >= before + TimeUnit.HOURS.toMillis(1) - 100);
        assertTrue(end <= after + TimeUnit.HOURS.toMillis(1) + 100);
    }

    @Test
    void calculateEndTime_saturatesOnOverflow() {
        long overflow = Long.MAX_VALUE - 1000L;
        assertEquals(Long.MAX_VALUE, TimeUtils.calculateEndTime(overflow));
    }

    // ============ formatDuration ============

    @Test
    void formatDuration_formatsEachUnit() {
        assertEquals("永久", TimeUtils.formatDuration(Long.MAX_VALUE));
        assertEquals("0秒", TimeUtils.formatDuration(0));
        assertEquals("0秒", TimeUtils.formatDuration(-100));
        assertEquals("30秒", TimeUtils.formatDuration(30_000L));
        assertEquals("5分钟", TimeUtils.formatDuration(5 * 60_000L));
        assertEquals("3小时", TimeUtils.formatDuration(3 * 3_600_000L));
        assertEquals("2天", TimeUtils.formatDuration(2L * 86_400_000L));
        assertEquals("2周", TimeUtils.formatDuration(14L * 86_400_000L));
        assertEquals("3个月", TimeUtils.formatDuration(90L * 86_400_000L));
        assertEquals("2年", TimeUtils.formatDuration(2L * 365 * 86_400_000L));
    }

    // ============ getRemainingTime ============

    @Test
    void getRemainingTime_handlesEdgeCases() {
        assertEquals("永久", TimeUtils.getRemainingTime(Long.MAX_VALUE));
        long past = System.currentTimeMillis() - 1000;
        assertEquals("已过期", TimeUtils.getRemainingTime(past));
    }
}