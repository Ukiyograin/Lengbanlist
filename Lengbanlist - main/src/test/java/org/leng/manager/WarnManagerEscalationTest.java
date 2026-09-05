package org.leng.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.leng.utils.TimeUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * WarnManager.calculateBanDuration 是纯函数（不依赖实例状态），
 * 但 WarnManager 构造依赖 Lengbanlist/DatabaseManager。
 * 用 Mockito 解耦后测纯逻辑。
 */
@ExtendWith(MockitoExtension.class)
class WarnManagerEscalationTest {

    @Mock Lengbanlist plugin;
    @Mock DatabaseManager db;
    WarnManager manager;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getDatabaseManager()).thenReturn(db);
        manager = new WarnManager(plugin);
    }

    @Test
    void calculateBanDuration_firstOffense_isOneDay() {
        assertEquals(TimeUtils.daysToMillis(1), manager.calculateBanDuration(1));
    }

    @Test
    void calculateBanDuration_progressionEscalates() {
        assertEquals(TimeUtils.daysToMillis(7), manager.calculateBanDuration(2));
        assertEquals(TimeUtils.daysToMillis(30), manager.calculateBanDuration(3));
        assertEquals(TimeUtils.daysToMillis(90), manager.calculateBanDuration(4));
        assertEquals(TimeUtils.daysToMillis(180), manager.calculateBanDuration(5));
    }

    @Test
    void calculateBanDuration_afterFiveOffenses_capsAtOneYear() {
        assertEquals(TimeUtils.daysToMillis(365), manager.calculateBanDuration(6));
        assertEquals(TimeUtils.daysToMillis(365), manager.calculateBanDuration(100));
    }

    @Test
    void calculateBanDuration_zeroOrNegative_isOneYear() {
        assertEquals(TimeUtils.daysToMillis(365), manager.calculateBanDuration(0));
        assertEquals(TimeUtils.daysToMillis(365), manager.calculateBanDuration(-1));
    }
}