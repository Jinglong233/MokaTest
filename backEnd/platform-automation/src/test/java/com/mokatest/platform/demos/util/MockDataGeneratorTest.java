package com.mokatest.platform.demos.util;

import com.mokatest.platform.demos.api.domain.requestModel.MockConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock 与数据模板函数单元测试
 */
class MockDataGeneratorTest {

    @Test
    void testPhone() {
        String phone = MockDataGenerator.generate("phone");
        assertNotNull(phone);
        assertEquals(11, phone.length());
        assertTrue(phone.startsWith("1"));
    }

    @Test
    void testInt() {
        String result = MockDataGenerator.generate("int, 10, 20");
        int value = Integer.parseInt(result);
        assertTrue(value >= 10 && value <= 20);
    }

    @Test
    void testFloat() {
        String result = MockDataGenerator.generate("float, 0, 1, 2");
        double value = Double.parseDouble(result);
        assertTrue(value >= 0 && value <= 1);
        assertTrue(result.contains("."));
    }

    @Test
    void testChoice() {
        String result = MockDataGenerator.generate("choice, \"A,B,C\"");
        assertTrue(result.equals("A") || result.equals("B") || result.equals("C"));
    }

    @Test
    void testFunctionParserMock() {
        String result = FunctionParser.parse("{{__MOCK(phone)__}}");
        assertNotNull(result);
        assertEquals(11, result.length());
    }

    @Test
    void testFunctionParserMockInt() {
        String result = FunctionParser.parse("{{__MOCK(int, 1, 10)__}}");
        int value = Integer.parseInt(result);
        assertTrue(value >= 1 && value <= 10);
    }

    @Test
    void testVariableReplacerParsesFunctionWhenVariablesEmpty() {
        // 没有环境变量时，{{__MOCK()__}} 仍应被解析
        String result = VariableReplacer.replace("{{__MOCK(phone)__}}", Collections.emptyMap());
        assertNotNull(result);
        assertEquals(11, result.length());
        assertTrue(result.startsWith("1"));
    }

