package com.mokatest.platform.demos.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mokatest.platform.demos.ai.domain.AiGenerationRecord;
import com.mokatest.platform.demos.ai.domain.AiAdoptionDetail;
import com.mokatest.platform.demos.ai.mapper.AiAdoptionDetailMapper;
import com.mokatest.platform.demos.ai.mapper.AiGenerationRecordMapper;
import com.mokatest.platform.demos.ai.service.AiGenerationRecordService;
import com.mokatest.platform.demos.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 生成记录服务实现（JSON 统一用 Jackson，无 fastjson 依赖）
 */
@Service
public class AiGenerationRecordServiceImpl implements AiGenerationRecordService {

    private static final int EXPIRE_HOURS = 24;
    private static final ObjectMapper OM = new ObjectMapper();

    @Autowired
    private AiGenerationRecordMapper recordMapper;

    @Autowired
    private AiAdoptionDetailMapper adoptionDetailMapper;

    // ==================== Jackson helpers ====================

    private static String toJson(Object obj) {
        try {
            return OM.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static List<Map<String, Object>> parseArr(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return OM.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static Object parseAny(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return OM.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getStr(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static Integer getInt(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception e) { return null; }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getList(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        if (v instanceof List) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object o : (List<?>) v) {
                if (o instanceof Map) {
                    result.add((Map<String, Object>) o);
                }
            }
            return result;
        }
        return null;
    }

    // ==================== 业务方法 ====================

    @Override
    public AiGenerationRecord create(Integer projectId, Integer teamId, String userId,
                                     String scene, Long entityId, String inputSummary) {
        if (projectId == null) {
            throw new BusinessException("缺少项目上下文，无法创建生成记录");
        }
        AiGenerationRecord record = new AiGenerationRecord();
        record.setRecordNo(UUID.randomUUID().toString().replace("-", ""));
        record.setUserId(userId);
        record.setTeamId(teamId);
        record.setProjectId(projectId);
        record.setScene(scene);
        record.setEntityId(entityId);
        record.setInputSummary(inputSummary);
        record.setStatus(AiGenerationRecord.STATUS_ACTIVE);
        Date now = new Date();
        record.setCreateTime(now);
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.HOUR_OF_DAY, EXPIRE_HOURS);
        record.setExpireTime(cal.getTime());
        recordMapper.insert(record);
        return record;
    }

    @Override
    public AiGenerationRecord requireValid(String recordNo, Integer projectId) {
        if (recordNo == null || recordNo.isEmpty()) {
            throw new BusinessException("缺少生成记录编号");
        }
        AiGenerationRecord record = recordMapper.selectOne(
                new QueryWrapper<AiGenerationRecord>().eq("record_no", recordNo));
        if (record == null || !record.getProjectId().equals(projectId)) {
            throw new BusinessException("生成记录不存在");
        }
        if (AiGenerationRecord.STATUS_EXPIRED.equals(record.getStatus())
                || (record.getExpireTime() != null && record.getExpireTime().before(new Date()))) {
            throw new BusinessException("生成记录已过期，请重新生成");
        }
        return record;
    }

    @Override
    public void appendSnapshot(String recordNo, Integer projectId, String outputSnapshot) {
        if (outputSnapshot == null) {
            return;
        }
        AiGenerationRecord record = requireValid(recordNo, projectId);
        AiGenerationRecord update = new AiGenerationRecord();
        update.setId(record.getId());
        update.setOutputSnapshot(outputSnapshot);
        recordMapper.updateById(update);
    }

    @Override
    public void appendRound(String recordNo, Integer projectId, String instruction, String draftsJson) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        List<Map<String, Object>> rounds = parseRounds(record.getOutputSnapshot());
        Map<String, Object> round = new LinkedHashMap<>();
        round.put("round", rounds.size() + 1);
        round.put("instruction", instruction);
        round.put("time", System.currentTimeMillis());
        round.put("drafts", draftsJson == null ? new ArrayList<>() : parseAny(draftsJson));
        rounds.add(round);
        AiGenerationRecord update = new AiGenerationRecord();
        update.setId(record.getId());
        update.setOutputSnapshot(toJson(rounds));
        recordMapper.updateById(update);
    }

    @Override
    public int beginRound(String recordNo, Integer projectId, String instruction) {
        return beginRound(recordNo, projectId, instruction, "gen");
    }

    @Override
    public int beginRound(String recordNo, Integer projectId, String instruction, String type) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        List<Map<String, Object>> rounds = parseRounds(record.getOutputSnapshot());
        Map<String, Object> active = findLatestGenerating(rounds);
        if (active != null) {
            throw new BusinessException("上一轮生成进行中，请等待完成或停止后再试");
        }
        Map<String, Object> round = new LinkedHashMap<>();
        round.put("round", rounds.size() + 1);
        round.put("type", type == null ? "gen" : type);
        round.put("instruction", instruction);
        round.put("time", System.currentTimeMillis());
        round.put("status", "GENERATING");
        round.put("drafts", new ArrayList<>());
        rounds.add(round);
        saveRounds(record, rounds);
        return getInt(round, "round");
    }

