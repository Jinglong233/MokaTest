package com.mokatest.platform.demos.qa.ai.casegen;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.domain.AiGenerationRecord;
import com.mokatest.platform.demos.ai.domain.CoverageAuditReport;
import com.mokatest.platform.demos.ai.domain.PairCheckResult;
import com.mokatest.platform.demos.ai.service.AiGenerationRecordService;
import com.mokatest.platform.demos.ai.service.AssistantFactory;
import com.mokatest.platform.demos.ai.service.CoverageAuditAssistant;
import com.mokatest.platform.demos.ai.service.PairCheckAssistant;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import com.mokatest.platform.demos.qa.service.TestCaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 覆盖度审查器（A/B/C 三层重构）。
 *
 * A 层（纯代码）：bigram Jaccard 证据矩阵，确定态不问模型。
 * B 层（窄判断）：仅模糊对进 PairCheckAssistant，YES/NO 二选一。
 * C 层（代码裁决）：overallVerdict 由 verdicts 推导，收回模型总结权。
 * missingPoints 仍由 CoverageAuditAssistant 产出，代码校验结构。
 */
@Component
public class CoverageAuditor {

    private static final Logger log = LoggerFactory.getLogger(CoverageAuditor.class);
    @Autowired
    private AiGenerationRecordService recordService;
    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private RequirementMapper requirementMapper;
    @Autowired
    private AssistantFactory assistantFactory;

    /** §3.4 输入预算：用例超过此数按更新时间截断 */
    private static final int CASE_BUDGET = 50;

