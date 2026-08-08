package com.mokatest.platform.demos.util;

import com.mokatest.platform.demos.api.domain.requestModel.MockFieldRule;
import com.mokatest.platform.demos.api.domain.requestModel.ResponseSchema;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;
import com.mokatest.platform.demos.api.http.validation.SchemaValidator;
import com.mokatest.platform.demos.result.AssertResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SchemaValidator 结构校验测试（INLINE 模式，不依赖数据库）
 */
class SchemaValidatorTest {

    private MockFieldRule field(String name, String type) {
        MockFieldRule rule = new MockFieldRule();
        rule.setFieldName(name);
        rule.setFieldType(type);
        rule.setRequired(true);
        return rule;
    }

    private MockFieldRule objectRoot(MockFieldRule... children) {
        MockFieldRule root = new MockFieldRule();
        root.setFieldType("OBJECT");
        root.setChildren(List.of(children));
        return root;
    }

    private AssertResult validateInline(MockFieldRule root, String json) {
        ResponseSchema rs = new ResponseSchema();
        rs.setMode(ResponseSchema.Mode.INLINE);
        rs.setSchema(root);
        rs.setValidateEnabled(true);
        TestHttpResponse response = TestHttpResponse.builder().rawBody(json.getBytes()).build();
        return SchemaValidator.validate(rs, response);
    }

    @Test
    void passWhenAllMatch() {
        MockFieldRule root = objectRoot(field("name", "STRING"), field("age", "INT"), field("vip", "BOOLEAN"));
        AssertResult result = validateInline(root, "{\"name\":\"张三\",\"age\":18,\"vip\":true}");
        assertNotNull(result);
        assertTrue(result.getSuccess(), result.getAssertTip());
        assertEquals("SCHEMA", result.getAssertType());
    }

    @Test
    void failWhenTypeMismatch() {
        MockFieldRule root = objectRoot(field("age", "INT"));
        AssertResult result = validateInline(root, "{\"age\":\"十八\"}");
        assertFalse(result.getSuccess());
        assertTrue(result.getAssertTip().contains("age"), result.getAssertTip());
    }

    @Test
    void failWhenRequiredMissing() {
        MockFieldRule root = objectRoot(field("name", "STRING"));
        AssertResult result = validateInline(root, "{}");
        assertFalse(result.getSuccess());
        assertTrue(result.getAssertTip().contains("缺少必填字段"));
    }

    @Test
    void optionalFieldCanBeMissing() {
        MockFieldRule optional = field("nickname", "STRING");
        optional.setRequired(false);
        AssertResult result = validateInline(objectRoot(optional), "{}");
        assertTrue(result.getSuccess(), result.getAssertTip());
    }

    @Test
    void nullableRespected() {
        MockFieldRule nullable = field("remark", "STRING");
        nullable.setNullable(true);
        AssertResult pass = validateInline(objectRoot(nullable), "{\"remark\":null}");
        assertTrue(pass.getSuccess(), pass.getAssertTip());

        MockFieldRule notNull = field("remark", "STRING");
        AssertResult fail = validateInline(objectRoot(notNull), "{\"remark\":null}");
        assertFalse(fail.getSuccess());
    }

    @Test
    void floatRejectsNonNumber() {
        AssertResult result = validateInline(objectRoot(field("price", "DOUBLE")), "{\"price\":\"9.9\"}");
        assertFalse(result.getSuccess());
    }

    @Test
    void intRejectsDecimal() {
        AssertResult result = validateInline(objectRoot(field("age", "INT")), "{\"age\":18.5}");
        assertFalse(result.getSuccess());
    }

    @Test
    void nestedObjectValidated() {
        MockFieldRule user = field("user", "OBJECT");
        user.setChildren(List.of(field("phone", "STRING")));
        AssertResult result = validateInline(objectRoot(user), "{\"user\":{\"phone\":138}}");
        assertFalse(result.getSuccess());
        assertTrue(result.getAssertTip().contains("$.user.phone"), result.getAssertTip());
    }

    @Test
    void arraySizeAndElementValidated() {
        MockFieldRule items = field("items", "ARRAY");
        items.setMinItems(2);
        MockFieldRule itemId = field("itemId", "STRING");
        items.setChildren(List.of(itemId));
        AssertResult sizeFail = validateInline(objectRoot(items), "{\"items\":[{\"itemId\":\"a\"}]}");
        assertFalse(sizeFail.getSuccess());

        AssertResult elementFail = validateInline(objectRoot(items), "{\"items\":[{\"itemId\":\"a\"},{\"itemId\":2}]}");
        assertFalse(elementFail.getSuccess());
        assertTrue(elementFail.getAssertTip().contains("$.items[1].itemId"), elementFail.getAssertTip());

        AssertResult pass = validateInline(objectRoot(items), "{\"items\":[{\"itemId\":\"a\"},{\"itemId\":\"b\"}]}");
        assertTrue(pass.getSuccess(), pass.getAssertTip());
    }

    @Test
    void choiceEnumValidated() {
        MockFieldRule status = field("status", "STRING");
        status.setRuleType("choice");
        status.setChoices("SUCCESS,FAIL");
        AssertResult fail = validateInline(objectRoot(status), "{\"status\":\"UNKNOWN\"}");
        assertFalse(fail.getSuccess());
        assertTrue(fail.getAssertTip().contains("枚举"), fail.getAssertTip());

        AssertResult pass = validateInline(objectRoot(status), "{\"status\":\"SUCCESS\"}");
        assertTrue(pass.getSuccess(), pass.getAssertTip());
    }

    @Test
    void invalidJsonFails() {
        AssertResult result = validateInline(objectRoot(field("a", "STRING")), "not-json{");
        assertNotNull(result);
        assertFalse(result.getSuccess());
    }

    @Test
    void nullWhenDisabledOrNone() {
        ResponseSchema rs = new ResponseSchema();
        rs.setMode(ResponseSchema.Mode.NONE);
        assertNull(SchemaValidator.validate(rs, TestHttpResponse.builder().rawBody("{}".getBytes()).build()));

        rs.setMode(ResponseSchema.Mode.INLINE);
        rs.setSchema(objectRoot(field("a", "STRING")));
        rs.setValidateEnabled(false);
        assertNull(SchemaValidator.validate(rs, TestHttpResponse.builder().rawBody("{}".getBytes()).build()));
    }

    @Test
    void applyOverridesHiddenAndReplace() {
        MockFieldRule root = objectRoot(field("a", "STRING"), field("b", "INT"));
        MockFieldRule overrideB = field("b", "STRING");
        MockFieldRule result = SchemaValidator.applyOverrides(root, List.of("a"), List.of(overrideB));

        assertEquals(1, result.getChildren().size());
        assertEquals("b", result.getChildren().get(0).getFieldName());
        assertEquals("STRING", result.getChildren().get(0).getFieldType());
        // 原树不被修改
        assertEquals(2, root.getChildren().size());
    }
}
