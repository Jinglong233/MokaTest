package com.mokatest.platform.demos.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.domain.AiGenerationRecord;
import com.mokatest.platform.demos.ai.mapper.AiGenerationRecordMapper;
import com.mokatest.platform.demos.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 生成记录锚点校验单元测试（会话隔离核心规则）
 */
class AiGenerationRecordServiceImplTest {

    private AiGenerationRecordServiceImpl service;
    private AiGenerationRecordMapper mapper;

    @BeforeEach
    void setUp() {
        service = new AiGenerationRecordServiceImpl();
        mapper = Mockito.mock(AiGenerationRecordMapper.class);
        ReflectionTestUtils.setField(service, "recordMapper", mapper);
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

    @Test
    void validRecordPasses() {
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(24)));
        AiGenerationRecord r = service.requireValid("abc123", 1);
        assertEquals(99L, r.getEntityId());
    }

    @Test
    void crossProjectRecordRejected() {
        // 项目 B 的用户拿项目 A 的 recordNo → 统一报"不存在"，不泄露
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

    @Test
    void parseRoundsEmptyAndLegacy() {
        assertTrue(service.parseRounds(null).isEmpty());
        assertTrue(service.parseRounds("").isEmpty());
        assertTrue(service.parseRounds("not-json").isEmpty());
        // 旧格式：单层草稿数组 → 包装为第 1 轮
        com.alibaba.fastjson.JSONArray rounds = service.parseRounds("[{\"caseName\":\"A\"}]");
        assertEquals(1, rounds.size());
        assertEquals(1, rounds.getJSONObject(0).getIntValue("round"));
        assertEquals(1, rounds.getJSONObject(0).getJSONArray("drafts").size());
    }

    @Test
    void parseRoundsNewFormat() {
        com.alibaba.fastjson.JSONArray rounds = service.parseRounds(
                "[{\"round\":1,\"instruction\":\"x\",\"drafts\":[{\"caseName\":\"A\"}]}]");
        assertEquals(1, rounds.size());
        assertEquals("x", rounds.getJSONObject(0).getString("instruction"));
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
        com.alibaba.fastjson.JSONArray rounds = service.parseRounds(captor.getValue().getOutputSnapshot());
        assertEquals(2, rounds.size());
        assertEquals("second", rounds.getJSONObject(1).getString("instruction"));
        assertEquals("B", rounds.getJSONObject(1).getJSONArray("drafts").getJSONObject(0).getString("caseName"));
    }

    @Test
    void appendRoundOnLegacySnapshot() {
        AiGenerationRecord existing = record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(24));
        existing.setOutputSnapshot("[{\"caseName\":\"A\"}]"); // 旧格式
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(existing);

        service.appendRound("abc123", 1, "next", "[{\"caseName\":\"B\"}]");

        org.mockito.ArgumentCaptor<AiGenerationRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerationRecord.class);
        Mockito.verify(mapper).updateById(captor.capture());
        com.alibaba.fastjson.JSONArray rounds = service.parseRounds(captor.getValue().getOutputSnapshot());
        assertEquals(2, rounds.size(), "旧格式应先包装为第 1 轮再追加");
        assertEquals(2, rounds.getJSONObject(1).getIntValue("round"));
    }

    @Test
    void parseAdoptedDraftIdsFromEntries() {
        assertTrue(service.parseAdoptedDraftIds(null).isEmpty());
        assertTrue(service.parseAdoptedDraftIds("").isEmpty());
        java.util.Set<String> ids = service.parseAdoptedDraftIds(
                "[{\"draftId\":\"d1\",\"caseId\":10},{\"draftId\":\"d2\",\"caseId\":11},{\"caseId\":12}]");
        assertEquals(2, ids.size());
        assertTrue(ids.contains("d1"));
        assertTrue(ids.contains("d2"));
    }

    @Test
    void legacyAdoptionFormatMigratesWithoutDraftIds() {
        // 旧格式 {caseIds, count} → 迁移为 caseId 条目，draftId 集合为空（不拦截老记录）
        com.alibaba.fastjson.JSONArray entries = service.parseAdoptionEntries("{\"caseIds\":[1,2],\"count\":2}");
        assertEquals(2, entries.size());
        assertEquals(1, entries.getJSONObject(0).getIntValue("caseId"));
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
        com.alibaba.fastjson.JSONArray rounds = service.parseRounds(captor.getValue().getOutputSnapshot());
        assertEquals("GENERATING", rounds.getJSONObject(0).getString("status"));
        assertEquals("给我5条用例", rounds.getJSONObject(0).getString("instruction"));

        // 完成：GENERATING 轮被替换为 DONE
        existing.setOutputSnapshot(captor.getValue().getOutputSnapshot());
        service.finishRound("abc123", 1, "[{\"caseName\":\"A\"}]");
        Mockito.verify(mapper, Mockito.times(2)).updateById(captor.capture());
        rounds = service.parseRounds(captor.getValue().getOutputSnapshot());
        assertEquals("DONE", rounds.getJSONObject(0).getString("status"));
        assertEquals(1, rounds.getJSONObject(0).getJSONArray("drafts").size());
    }

    @Test
    void failRoundMarksError() {
        AiGenerationRecord existing = record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(24));
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(existing);

        service.failRound("abc123", 1, "连接超时");
        org.mockito.ArgumentCaptor<AiGenerationRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerationRecord.class);
        Mockito.verify(mapper).updateById(captor.capture());
        com.alibaba.fastjson.JSONArray rounds = service.parseRounds(captor.getValue().getOutputSnapshot());
        assertEquals("FAILED", rounds.getJSONObject(0).getString("status"));
        assertEquals("连接超时", rounds.getJSONObject(0).getString("error"));
    }

    @Test
    void mergeAdoptionAccumulatesAndMigratesLegacy() {        AiGenerationRecord existing = record(1, AiGenerationRecord.STATUS_ACTIVE, hoursLater(24));
        existing.setAdoptedDetail("{\"caseIds\":[7],\"count\":1}"); // 旧格式
        Mockito.when(mapper.selectOne(Mockito.<QueryWrapper<AiGenerationRecord>>any()))
                .thenReturn(existing);

        com.alibaba.fastjson.JSONArray newEntries = new com.alibaba.fastjson.JSONArray();
        com.alibaba.fastjson.JSONObject entry = new com.alibaba.fastjson.JSONObject();
        entry.put("draftId", "d9");
        entry.put("caseId", 9);
        newEntries.add(entry);
        service.mergeAdoption("abc123", 1, newEntries);

        org.mockito.ArgumentCaptor<AiGenerationRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AiGenerationRecord.class);
        Mockito.verify(mapper).updateById(captor.capture());
        String merged = captor.getValue().getAdoptedDetail();
        com.alibaba.fastjson.JSONArray entries = service.parseAdoptionEntries(merged);
        assertEquals(2, entries.size(), "旧 1 条 + 新 1 条");
        assertTrue(service.parseAdoptedDraftIds(merged).contains("d9"));
    }
}
