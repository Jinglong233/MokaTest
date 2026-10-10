package com.mokatest.platform.demos.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.domain.AiGenerationRecord;
import com.mokatest.platform.demos.ai.mapper.AiGenerationRecordMapper;
import com.mokatest.platform.demos.ai.mapper.AiAdoptionDetailMapper;
import com.mokatest.platform.demos.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 生成记录锚点校验单元测试（会话隔离核心规则）
 * JSON 统一用 Jackson（List<Map>），无 fastjson 依赖。
 */
class AiGenerationRecordServiceImplTest {

    private AiGenerationRecordServiceImpl service;
    private AiGenerationRecordMapper mapper;
    private AiAdoptionDetailMapper adoptionDetailMapper;

    @BeforeEach
    void setUp() {
        service = new AiGenerationRecordServiceImpl();
        mapper = Mockito.mock(AiGenerationRecordMapper.class);
        ReflectionTestUtils.setField(service, "recordMapper", mapper);
        adoptionDetailMapper = Mockito.mock(AiAdoptionDetailMapper.class);
        ReflectionTestUtils.setField(service, "adoptionDetailMapper", adoptionDetailMapper);
        Mockito.lenient().when(mapper.updateById(Mockito.any(AiGenerationRecord.class))).thenReturn(1);
    }

    // ==================== helpers ====================