    /**
     * 执行覆盖审查，返回结论 Map（overallVerdict + verdicts + missingPoints + 统计）。
     * 结果同时落库到 stagePayload.audit。
     */
    public Map<String, Object> audit(String recordNo, Integer projectId, Long entityId) {
        // 1. 加载记录
        AiGenerationRecord record = recordService.getByRecordNo(recordNo, projectId);
        if (record == null) {
            throw new BusinessException("生成记录不存在");
        }

        // 2. 恢复 testPoints + drafts（从 stagePayload）
        JSONObject stagePayload = null;
        List<Map<String, Object>> testPoints = new ArrayList<>();
        List<Object> draftList = new ArrayList<>();
        if (record.getStagePayload() != null) {
            try {
                stagePayload = JSON.parseObject(record.getStagePayload());
                JSONArray tpArr = stagePayload.getJSONArray("testPoints");
                if (tpArr != null) {
                    for (int i = 0; i < tpArr.size(); i++) {
                        testPoints.add(tpArr.getJSONObject(i));
                    }
                }
            } catch (Exception ignored) {}
        }

        // §4：优先从 stage_payload.pool 读取（单一事实源），回退到 rounds（兼容旧记录）
        List<Map<String, Object>> rounds = recordService.parseRounds(record.getOutputSnapshot());
        java.util.Set<String> adoptedIds = recordService.parseAdoptedDraftIds(recordNo);
        boolean poolRead = false;
        if (stagePayload != null) {
            JSONArray poolArr = stagePayload.getJSONArray("pool");
            if (poolArr != null && !poolArr.isEmpty()) {
                for (int i = 0; i < poolArr.size(); i++) {
                    Object item = poolArr.get(i);
                    if (item instanceof Map) {
                        Map<String, Object> m = (Map<String, Object>) item;
                        String draftId = String.valueOf(m.get("draftId"));
                        if (adoptedIds.contains(draftId)) continue;
                        draftList.add(item);
                    }
                }
                poolRead = true;
            }
        }
        if (!poolRead) {
            java.util.Set<String> seenDraftIds = new java.util.HashSet<>();
            for (Map<String, Object> round : rounds) {
                if ("DONE".equals(String.valueOf(round.get("status")))) {
                    Object d = round.get("drafts");
                    if (d instanceof List) {
                        for (Object item : (List<?>) d) {
                            if (item instanceof Map) {
                                Map<String, Object> m = (Map<String, Object>) item;
                                String draftId = String.valueOf(m.get("draftId"));
                                if (seenDraftIds.contains(draftId)) continue;
                                if (adoptedIds.contains(draftId)) continue;
                                seenDraftIds.add(draftId);
                                draftList.add(item);
                            }
                        }
                    }
                }
            }
        }

        // 3. 加载已入库用例（§3.4：>50 条截断）
        List<TestCase> existingCases = new ArrayList<>();
        if (entityId != null) {
            existingCases = testCaseService.list(
                    new QueryWrapper<TestCase>().eq("project_id", projectId)
                            .eq("requirement_id", entityId)
                            .select("id", "case_name", "test_steps")
                            .orderByDesc("update_time")
                            .last("limit " + CASE_BUDGET));
        }

        // 4. 加载需求原文
        String reqText = "";
        if (entityId != null) {
            Requirement req = requirementMapper.selectById(entityId);
            if (req != null) {
                reqText = "标题：" + (req.getTitle() != null ? req.getTitle() : "") + "\n描述："
                        + (req.getDescription() != null ? req.getDescription() : "");
                if (reqText.length() > 8000) {
                    reqText = reqText.substring(0, 8000) + "\n（需求过长，仅审查前部）";
                }
            }
        }

        // 5. 组装审查输入（@UserMessage 模板变量）
        String testPointsText = formatTestPoints(testPoints);
        String adoptedCasesText = formatCasesSummary(existingCases);
        String draftsText = formatDraftsSummary(draftList);

        log.info("[覆盖审查] 开始审查 | recordNo={} testPoints={} existingCases={} drafts={}",
                recordNo, testPoints.size(), existingCases.size(), draftList.size());

        // 6. A 层：纯代码证据矩阵
        Map<String, TpVerdictBuilder> tpVerdicts = aLayerClassify(testPoints, existingCases, draftList);

        // 7. B 层：模糊对进 PairCheckAssistant（窄判断 YES/NO）
        bLayerResolve(tpVerdicts, projectId);

        // 8. CoverageAuditAssistant：仅产出 missingPoints（prompt 已收窄，不再输出 verdicts/overallVerdict）
        CoverageAuditAssistant auditor = assistantFactory.createCoverageAuditor(projectId);
        CoverageAuditReport report = auditor.audit(reqText, testPointsText, adoptedCasesText, draftsText);

        // 9. C 层：代码聚合裁决 → overallVerdict + result map
        Map<String, Object> result = aggregateResult(tpVerdicts, report, testPoints);

        // 10. 结果落库
        try {
            if (stagePayload != null) {
                stagePayload.put("audit", JSON.toJSONString(result));
                recordService.updateStage(recordNo, projectId, "DONE", stagePayload.toJSONString());
            }
        } catch (Exception e) {
            log.warn("[覆盖审查] 结果落库失败 | recordNo={} err={}", recordNo, e.getMessage());
        }

        log.info("[覆盖审查] 完成 | recordNo={} overallVerdict={} claimedOnly={} missing={}",
                recordNo, result.get("overallVerdict"), result.get("claimedOnly"), result.get("missingCount"));
        return result;
    }

