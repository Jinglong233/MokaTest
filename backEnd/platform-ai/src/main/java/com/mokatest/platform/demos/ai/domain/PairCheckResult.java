package com.mokatest.platform.demos.ai.domain;

import java.util.List;

/**
 * B 层窄判断结论 POJO（langchain4j 结构化输出直接反序列化）。
 *
 * 模型对模糊对逐对回答 YES/NO，代码校验后保守降级。
 */
public class PairCheckResult {

    private List<PairAnswer> answers;

    public static class PairAnswer {
        private String tpId;
        private String caseName;
        /** YES / NO */
        private String answer;

        public String getTpId() { return tpId; }
        public void setTpId(String tpId) { this.tpId = tpId; }
        public String getCaseName() { return caseName; }
        public void setCaseName(String caseName) { this.caseName = caseName; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
    }

    public List<PairAnswer> getAnswers() { return answers; }
    public void setAnswers(List<PairAnswer> answers) { this.answers = answers; }
}