    private static String str(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static int roundNo(Map<String, Object> m) {
        if (m == null) return 0;
        Object v = m.get("round");
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> drafts(Map<String, Object> m) {
        if (m == null) return List.of();
        Object v = m.get("drafts");
        if (v instanceof List) {
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Object o : (List<?>) v) {
                if (o instanceof Map) result.add((Map<String, Object>) o);
            }
            return result;
        }
        return List.of();
    }

    private AiGenerationRecord record(Integer projectId, String status, Date expireTime) {
        AiGenerationRecord r = new AiGenerationRecord();
        r.setId(1L);
        r.setRecordNo("abc123");
        r.setProjectId(projectId);
        r.setScene("GENERATE_CASE");
        r.setEntityId(99L);
        r.setStatus(status);
        r.setExpireTime(expireTime);
        return r;
    }

    private Date hoursLater(int h) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, h);
        return cal.getTime();
    }

    // ==================== 锚点校验 ====================

    @Test
    void validRecordPasses() {
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(24)));
        AiGenerationRecord r = service.requireValid("abc123", 1);
        assertEquals(99L, r.getEntityId());
    }

    @Test
    void crossProjectRecordRejected() {
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(24)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireValid("abc123", 2));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void unknownRecordRejected() {
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(null);
        assertThrows(BusinessException.class, () -> service.requireValid("nope", 1));
    }

    @Test
    void expiredStatusRejected() {
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(record(1, AiGenerationRecord.STATUS_EXPIRED, hoursLater(24)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireValid("abc123", 1));
        assertTrue(ex.getMessage().contains("过期"));
    }

    @Test
    void pastExpireTimeRejected() {
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(-1)));
        assertThrows(BusinessException.class, () -> service.requireValid("abc123", 1));
    }

    @Test
    void blankRecordNoRejected() {
        assertThrows(BusinessException.class, () -> service.requireValid("", 1));
        assertThrows(BusinessException.class, () -> service.requireValid(null, 1));
    }

    @Test
    void createRequiresProject() {
        assertThrows(BusinessException.class,
                () -> service.create(null, 1, "u1", "GENERATE_CASE", 1L, "summary"));
    }

    @Test
    void createSetsExpireTime24h() {
        AiGenerationRecord r = service.create(1, 1, "u1", "GENERATE_CASE", 1L, "summary");
        assertNotNull(r.getRecordNo());
        assertEquals(AiGenerationRecord.STATUS_ACTIVE, r.getStatus());
        assertNotNull(r.getExpireTime());
        long diffMs = r.getExpireTime().getTime() - r.getCreateTime().getTime();
        assertEquals(24 * 3600 * 1000L, diffMs);
        Mockito.verify(mapper).insert(Mockito.any(AiGenerationRecord.class));
    }

    // ==================== 轮次解析 ====================

    @Test
    void parseRoundsEmptyAndLegacy() {
        assertTrue(service.parseRounds(null).isEmpty());
        assertTrue(service.parseRounds("").isEmpty());
        assertTrue(service.parseRounds("not-json").isEmpty());
        // 旧格式：单层草稿数组 → 包装为第 1 轮
        List<Map<String, Object>> rounds = service.parseRounds("[{\"caseName\":\"A\"}]");
        assertEquals(1, rounds.size());
        assertEquals(1, roundNo(rounds.get(0)));
        assertEquals(1, drafts(rounds.get(0)).size());
    }

    @Test
    void parseRoundsNewFormat() {
        List<Map<String, Object>> rounds = service.parseRounds(
                "[{\"round\":1,\"instruction\":\"x\",\"drafts\":[{\"caseName\":\"A\"}]}]");
        assertEquals(1, rounds.size());
        assertEquals("x", str(rounds.get(0), "instruction"));
    }

    @Test
    void appendRoundAccumulates() {
        AiGenerationRecord existing = record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(24));
        existing.setOutputSnapshot("[{\"round\":1,\"instruction\":\"first\",\"drafts\":[{\"caseName\":\"A\"}]}]");
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(existing);

        service.appendRound("abc123", 1, "second", "[{\"caseName\":\"B\"}]");

        org.mockito.ArgumentCaptor<AiGenerationRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerationRecord.class);
        Mockito.verify(mapper).updateById(captor.capture());
        List<Map<String, Object>> rounds = service.parseRounds(captor.getValue().getOutputSnapshot());
        assertEquals(2, rounds.size());
        assertEquals("second", str(rounds.get(1), "instruction"));
        assertEquals("B", str(drafts(rounds.get(1)).get(0), "caseName"));
    }

    @Test
    void appendRoundOnLegacySnapshot() {
        AiGenerationRecord existing = record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(24));
        existing.setOutputSnapshot("[{\"caseName\":\"A\"}]");
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(existing);

        service.appendRound("abc123", 1, "next", "[{\"caseName\":\"B\"}]");

        org.mockito.ArgumentCaptor<AiGenerationRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerationRecord.class);
        Mockito.verify(mapper).updateById(captor.capture());
        List<Map<String, Object>> rounds = service.parseRounds(captor.getValue().getOutputSnapshot());
        assertEquals(2, rounds.size(), "旧格式应先包装为第 1 轮再追加");
        assertEquals(2, roundNo(rounds.get(1)));
    }

    @Test
    void parseAdoptedDraftIdsFromEntries() {
        assertTrue(service.parseAdoptedDraftIds(null).isEmpty());
        assertTrue(service.parseAdoptedDraftIds("").isEmpty());
        com.mokatest.platform.demos.ai.domain.AiAdoptionDetail d1 = new com.mokatest.platform.demos.ai.domain.AiAdoptionDetail();
        d1.setDraftId("d1");
        com.mokatest.platform.demos.ai.domain.AiAdoptionDetail d2 = new com.mokatest.platform.demos.ai.domain.AiAdoptionDetail();
        d2.setDraftId("d2");
        com.mokatest.platform.demos.ai.domain.AiAdoptionDetail d3 = new com.mokatest.platform.demos.ai.domain.AiAdoptionDetail();
        Mockito.when(adoptionDetailMapper.selectList(Mockito.any()))
                .thenReturn(java.util.List.of(d1, d2, d3));
        java.util.Set<String> ids = service.parseAdoptedDraftIds("abc123");
        assertEquals(2, ids.size());
        assertTrue(ids.contains("d1"));
        assertTrue(ids.contains("d2"));
    }

    @Test
    void legacyAdoptionFormatMigratesWithoutDraftIds() {
        List<Map<String, Object>> entries = service.parseAdoptionEntries("{\"caseIds\":[1,2],\"count\":2}");
        assertEquals(2, entries.size());
        assertEquals("1", str(entries.get(0), "caseId"));
        assertTrue(service.parseAdoptedDraftIds("{\"caseIds\":[1,2],\"count\":2}").isEmpty());
    }

    @Test
    void beginFinishRoundLifecycle() {
        AiGenerationRecord existing = record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(24));
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(existing);

        int roundNo = service.beginRound("abc123", 1, "给我5条用例");
        assertEquals(1, roundNo);
        org.mockito.ArgumentCaptor<AiGenerationRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerationRecord.class);
        Mockito.verify(mapper).updateById(captor.capture());
        List<Map<String, Object>> rounds = service.parseRounds(captor.getValue().getOutputSnapshot());
        assertEquals("GENERATING", str(rounds.get(0), "status"));
        assertEquals("给我5条用例", str(rounds.get(0), "instruction"));

        existing.setOutputSnapshot(captor.getValue().getOutputSnapshot());
        service.finishRound("abc123", 1, "[{\"caseName\":\"A\"}]");
        Mockito.verify(mapper, Mockito.times(2)).updateById(captor.capture());
        rounds = service.parseRounds(captor.getValue().getOutputSnapshot());
        assertEquals("DONE", str(rounds.get(0), "status"));
        assertEquals(1, drafts(rounds.get(0)).size());
    }

    @Test
    void failRoundMarksError() {
        AiGenerationRecord existing = record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(24));
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(existing);

        service.failRound("abc123", 1, "连接超时", null);
        org.mockito.ArgumentCaptor<AiGenerationRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerationRecord.class);
        Mockito.verify(mapper).updateById(captor.capture());
        List<Map<String, Object>> rounds = service.parseRounds(captor.getValue().getOutputSnapshot());
        assertEquals("FAILED", str(rounds.get(0), "status"));
        assertEquals("连接超时", str(rounds.get(0), "error"));
    }

    @Test
    void mergeAdoptionAccumulatesAndMigratesLegacy() {
        AiGenerationRecord existing = record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(24));
        existing.setAdoptedDetail("{\"caseIds\":[7],\"count\":1}");
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(existing);

        java.util.List<java.util.Map<String, Object>> newEntries = new java.util.ArrayList<>();
        java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("draftId", "d9");
        entry.put("caseId", 9);
        newEntries.add(entry);
        service.mergeAdoption("abc123", 1, newEntries);

        org.mockito.ArgumentCaptor<AiGenerationRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerationRecord.class);
        Mockito.verify(mapper).updateById(captor.capture());
        String merged = captor.getValue().getAdoptedDetail();
        List<Map<String, Object>> entries = service.parseAdoptionEntries(merged);
        assertEquals(2, entries.size(), "旧 1 条 + 新 1 条");
        org.mockito.ArgumentCaptor<com.mokatest.platform.demos.ai.domain.AiAdoptionDetail> detailCaptor =
                org.mockito.ArgumentCaptor.forClass(com.mokatest.platform.demos.ai.domain.AiAdoptionDetail.class);
        Mockito.verify(adoptionDetailMapper).insert(detailCaptor.capture());
        assertEquals("d9", detailCaptor.getValue().getDraftId());
    }
}