    @Override
    public void finishQaRound(String recordNo, Integer projectId, String answer) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        List<Map<String, Object>> rounds = parseRounds(record.getOutputSnapshot());
        Map<String, Object> target = findLatestGenerating(rounds);
        if (target == null) {
            target = new LinkedHashMap<>();
            target.put("round", rounds.size() + 1);
            target.put("time", System.currentTimeMillis());
            rounds.add(target);
        }
        target.put("type", "qa");
        target.put("status", "DONE");
        target.put("rawText", answer);
        target.put("drafts", new ArrayList<>());
        target.remove("error");
        saveRounds(record, rounds);
    }

    @Override
    public void finishRound(String recordNo, Integer projectId, String draftsJson) {
        finishRound(recordNo, projectId, draftsJson, null);
    }

    @Override
    public void finishRound(String recordNo, Integer projectId, String draftsJson, String rawText) {
        finishRound(recordNo, projectId, draftsJson, rawText, null);
    }

    @Override
    public void finishRound(String recordNo, Integer projectId, String draftsJson, String rawText,
                            String uncertaintiesJson) {
        finishRound(recordNo, projectId, draftsJson, rawText, uncertaintiesJson, null);
    }

    @Override
    public void finishRound(String recordNo, Integer projectId, String draftsJson, String rawText,
                            String uncertaintiesJson, String citationsJson) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        List<Map<String, Object>> rounds = parseRounds(record.getOutputSnapshot());
        Map<String, Object> target = findLatestGenerating(rounds);
        if (target == null) {
            target = new LinkedHashMap<>();
            target.put("round", rounds.size() + 1);
            target.put("time", System.currentTimeMillis());
            rounds.add(target);
        }
        target.put("status", "DONE");
        target.put("drafts", draftsJson == null ? new ArrayList<>() : parseAny(draftsJson));
        if (rawText != null) {
            target.put("rawText", rawText);
        }
        if (uncertaintiesJson != null) {
            target.put("uncertainties", parseAny(uncertaintiesJson));
        }
        if (citationsJson != null) {
            target.put("citations", parseAny(citationsJson));
        }
        target.remove("error");
        saveRounds(record, rounds);
    }

    @Override
    public void updateRoundRawText(String recordNo, Integer projectId, String rawText) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        List<Map<String, Object>> rounds = parseRounds(record.getOutputSnapshot());
        Map<String, Object> target = findLatestGenerating(rounds);
        if (target == null) {
            return;
        }
        target.put("rawText", rawText);
        saveRounds(record, rounds);
    }

    @Override
    public void stopRound(String recordNo, Integer projectId, String rawText) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        List<Map<String, Object>> rounds = parseRounds(record.getOutputSnapshot());
        Map<String, Object> target = findLatestGenerating(rounds);
        if (target == null) {
            return;
        }
        target.put("status", "STOPPED");
        if (rawText != null) {
            target.put("rawText", rawText);
        }
        saveRounds(record, rounds);
    }

    @Override
    public void removeLastRound(String recordNo, Integer projectId) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        List<Map<String, Object>> rounds = parseRounds(record.getOutputSnapshot());
        if (!rounds.isEmpty()) {
            rounds.remove(rounds.size() - 1);
        }
        saveRounds(record, rounds);
    }

    @Override
    public void updateRoundDrafts(String recordNo, Integer projectId, String draftsJson) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        List<Map<String, Object>> rounds = parseRounds(record.getOutputSnapshot());
        if (rounds.isEmpty()) return;
        Map<String, Object> target = rounds.get(rounds.size() - 1);
        if (target == null) return;
        target.put("drafts", draftsJson == null ? new ArrayList<>() : parseAny(draftsJson));
        saveRounds(record, rounds);
    }

    private void saveRounds(AiGenerationRecord record, List<Map<String, Object>> rounds) {
        String json = toJson(rounds);
        AiGenerationRecord update = new AiGenerationRecord();
        update.setId(record.getId());
        update.setOutputSnapshot(json);
        update.setVersion(record.getVersion());
        int rows = recordMapper.updateById(update);
        if (rows == 0) {
            throw new BusinessException("记录已被其他操作修改，请刷新后重试");
        }
        record.setOutputSnapshot(json);
        record.setVersion(record.getVersion() == null ? 1 : record.getVersion() + 1);
    }

    @Override
    public void delete(String recordNo, Integer projectId) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        recordMapper.deleteById(record.getId());
    }

    @Override
    public void failRound(String recordNo, Integer projectId, String errorMsg, String rawText) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        List<Map<String, Object>> rounds = parseRounds(record.getOutputSnapshot());
        Map<String, Object> target = findLatestGenerating(rounds);
        if (target == null) {
            target = new LinkedHashMap<>();
            target.put("round", rounds.size() + 1);
            target.put("time", System.currentTimeMillis());
            rounds.add(target);
        }
        target.put("status", "FAILED");
        target.put("error", errorMsg == null ? "生成失败" : errorMsg);
        target.put("drafts", new ArrayList<>());
        if (rawText != null && !rawText.isEmpty()) {
            target.put("rawText", rawText);
        }
        AiGenerationRecord update = new AiGenerationRecord();
        update.setId(record.getId());
        update.setOutputSnapshot(toJson(rounds));
        recordMapper.updateById(update);
    }

    private Map<String, Object> findLatestGenerating(List<Map<String, Object>> rounds) {
        for (int i = rounds.size() - 1; i >= 0; i--) {
            Map<String, Object> round = rounds.get(i);
            if (round != null && "GENERATING".equals(round.get("status"))) {
                return round;
            }
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> parseRounds(String outputSnapshot) {
        if (outputSnapshot == null || outputSnapshot.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> arr = OM.readValue(outputSnapshot,
                    new TypeReference<List<Map<String, Object>>>() {});
            if (arr.isEmpty()) {
                return arr;
            }
            Map<String, Object> first = arr.get(0);
            // 新格式：元素是含 drafts 键的对象 → 已是轮次数组
            if (first != null && first.containsKey("drafts")) {
                return arr;
            }
            // 旧格式：单层草稿数组 → 包装为第 1 轮
            List<Map<String, Object>> rounds = new ArrayList<>();
            Map<String, Object> round = new LinkedHashMap<>();
            round.put("round", 1);
            round.put("instruction", null);
            round.put("time", null);
            round.put("drafts", arr);
            rounds.add(round);
            return rounds;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public void recordAdoption(String recordNo, Integer projectId, String adoptedDetail) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        AiGenerationRecord update = new AiGenerationRecord();
        update.setId(record.getId());
        update.setAdoptedDetail(adoptedDetail);
        recordMapper.updateById(update);
    }

    @Override
    public java.util.Set<String> parseAdoptedDraftIds(String recordNo) {
        // BUG#10 修复：交叉验证 test_case 表，只返回 caseId 仍存在的 draftId
        List<String> validIds = adoptionDetailMapper.selectValidDraftIds(recordNo);
        return new java.util.HashSet<>(validIds);
    }

    @Override
    public void mergeAdoption(String recordNo, Integer projectId, List<Map<String, Object>> entries) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        if (entries != null) {
            for (Map<String, Object> entry : entries) {
                String draftId = getStr(entry, "draftId");
                if (draftId == null || draftId.isEmpty()) {
                    continue;
                }
                AiAdoptionDetail detail = new AiAdoptionDetail();
                detail.setRecordNo(recordNo);
                detail.setDraftId(draftId);
                detail.setCaseId(getInt(entry, "caseId"));
                detail.setCreateTime(new Date());
                try {
                    adoptionDetailMapper.insert(detail);
                } catch (org.springframework.dao.DuplicateKeyException dke) {
                    // 唯一索引兜底：并发重复时跳过
                }
            }
        }
        List<Map<String, Object>> merged = parseAdoptionEntries(record.getAdoptedDetail());
        if (entries != null) {
            merged.addAll(entries);
        }
        AiGenerationRecord update = new AiGenerationRecord();
        update.setId(record.getId());
        update.setAdoptedDetail(toJson(merged));
        recordMapper.updateById(update);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseAdoptionEntries(String adoptedDetail) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (adoptedDetail == null || adoptedDetail.trim().isEmpty()) {
            return entries;
        }
        try {
            Object parsed = OM.readValue(adoptedDetail, Object.class);
            if (parsed instanceof List) {
                for (Object o : (List<?>) parsed) {
                    if (o instanceof Map) {
                        entries.add((Map<String, Object>) o);
                    }
                }
                return entries;
            }
            if (parsed instanceof Map) {
                Object caseIds = ((Map<String, Object>) parsed).get("caseIds");
                if (caseIds instanceof List) {
                    for (Object cid : (List<?>) caseIds) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("caseId", cid);
                        entries.add(entry);
                    }
                }
            }
        } catch (Exception e) {
            // 无法解析视为无明细
        }
        return entries;
    }

    @Override
    public List<AiGenerationRecord> listByEntity(Integer projectId, String scene, Long entityId) {
        if (projectId == null) {
            return List.of();
        }
        QueryWrapper<AiGenerationRecord> qw = new QueryWrapper<>();
        qw.eq("project_id", projectId)
                .eq("scene", scene)
                .orderByDesc("id")
                .last("limit 20");
        if (entityId == null) {
            qw.isNull("entity_id");
        } else {
            qw.eq("entity_id", entityId);
        }
        return recordMapper.selectList(qw);
    }

    @Override
    public void updateStage(String recordNo, Integer projectId, String stage, String stagePayloadJson) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        record.setCurrentStage(stage);
        if (stagePayloadJson != null) {
            record.setStagePayload(stagePayloadJson);
        }
        recordMapper.updateById(record);
    }

    @Override
    public AiGenerationRecord getByRecordNo(String recordNo, Integer projectId) {
        if (recordNo == null || recordNo.isEmpty()) {
            return null;
        }
        AiGenerationRecord record = recordMapper.selectOne(
                new QueryWrapper<AiGenerationRecord>().eq("record_no", recordNo));
        if (record == null || !record.getProjectId().equals(projectId)) {
            return null;
        }
        return record;
    }

    @Override
    public void archive(String recordNo, Integer projectId) {
        AiGenerationRecord record = getByRecordNo(recordNo, projectId);
        if (record == null) return;
        record.setStatus(AiGenerationRecord.STATUS_EXPIRED);
        record.setCurrentStage("ARCHIVED");
        recordMapper.updateById(record);
    }
}
