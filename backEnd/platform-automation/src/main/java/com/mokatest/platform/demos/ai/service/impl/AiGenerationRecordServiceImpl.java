package com.mokatest.platform.demos.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;import com.mokatest.platform.demos.ai.domain.AiGenerationRecord;
import com.mokatest.platform.demos.ai.mapper.AiGenerationRecordMapper;
import com.mokatest.platform.demos.ai.service.AiGenerationRecordService;
import com.mokatest.platform.demos.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * AI 生成记录服务实现
 */
@Service
public class AiGenerationRecordServiceImpl implements AiGenerationRecordService {

    /** 记录有效期：24 小时 */
    private static final int EXPIRE_HOURS = 24;

    @Autowired
    private AiGenerationRecordMapper recordMapper;

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
        // 跨项目访问统一报"不存在"，不泄露记录是否真实存在
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
        // 流式生成先建记录、暂无快照可写：outputSnapshot 为 null 时直接跳过，
        // 否则 updateById 会生成缺少 SET 子句的 SQL（语法错误，首轮生成必失败）
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
        JSONArray rounds = parseRounds(record.getOutputSnapshot());
        JSONObject round = new JSONObject();
        round.put("round", rounds.size() + 1);
        round.put("instruction", instruction);
        round.put("time", System.currentTimeMillis());
        round.put("drafts", draftsJson == null ? new JSONArray() : JSON.parse(draftsJson));
        rounds.add(round);
        AiGenerationRecord update = new AiGenerationRecord();
        update.setId(record.getId());
        update.setOutputSnapshot(rounds.toJSONString());
        recordMapper.updateById(update);
    }

    @Override
    public int beginRound(String recordNo, Integer projectId, String instruction) {
        return beginRound(recordNo, projectId, instruction, "gen");
    }

    @Override
    public int beginRound(String recordNo, Integer projectId, String instruction, String type) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        JSONArray rounds = parseRounds(record.getOutputSnapshot());
        JSONObject round = new JSONObject();
        round.put("round", rounds.size() + 1);
        round.put("type", type == null ? "gen" : type);
        round.put("instruction", instruction);
        round.put("time", System.currentTimeMillis());
        round.put("status", "GENERATING");
        round.put("drafts", new JSONArray());
        rounds.add(round);
        AiGenerationRecord update = new AiGenerationRecord();
        update.setId(record.getId());
        update.setOutputSnapshot(rounds.toJSONString());
        recordMapper.updateById(update);
        return round.getIntValue("round");
    }

    @Override
    public void finishQaRound(String recordNo, Integer projectId, String answer) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        JSONArray rounds = parseRounds(record.getOutputSnapshot());
        JSONObject target = findLatestGenerating(rounds);
        if (target == null) {
            target = new JSONObject();
            target.put("round", rounds.size() + 1);
            target.put("time", System.currentTimeMillis());
            rounds.add(target);
        }
        target.put("type", "qa");
        target.put("status", "DONE");
        target.put("rawText", answer);
        target.put("drafts", new JSONArray());
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
        JSONArray rounds = parseRounds(record.getOutputSnapshot());
        JSONObject target = findLatestGenerating(rounds);
        if (target == null) {
            // 无 GENERATING 轮（如旧链路），直接追加完成轮
            target = new JSONObject();
            target.put("round", rounds.size() + 1);
            target.put("time", System.currentTimeMillis());
            rounds.add(target);
        }
        target.put("status", "DONE");
        target.put("drafts", draftsJson == null ? new JSONArray() : JSON.parse(draftsJson));
        if (rawText != null) {
            target.put("rawText", rawText);
        }
        if (uncertaintiesJson != null) {
            target.put("uncertainties", JSON.parse(uncertaintiesJson));
        }
        if (citationsJson != null) {
            target.put("citations", JSON.parse(citationsJson));
        }
        target.remove("error");
        saveRounds(record, rounds);
    }

    @Override
    public void updateRoundRawText(String recordNo, Integer projectId, String rawText) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        JSONArray rounds = parseRounds(record.getOutputSnapshot());
        JSONObject target = findLatestGenerating(rounds);
        if (target == null) {
            return;
        }
        target.put("rawText", rawText);
        saveRounds(record, rounds);
    }

    @Override
    public void stopRound(String recordNo, Integer projectId, String rawText) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        JSONArray rounds = parseRounds(record.getOutputSnapshot());
        JSONObject target = findLatestGenerating(rounds);
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
        JSONArray rounds = parseRounds(record.getOutputSnapshot());
        if (!rounds.isEmpty()) {
            rounds.remove(rounds.size() - 1);
        }
        saveRounds(record, rounds);
    }

    private void saveRounds(AiGenerationRecord record, JSONArray rounds) {
        AiGenerationRecord update = new AiGenerationRecord();
        update.setId(record.getId());
        update.setOutputSnapshot(rounds.toJSONString());
        recordMapper.updateById(update);
    }

    @Override
    public void delete(String recordNo, Integer projectId) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        recordMapper.deleteById(record.getId());
    }

    @Override
    public void failRound(String recordNo, Integer projectId, String errorMsg) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        JSONArray rounds = parseRounds(record.getOutputSnapshot());
        JSONObject target = findLatestGenerating(rounds);
        if (target == null) {
            target = new JSONObject();
            target.put("round", rounds.size() + 1);
            target.put("time", System.currentTimeMillis());
            rounds.add(target);
        }
        target.put("status", "FAILED");
        target.put("error", errorMsg == null ? "生成失败" : errorMsg);
        target.put("drafts", new JSONArray());
        AiGenerationRecord update = new AiGenerationRecord();
        update.setId(record.getId());
        update.setOutputSnapshot(rounds.toJSONString());
        recordMapper.updateById(update);
    }

    /** 找最新的 GENERATING 轮次（正常情况即最后一轮） */
    private JSONObject findLatestGenerating(JSONArray rounds) {
        for (int i = rounds.size() - 1; i >= 0; i--) {
            JSONObject round = rounds.getJSONObject(i);
            if (round != null && "GENERATING".equals(round.getString("status"))) {
                return round;
            }
        }
        return null;
    }

    @Override
    public JSONArray parseRounds(String outputSnapshot) {
        if (outputSnapshot == null || outputSnapshot.trim().isEmpty()) {
            return new JSONArray();
        }
        try {
            JSONArray arr = JSON.parseArray(outputSnapshot);
            if (arr.isEmpty()) {
                return arr;
            }
            Object first = arr.get(0);
            // 新格式：元素是含 drafts 键的对象 → 已是轮次数组
            if (first instanceof JSONObject && ((JSONObject) first).containsKey("drafts")) {
                return arr;
            }
            // 旧格式：单层草稿数组 → 包装为第 1 轮
            JSONArray rounds = new JSONArray();
            JSONObject round = new JSONObject();
            round.put("round", 1);
            round.put("instruction", null);
            round.put("time", null);
            round.put("drafts", arr);
            rounds.add(round);
            return rounds;
        } catch (Exception e) {
            return new JSONArray();
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
    public java.util.Set<String> parseAdoptedDraftIds(String adoptedDetail) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        JSONArray entries = parseAdoptionEntries(adoptedDetail);
        for (int i = 0; i < entries.size(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String draftId = entry == null ? null : entry.getString("draftId");
            if (draftId != null && !draftId.isEmpty()) {
                ids.add(draftId);
            }
        }
        return ids;
    }

    @Override
    public void mergeAdoption(String recordNo, Integer projectId, JSONArray entries) {
        AiGenerationRecord record = requireValid(recordNo, projectId);
        JSONArray merged = parseAdoptionEntries(record.getAdoptedDetail());
        if (entries != null) {
            merged.addAll(entries);
        }
        AiGenerationRecord update = new AiGenerationRecord();
        update.setId(record.getId());
        update.setAdoptedDetail(merged.toJSONString());
        recordMapper.updateById(update);
    }

    @Override
    public JSONArray parseAdoptionEntries(String adoptedDetail) {
        JSONArray entries = new JSONArray();
        if (adoptedDetail == null || adoptedDetail.trim().isEmpty()) {
            return entries;
        }
        try {
            Object parsed = JSON.parse(adoptedDetail);
            if (parsed instanceof JSONArray) {
                return (JSONArray) parsed;
            }
            // 旧格式 {caseIds:[...], count:n} → 迁移为 [{caseId}]
            if (parsed instanceof JSONObject) {
                JSONArray caseIds = ((JSONObject) parsed).getJSONArray("caseIds");
                if (caseIds != null) {
                    for (int i = 0; i < caseIds.size(); i++) {
                        JSONObject entry = new JSONObject();
                        entry.put("caseId", caseIds.get(i));
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
}
