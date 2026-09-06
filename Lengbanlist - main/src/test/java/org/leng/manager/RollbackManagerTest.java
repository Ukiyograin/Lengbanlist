package org.leng.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.object.AuditEntry;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RollbackManager.extractWarnIds 单元测试 —— 从审计日志 reason 解析警告 ID 列表。
 *
 * <p>这是 rollbackUnwarn 的关键解析步骤。reason 形如 "警告ID: id1,警告ID: id2,..."。
 * 方法提取并返回所有 ID,无匹配返回 null。
 */
@ExtendWith(MockitoExtension.class)
class RollbackManagerTest {

    private final RollbackManager manager = new RollbackManager(null);

    private AuditEntry entryWithReason(String reason) {
        return new AuditEntry(0, System.currentTimeMillis(), "actor", "取消警告", "target", reason, true, "");
    }

    @Test
    void extractWarnIds_singleId_returnsSingleElementList() {
        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: 42"));
        assertNotNull(ids);
        assertEquals(1, ids.size());
        assertEquals("42", ids.get(0));
    }

    @Test
    void extractWarnIds_multipleIdsCommaDelimited_returnsAllInOrder() {
        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: 1,警告ID: 2,警告ID: 3"));
        assertNotNull(ids);
        assertEquals(List.of("1", "2", "3"), ids);
    }

    @Test
    void extractWarnIds_multipleIdsSpaceDelimited_returnsAll() {
        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: 1 警告ID: 2 警告ID: 3"));
        assertNotNull(ids);
        assertEquals(List.of("1", "2", "3"), ids);
    }

    @Test
    void extractWarnIds_mixedDelimiters_handlesBoth() {
        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: 1,警告ID: 2 警告ID: 3"));
        assertNotNull(ids);
        assertEquals(List.of("1", "2", "3"), ids);
    }

    @Test
    void extractWarnIds_emptyReason_returnsNull() {
        assertNull(manager.extractWarnIds(entryWithReason("")));
    }

    @Test
    void extractWarnIds_nullReason_returnsNull() {
        assertNull(manager.extractWarnIds(entryWithReason(null)));
    }

    @Test
    void extractWarnIds_reasonWithoutPrefix_returnsNull() {
        assertNull(manager.extractWarnIds(entryWithReason("其他原因")));
    }

    @Test
    void extractWarnIds_trailingCommaWithEmptyId_filtersEmpty() {
        // "警告ID: 1,警告ID: " 末尾空 ID 应被过滤
        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: 1,警告ID: "));
        assertNotNull(ids);
        assertEquals(List.of("1"), ids);
    }

    @Test
    void extractWarnIds_nonNumericId_preservedAsString() {
        // rollback 按字符串 ID 匹配,允许非数字 ID
        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: abc-123"));
        assertNotNull(ids);
        assertEquals(List.of("abc-123"), ids);
    }

    @Test
    void extractWarnIds_prefixOnly_returnsNull() {
        // 只有前缀没有 ID
        List<String> ids = manager.extractWarnIds(entryWithReason("警告ID: "));
        // 空 ID 被过滤,最终列表为空 → 返回 null
        assertNull(ids);
    }
}