    /**
     * 单点覆盖审查：只审查指定 tpId 的覆盖情况。
     * 返回 {tpId, verdict, reason, missingPoints} 单点结论。
     * 落库到 stagePayload.audit.perPoint.{tpId}。
     */
    public Map<String, Object> auditSinglePoint(String recordNo, Integer projectId, Long entityId, String tpId) {
        AiGenerationRecord record = recordService.getByRecordNo(recordNo, projectId);
        if (record == null) throw new BusinessException("生成记录不存在");

        JSONObject stagePayload = null;
        Map<String, Object> targetTp = null;
        List<Object> draftList = new ArrayList<>();
        if (record.getStagePayload() != null) {
            try {
                stagePayload = JSON.parseObject(record.getStagePayload());
                JSONArray tpArr = stagePayload.getJSONArray("testPoints");
                if (tpArr != null) {
                    for (int i = 0; i < tpArr.size(); i++) {
                        JSONObject tp = tpArr.getJSONObject(i);
                        if (tpId.equals(tp.getString("id"))) { targetTp = new LinkedHashMap<>(tp); break; }
                    }
                }
                // 只取该点的草稿（coverPoints 包含 tpId）
                JSONArray poolArr = stagePayload.getJSONArray("pool");
                java.util.Set<String> adoptedIds = recordService.parseAdoptedDraftIds(recordNo);
                if (poolArr != null) {
                    for (int i = 0; i < poolArr.size(); i++) {
                        Object item = poolArr.get(i);
                        if (item instanceof Map) {
                            Map<String, Object> m = (Map<String, Object>) item;
                            String draftId = String.valueOf(m.get("draftId"));
                            if (adoptedIds.contains(draftId)) continue;
                            Object cps = m.get("coverPoints");
                            if (cps instanceof List && ((List<?>) cps).contains(tpId)) {
                                draftList.add(item);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (targetTp == null) throw new BusinessException("测试点 " + tpId + " 不存在");

        // 加载已入库用例（用于该点审查）
        List<TestCase> existingCases = new ArrayList<>();
        if (entityId != null) {
            existingCases = testCaseService.list(
                    new QueryWrapper<TestCase>().eq("project_id", projectId)
                            .eq("requirement_id", entityId).select("id", "case_name", "test_steps"));
        }
        // 过滤已入库用例到只含该 TP 的（通过 coverPoints 反查不可行，取全部参与 A 层比对）

        String reqText = "";
        if (entityId != null) {
            Requirement req = requirementMapper.selectById(entityId);
            if (req != null) {
                reqText = "标题：" + (req.getTitle() != null ? req.getTitle() : "") + "\n描述："
                        + (req.getDescription() != null ? req.getDescription() : "");
            }
        }

        List<Map<String, Object>> singleTp = new ArrayList<>();
        singleTp.add(targetTp);

        // A 层
        Map<String, TpVerdictBuilder> tpVerdicts = aLayerClassify(singleTp, existingCases, draftList);
        // B 层
        bLayerResolve(tpVerdicts, projectId);
        // CoverageAuditAssistant for missingPoints
        String testPointsText = formatTestPoints(singleTp);
        String adoptedCasesText = formatCasesSummary(existingCases);
        String draftsText = formatDraftsSummary(draftList);
        CoverageAuditAssistant auditor = assistantFactory.createCoverageAuditor(projectId);
        CoverageAuditReport report = auditor.audit(reqText, testPointsText, adoptedCasesText, draftsText);
        // C 层
        Map<String, Object> result = aggregateResult(tpVerdicts, report, singleTp);

        // 落库 perPoint
        try {
            if (stagePayload != null) {
                JSONObject auditObj = stagePayload.getJSONObject("audit");
                if (auditObj == null) auditObj = new JSONObject();
                JSONObject perPoint = auditObj.getJSONObject("perPoint");
                if (perPoint == null) perPoint = new JSONObject();
                perPoint.put(tpId, JSON.toJSON(result));
                auditObj.put("perPoint", perPoint);
                stagePayload.put("audit", auditObj);
                recordService.updateStage(recordNo, projectId, "DONE", stagePayload.toJSONString());
            }
        } catch (Exception e) {
            log.warn("[单点审查] 落库失败 | recordNo={} tpId={} err={}", recordNo, tpId, e.getMessage());
        }

        log.info("[单点审查] 完成 | recordNo={} tpId={} verdict={}", recordNo, tpId, result.get("overallVerdict"));
        return result;
    }

    // ==================== A 层：确定性证据矩阵（纯代码） ====================

    /**
     * 对每个 TP × 用例/草稿计算 bigram Jaccard 证据分，分类为确定态或模糊态。
     * 确定态（UNCOVERED/CLAIMED_ONLY/COVERED）直接定 verdict；模糊态（AMBIGUOUS）收集待 B 层。
     */
    @SuppressWarnings("unchecked")
    private Map<String, TpVerdictBuilder> aLayerClassify(List<Map<String, Object>> testPoints,
                                                          List<TestCase> existingCases,
                                                          List<Object> draftList) {
        Map<String, TpVerdictBuilder> builders = new LinkedHashMap<>();
        for (Map<String, Object> tp : testPoints) {
            String tpId = String.valueOf(tp.get("id"));
            String tpTitle = String.valueOf(tp.get("title"));
            builders.put(tpId, new TpVerdictBuilder(tpId, tpTitle));
        }

        // 已入库用例（无 coverPoints 声明 → hasClaim=false）
        for (TestCase c : existingCases) {
            String text = caseTextStr(c);
            String caseName = c.getCaseName() != null ? c.getCaseName() : "";
            for (TpVerdictBuilder b : builders.values()) {
                if (b.isCovered()) continue;
                double score = PointExtractor.evidenceScore(b.tpTitle, text);
                String verdict = PointExtractor.classifyByScore(score, false);
                if ("COVERED".equals(verdict)) {
                    b.setCovered("COVERED_ADOPTED");
                } else if ("AMBIGUOUS".equals(verdict)) {
                    b.addAmbiguous(caseName, text, false, false);
                }
            }
        }

        // 草稿（有 coverPoints 声明）
        for (Object d : draftList) {
            if (!(d instanceof Map)) continue;
            Map<String, Object> draft = (Map<String, Object>) d;
            String caseName = String.valueOf(draft.getOrDefault("caseName", ""));
            String text = draftTextStr(draft);
            List<String> coverPoints = getCoverPoints(draft);
            for (TpVerdictBuilder b : builders.values()) {
                if (b.isCovered()) continue;
                boolean hasClaim = coverPoints.contains(b.tpId);
                double score = PointExtractor.evidenceScore(b.tpTitle, text);
                String verdict = PointExtractor.classifyByScore(score, hasClaim);
                if ("COVERED".equals(verdict)) {
                    b.setCovered("COVERED_DRAFT");
                } else if ("CLAIMED_ONLY".equals(verdict)) {
                    b.setClaimedOnly(caseName);
                } else if ("AMBIGUOUS".equals(verdict)) {
                    b.addAmbiguous(caseName, text, true, hasClaim);
                }
            }
        }

        // 未进 B 层的 TP 直接定终态
        for (TpVerdictBuilder b : builders.values()) {
            if (!b.isCovered() && b.ambiguous.isEmpty()) {
                b.finalVerdict = b.claimedOnly ? "CLAIMED_ONLY" : "UNCOVERED";
            }
        }

        return builders;
    }

    // ==================== B 层：模型窄判断 ====================

    /**
     * 批量模糊对进 PairCheckAssistant，YES→COVERED，NO/异常→保守归入未覆盖。
     */
    private void bLayerResolve(Map<String, TpVerdictBuilder> tpVerdicts, Integer projectId) {
        List<AmbiguousPair> allAmbiguous = new ArrayList<>();
        for (TpVerdictBuilder b : tpVerdicts.values()) {
            if (!b.isCovered()) {
                allAmbiguous.addAll(b.ambiguous);
            }
        }
        if (allAmbiguous.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (AmbiguousPair p : allAmbiguous) {
            sb.append("- ").append(p.tpId).append(": ").append(p.tpTitle)
                    .append(" | 用例: ").append(p.caseName).append(" | ").append(p.caseText).append("\n");
        }

        try {
            PairCheckAssistant checker = assistantFactory.createPairCheckAssistant(projectId);
            PairCheckResult result = checker.check(sb.toString());
            if (result == null || result.getAnswers() == null) return;

            for (PairCheckResult.PairAnswer ans : result.getAnswers()) {
                TpVerdictBuilder b = tpVerdicts.get(ans.getTpId());
                if (b == null || b.isCovered()) continue;
                // B 层异常保守降级：非 YES 一律不提升 verdict
                if ("YES".equalsIgnoreCase(ans.getAnswer())) {
                    AmbiguousPair pair = b.findAmbiguous(ans.getCaseName());
                    if (pair != null) {
                        b.setCovered(pair.isDraft ? "COVERED_DRAFT" : "COVERED_ADOPTED");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[覆盖审查] B层窄判断异常，模糊对保守归入未覆盖: {}", e.getMessage());
        }

        // B 层结束后定终态（未被 B 层提升为 COVERED 的）
        for (TpVerdictBuilder b : tpVerdicts.values()) {
            if (!b.isCovered() && !b.ambiguous.isEmpty()) {
                b.finalVerdict = b.claimedOnly ? "CLAIMED_ONLY" : "UNCOVERED";
            }
        }
    }

    // ==================== C 层：代码聚合裁决 ====================

    /**
     * overallVerdict 由代码从 verdicts 推导：存在 UNCOVERED / CLAIMED_ONLY → INSUFFICIENT，否则 SUFFICIENT。
     */
    private Map<String, Object> aggregateResult(Map<String, TpVerdictBuilder> tpVerdicts,
                                                 CoverageAuditReport report,
                                                 List<Map<String, Object>> testPoints) {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> verdicts = new ArrayList<>();
        int claimedOnly = 0, coveredAdopted = 0, coveredDraft = 0, uncovered = 0;
        for (TpVerdictBuilder b : tpVerdicts.values()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("tpId", b.tpId);
            v.put("status", b.finalVerdict);
            if (b.claimedBy != null) {
                v.put("claimedBy", b.claimedBy);
                v.put("reason", b.reason);
                v.put("codeDetected", true);
            }
            verdicts.add(v);

            switch (b.finalVerdict) {
                case "CLAIMED_ONLY" -> claimedOnly++;
                case "COVERED_ADOPTED" -> coveredAdopted++;
                case "COVERED_DRAFT" -> coveredDraft++;
                default -> uncovered++;
            }
        }

        boolean sufficient = claimedOnly == 0 && uncovered == 0;
        result.put("overallVerdict", sufficient ? "SUFFICIENT" : "INSUFFICIENT");
        result.put("overallSummary", sufficient
                ? "所有维度点均有内容覆盖"
                : String.format("存在 %d 个未覆盖维度、%d 个挂名覆盖", uncovered, claimedOnly));
        result.put("verdicts", verdicts);

        List<CoverageAuditReport.MissingPoint> missing = report.getMissingPoints() != null
                ? report.getMissingPoints() : new ArrayList<>();
        result.put("missingPoints", missing);
        result.put("claimedOnly", claimedOnly);
        result.put("coveredAdopted", coveredAdopted);
        result.put("coveredDraft", coveredDraft);
        result.put("uncovered", uncovered);
        result.put("missingCount", missing.size());

        log.info("[覆盖审查] 语义校验 | verdicts={} overallVerdict={} claimedOnly={} uncovered={}",
                verdicts.size(), sufficient ? "SUFFICIENT" : "INSUFFICIENT", claimedOnly, uncovered);

        return result;
    }

    // ==================== 文本提取辅助 ====================

    private String caseTextStr(TestCase c) {
        StringBuilder sb = new StringBuilder(c.getCaseName() != null ? c.getCaseName() : "");
        if (c.getTestSteps() != null) {
            for (TestCase.TestStepItem step : c.getTestSteps()) {
                if (step.getStep() != null) sb.append(step.getStep());
                if (step.getExpected() != null) sb.append(step.getExpected());
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String draftTextStr(Map<String, Object> d) {
        StringBuilder sb = new StringBuilder(String.valueOf(d.getOrDefault("caseName", "")));
        Object steps = d.get("testSteps");
        if (steps instanceof List) {
            for (Object item : (List<?>) steps) {
                if (item instanceof Map) {
                    Map<String, Object> sm = (Map<String, Object>) item;
                    sb.append(sm.getOrDefault("step", ""));
                    sb.append(sm.getOrDefault("expected", ""));
                }
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> getCoverPoints(Map<String, Object> draft) {
        Object cp = draft.get("coverPoints");
        if (cp instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) cp) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return List.of();
    }

    // ==================== 格式化（@UserMessage 变量） ====================

    private String formatTestPoints(List<Map<String, Object>> testPoints) {
        if (testPoints.isEmpty()) {
            return "（无维度点清单，请基于需求原文自行提取应覆盖的维度）";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> tp : testPoints) {
            sb.append("- ").append(tp.get("id")).append(": ").append(tp.get("title"))
                    .append("（").append(tp.get("category")).append("）\n");
        }
        return sb.toString();
    }

    private String formatCasesSummary(List<TestCase> cases) {
        if (cases.isEmpty()) return "（无已入库用例）";
        StringBuilder sb = new StringBuilder();
        for (TestCase c : cases) {
            sb.append("- ").append(c.getCaseName());
            if (c.getTestSteps() != null && !c.getTestSteps().isEmpty()) {
                sb.append("（").append(c.getTestSteps().size()).append("步）");
                int limit = Math.min(c.getTestSteps().size(), 3);
                for (int i = 0; i < limit; i++) {
                    TestCase.TestStepItem step = c.getTestSteps().get(i);
                    sb.append("\n  ").append(i + 1).append(". ").append(step.getStep());
                    if (step.getExpected() != null && !step.getExpected().isBlank()) {
                        sb.append(" → ").append(step.getExpected());
                    }
                }
                if (c.getTestSteps().size() > 3) sb.append("\n  （更多步骤略）");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String formatDraftsSummary(List<Object> drafts) {
        if (drafts.isEmpty()) return "（无待采纳草稿）";
        StringBuilder sb = new StringBuilder();
        for (Object d : drafts) {
            if (d instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) d;
                sb.append("- ").append(m.get("caseName"));
                Object steps = m.get("testSteps");
                if (steps instanceof List) {
                    List<?> stepList = (List<?>) steps;
                    sb.append("（").append(stepList.size()).append("步）");
                    int limit = Math.min(stepList.size(), 3);
                    for (int i = 0; i < limit; i++) {
                        Object step = stepList.get(i);
                        if (step instanceof Map) {
                            Map<String, Object> sm = (Map<String, Object>) step;
                            sb.append("\n  ").append(i + 1).append(". ").append(sm.get("step"));
                            Object exp = sm.get("expected");
                            if (exp != null && !String.valueOf(exp).isBlank()) {
                                sb.append(" → ").append(exp);
                            }
                        }
                    }
                    if (stepList.size() > 3) sb.append("\n  （更多步骤略）");
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    // ==================== 内部状态类 ====================

    private static class TpVerdictBuilder {
        final String tpId;
        final String tpTitle;
        String finalVerdict = "UNCOVERED";
        boolean claimedOnly = false;
        String claimedBy;
        String reason;
        final List<AmbiguousPair> ambiguous = new ArrayList<>();

        TpVerdictBuilder(String tpId, String tpTitle) {
            this.tpId = tpId;
            this.tpTitle = tpTitle;
        }

        boolean isCovered() {
            return "COVERED_ADOPTED".equals(finalVerdict) || "COVERED_DRAFT".equals(finalVerdict);
        }

        void setCovered(String v) { this.finalVerdict = v; }

        void setClaimedOnly(String caseName) {
            this.claimedOnly = true;
            this.claimedBy = caseName;
            this.reason = "声明覆盖但步骤内容未体现（代码证据矩阵检出）";
        }

        void addAmbiguous(String caseName, String caseText, boolean isDraft, boolean hasClaim) {
            this.ambiguous.add(new AmbiguousPair(tpId, tpTitle, caseName, caseText, isDraft, hasClaim));
        }

        AmbiguousPair findAmbiguous(String caseName) {
            return this.ambiguous.stream()
                    .filter(p -> p.caseName.equals(caseName))
                    .findFirst().orElse(null);
        }
    }

    private static class AmbiguousPair {
        final String tpId;
        final String tpTitle;
        final String caseName;
        final String caseText;
        final boolean isDraft;
        final boolean hasClaim;

        AmbiguousPair(String tpId, String tpTitle, String caseName, String caseText, boolean isDraft, boolean hasClaim) {
            this.tpId = tpId;
            this.tpTitle = tpTitle;
            this.caseName = caseName;
            this.caseText = caseText;
            this.isDraft = isDraft;
            this.hasClaim = hasClaim;
        }
    }
}
