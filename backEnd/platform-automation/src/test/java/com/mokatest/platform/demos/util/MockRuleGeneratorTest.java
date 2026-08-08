package com.mokatest.platform.demos.util;

import com.mokatest.platform.demos.api.domain.requestModel.MockFieldRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockRuleGenerator 规则参数生效测试（datetime/time format、timestamp 单位、
 * character caseType/length、minLength/maxLength 区间、pattern 正则）
 */
class MockRuleGeneratorTest {

    private MockFieldRule stringRule(String ruleType) {
        MockFieldRule rule = new MockFieldRule();
        rule.setFieldType("STRING");
        rule.setRuleType(ruleType);
        return rule;
    }

    @Test
    void datetimeFormatTakesEffect() {
        MockFieldRule rule = stringRule("datetime");
        rule.setFormat("yyyyMMdd");
        Object value = MockRuleGenerator.generateValue(rule);
        assertNotNull(value);
        assertTrue(value.toString().matches("\\d{8}"), "datetime 应按 yyyyMMdd 格式生成: " + value);
    }

    @Test
    void timeFormatTakesEffect() {
        MockFieldRule rule = stringRule("time");
        rule.setFormat("HHmm");
        Object value = MockRuleGenerator.generateValue(rule);
        assertNotNull(value);
        assertTrue(value.toString().matches("\\d{4}"), "time 应按 HHmm 格式生成: " + value);
    }

    @Test
    void timestampUnitTakesEffect() {
        MockFieldRule rule = stringRule("timestamp");
        rule.setFormat("s");
        Object value = MockRuleGenerator.generateValue(rule);
        assertNotNull(value);
        // 秒级时间戳 10 位
        assertTrue(value.toString().matches("\\d{10}"), "timestamp 单位 s 应为 10 位秒级时间戳: " + value);
    }

    @Test
    void timestampDefaultMs() {
        MockFieldRule rule = stringRule("timestamp");
        Object value = MockRuleGenerator.generateValue(rule);
        assertNotNull(value);
        assertTrue(value.toString().matches("\\d{13}"), "timestamp 默认应为 13 位毫秒时间戳: " + value);
    }

    @Test
    void characterCaseTypeAndLength() {
        MockFieldRule rule = stringRule("character");
        rule.setCaseType("upper");
        rule.setLength(8);
        Object value = MockRuleGenerator.generateValue(rule);
        assertNotNull(value);
        assertTrue(value.toString().matches("[A-Z]{8}"), "character upper+8 应生成 8 位大写: " + value);
    }

    @Test
    void characterNumberOnly() {
        MockFieldRule rule = stringRule("character");
        rule.setCaseType("number");
        rule.setLength(6);
        Object value = MockRuleGenerator.generateValue(rule);
        assertTrue(value.toString().matches("\\d{6}"), "character number+6 应生成 6 位数字: " + value);
    }

    @Test
    void textMinMaxLengthRange() {
        MockFieldRule rule = stringRule("text");
        rule.setMinLength(5);
        rule.setMaxLength(8);
        for (int i = 0; i < 50; i++) {
            Object value = MockRuleGenerator.generateValue(rule);
            int len = value.toString().length();
            assertTrue(len >= 5 && len <= 8, "text 长度应在 5~8 区间: " + len);
        }
    }

    @Test
    void textDefaultLengthWhenRangeUnset() {
        MockFieldRule rule = stringRule("text");
        // 前端模板默认值 0/1000 视为未配置，回退 length
        rule.setMinLength(0);
        rule.setMaxLength(1000);
        rule.setLength(12);
        Object value = MockRuleGenerator.generateValue(rule);
        assertEquals(12, value.toString().length(), "min/max 为默认值时应回退 length 字段");
    }

    @Test
    void patternTakesPriority() {
        MockFieldRule rule = stringRule("name");
        rule.setPattern("^1[3-9]\\d{9}$");
        for (int i = 0; i < 20; i++) {
            Object value = MockRuleGenerator.generateValue(rule);
            assertTrue(value.toString().matches("1[3-9]\\d{9}"), "pattern 应优先生成手机号格式: " + value);
        }
    }

    @Test
    void invalidPatternFallsBackToDefault() {
        MockFieldRule rule = stringRule("text");
        rule.setPattern("([invalid");
        rule.setDefaultValue("fallback");
        Object value = MockRuleGenerator.generateValue(rule);
        assertEquals("fallback", value);
    }

    @Test
    void choiceStillWorks() {
        MockFieldRule rule = stringRule("choice");
        rule.setChoices("A,B,C");
        Object value = MockRuleGenerator.generateValue(rule);
        assertTrue("ABC".contains(value.toString()), "choice 应从选项中取值: " + value);
    }
}
