package com.mokatest.platform.demos.ai.domain;

import java.util.List;

/**
 * 覆盖审查结论 POJO（langchain4j 结构化输出直接反序列化）。
 *
 * §5.1 整体结论层：overallVerdict 为收敛判定的最高依据。
 */
public class CoverageAuditReport {

    /** 终审结论：SUFFICIENT（覆盖充分）/ INSUFFICIENT（覆盖不充分） */
    private String overallVerdict;

    /** 终审摘要（一句话，前端横幅直接引用） */
    private String overallSummary;

    /** 逐 TP 判定 */
    private List<TpVerdict> verdicts;

    /** 清单遗漏的维度点 */
    private List<MissingPoint> missingPoints;

    // ==================== 嵌套 POJO ====================

    public static class TpVerdict {
        private String tpId;
        /** COVERED_ADOPTED / COVERED_DRAFT / CLAIMED_ONLY / UNCOVERED */
        private String status;
        private String claimedBy;
        private String reason;
        private String evidence;

        public String getTpId() { return tpId; }
        public void setTpId(String tpId) { this.tpId = tpId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getClaimedBy() { return claimedBy; }
        public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getEvidence() { return evidence; }
        public void setEvidence(String evidence) { this.evidence = evidence; }
    }

    public static class MissingPoint {
        private String title;
        private String category;
        private String reason;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    // ==================== getters/setters ====================

    public String getOverallVerdict() { return overallVerdict; }
    public void setOverallVerdict(String overallVerdict) { this.overallVerdict = overallVerdict; }
    public String getOverallSummary() { return overallSummary; }
    public void setOverallSummary(String overallSummary) { this.overallSummary = overallSummary; }
    public List<TpVerdict> getVerdicts() { return verdicts; }
    public void setVerdicts(List<TpVerdict> verdicts) { this.verdicts = verdicts; }
    public List<MissingPoint> getMissingPoints() { return missingPoints; }
    public void setMissingPoints(List<MissingPoint> missingPoints) { this.missingPoints = missingPoints; }
}