    @Test
    void testVariableReplacerReplacesVariableAndParsesFunction() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("prefix", "user");
        String result = VariableReplacer.replace("${prefix}_{{__MOCK(phone)__}}", vars);
        assertNotNull(result);
        assertTrue(result.startsWith("user_"));
        String phone = result.substring("user_".length());
        assertEquals(11, phone.length());
        assertTrue(phone.startsWith("1"));
    }

    @Test
    void testVariableReplacerReplaceWithTrackParsesFunctionWhenVariablesEmpty() {
        VariableReplacer.ReplaceResult result = VariableReplacer.replaceWithTrack(
                "{{__MOCK(phone)__}}", Collections.emptyMap());
        String phone = result.getResult();
        assertNotNull(phone);
        assertEquals(11, phone.length());
        assertTrue(phone.startsWith("1"));
        assertTrue(result.getFoundVariables().isEmpty());
        assertTrue(result.getUnmatchedVariables().isEmpty());
    }

    @Test
    void testMockConfigGeneratorPlaceholder() {
        assertTrue(MockConfigGenerator.isMockPlaceholder("{{__MOCK__}}"));
        assertFalse(MockConfigGenerator.isMockPlaceholder("{{__MOCK(phone)__}}"));
        assertFalse(MockConfigGenerator.isMockPlaceholder("13812345678"));
    }

    @Test
    void testMockConfigGeneratorPhone() {
        MockConfig config = new MockConfig();
        config.setType("phone");
        String result = MockConfigGenerator.generate(config);
        assertNotNull(result);
        assertEquals(11, result.length());
        assertTrue(result.startsWith("1"));
    }

    @Test
    void testMockConfigGeneratorInt() {
        MockConfig config = new MockConfig();
        config.setType("int");
        config.setMin(10);
        config.setMax(20);
        String result = MockConfigGenerator.generate(config);
        int value = Integer.parseInt(result);
        assertTrue(value >= 10 && value <= 20);
    }

    @Test
    void testMockConfigGeneratorCharacter() {
        MockConfig config = new MockConfig();
        config.setType("character");
        config.setCaseType("lower");
        config.setLength(8);
        String result = MockConfigGenerator.generate(config);
        assertNotNull(result);
        assertEquals(8, result.length());
        assertTrue(result.matches("[a-z]+"));
    }

    @Test
    void testMockConfigGeneratorCName() {
        MockConfig config = new MockConfig();
        config.setType("cname");
        String result = MockConfigGenerator.generate(config);
        assertNotNull(result);
        assertTrue(result.length() >= 2);
    }

    @Test
    void testMockConfigGeneratorEName() {
        MockConfig config = new MockConfig();
        config.setType("ename");
        String result = MockConfigGenerator.generate(config);
        assertNotNull(result);
        assertTrue(result.contains(" "));
    }

    @Test
    void testMockConfigGeneratorBoolean() {
        MockConfig config = new MockConfig();
        config.setType("boolean");
        String result = MockConfigGenerator.generate(config);
        assertTrue("true".equals(result) || "false".equals(result));
    }

    @Test
    void testMockConfigGeneratorTimestamp() {
        MockConfig config = new MockConfig();
        config.setType("timestamp");
        String result = MockConfigGenerator.generate(config);
        assertNotNull(result);
        assertTrue(result.matches("\\d+"));
    }

    @Test
    void testMockConfigGeneratorBankCard() {
        MockConfig config = new MockConfig();
        config.setType("bankcard");
        String result = MockConfigGenerator.generate(config);
        assertNotNull(result);
        assertEquals(18, result.length());
        assertTrue(result.matches("\\d+"));
    }

    @Test
    void testMockConfigGeneratorDateTime() {
        MockConfig config = new MockConfig();
        config.setType("datetime");
        config.setFormat("yyyy-MM-dd HH:mm:ss");
        String result = MockConfigGenerator.generate(config);
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void testMockConfigGeneratorTime() {
        MockConfig config = new MockConfig();
        config.setType("time");
        config.setFormat("HH:mm:ss");
        String result = MockConfigGenerator.generate(config);
        assertNotNull(result);
        assertTrue(result.matches("\\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void testMockDataGeneratorCharacterUpper() {
        String result = MockDataGenerator.generate("character, upper, 10");
        assertNotNull(result);
        assertEquals(10, result.length());
        assertTrue(result.matches("[A-Z]+"));
    }

    @Test
    void testMockDataGeneratorCharacterNumber() {
        String result = MockDataGenerator.generate("character, number, 6");
        assertNotNull(result);
        assertEquals(6, result.length());
        assertTrue(result.matches("\\d+"));
    }

    @Test
    void testMockDataGeneratorCharacterMixed() {
        String result = MockDataGenerator.generate("character, mixed, 16");
        assertNotNull(result);
        assertEquals(16, result.length());
        assertTrue(result.matches("[a-zA-Z0-9]+"));
    }

    @Test
    void testMockDataGeneratorCName() {
        String result = MockDataGenerator.generate("cname");
        assertNotNull(result);
        assertTrue(result.length() >= 2);
    }

    @Test
    void testMockDataGeneratorEName() {
        String result = MockDataGenerator.generate("ename");
        assertNotNull(result);
        assertTrue(result.contains(" "));
    }

    @Test
    void testMockDataGeneratorBankCard() {
        String result = MockDataGenerator.generate("bankcard");
        assertNotNull(result);
        assertEquals(18, result.length());
        assertTrue(result.matches("\\d+"));
    }

    @Test
    void testMockDataGeneratorTimestampSeconds() {
        String result = MockDataGenerator.generate("timestamp, s");
        long value = Long.parseLong(result);
        long nowSeconds = System.currentTimeMillis() / 1000;
        assertTrue(Math.abs(value - nowSeconds) < 10);
    }

    @Test
    void testMockDataGeneratorBoolean() {
        String result = MockDataGenerator.generate("boolean");
        assertTrue("true".equals(result) || "false".equals(result));
    }

    @Test
    void testFunctionParserAtSyntaxPhone() {
        String result = FunctionParser.parse("prefix_@phone()_suffix");
        assertNotNull(result);
        assertTrue(result.startsWith("prefix_"));
        assertTrue(result.endsWith("_suffix"));
        String phone = result.substring("prefix_".length(), result.length() - "_suffix".length());
        assertEquals(11, phone.length());
        assertTrue(phone.startsWith("1"));
    }

    @Test
    void testFunctionParserAtSyntaxInteger() {
        String result = FunctionParser.parse("@integer(10, 20)");
        int value = Integer.parseInt(result);
        assertTrue(value >= 10 && value <= 20);
    }

    @Test
    void testFunctionParserAtSyntaxCharacter() {
        String result = FunctionParser.parse("@character('lower', 8)");
        assertNotNull(result);
        assertEquals(8, result.length());
        assertTrue(result.matches("[a-z]+"));
    }

    @Test
    void testFunctionParserAtSyntaxName() {
        String result = FunctionParser.parse("@name('zh')");
        assertNotNull(result);
        assertTrue(result.length() >= 2);
    }

    @Test
    void testFunctionParserContainsAtSyntax() {
        assertTrue(FunctionParser.containsFunction("@phone()"));
        assertTrue(FunctionParser.containsFunction("@integer(1, 100)"));
        assertFalse(FunctionParser.containsFunction("hello world"));
    }

    @Test
    void testAtSyntaxMockGeneratorFixed() {
        assertEquals("hello", AtSyntaxMockGenerator.generate("fixed", "'hello'"));
        assertEquals("world", AtSyntaxMockGenerator.generate("fixed", "world"));
    }
}
