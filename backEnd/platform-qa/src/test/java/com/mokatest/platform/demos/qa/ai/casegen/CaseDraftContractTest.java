package com.mokatest.platform.demos.qa.ai.casegen;

import com.mokatest.platform.demos.ai.skill.DraftGenResult;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.qa.domain.TestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用例草稿「解析 → 映射」契约测试（防腐层）
 */
class CaseDraftContractTest {

    private final CaseDraftParser parser = new CaseDraftParser();
    private final CaseDraftMapper mapper = new CaseDraftMapper();

    @Test
    void parseCleanJsonArray() {
        String raw = "[{\"caseName\":\"登录成功\",\"preCondition\":\"用户已注册\","
                + "\"testSteps\":[{\"step\":\"输入正确账号密码\",\"expected\":\"登录成功\"}],"
                + "\"caseType\":\"FUNCTION\",\"priority\":\"P0\",\"tags\":\"登录\",\"expectDuration\":5}]";
        List<CaseDraftDTO> drafts = parser.parse(raw).getDrafts();
        assertEquals(1, drafts.size());
        CaseDraftDTO d = drafts.get(0);
        assertEquals("登录成功", d.getCaseName());
        assertEquals("FUNCTION", d.getCaseType());
        assertEquals("P0", d.getPriority());
        assertEquals(1, d.getTestSteps().size());
        assertFalse(d.getEnumFallback());
    }

    @Test
    void parseToleratesMarkdownFenceAndText() {
        String raw = "以下是生成的用例：\n```json\n[{\"caseName\":\"A\"}]\n```\n希望有帮助";
        List<CaseDraftDTO> drafts = parser.parse(raw).getDrafts();
        assertEquals(1, drafts.size());
        assertEquals("A", drafts.get(0).getCaseName());
        // 缺省枚举兜底
        assertEquals("FUNCTION", drafts.get(0).getCaseType());
        assertEquals("P2", drafts.get(0).getPriority());
        assertFalse(drafts.get(0).getEnumFallback());
    }

    @Test
    void invalidEnumFallbackAndFlagged() {
        String raw = "[{\"caseName\":\"A\",\"caseType\":\"SUPER\",\"priority\":\"P9\"}]";
        CaseDraftDTO d = parser.parse(raw).getDrafts().get(0);
        assertEquals("FUNCTION", d.getCaseType());
        assertEquals("P2", d.getPriority());
        assertTrue(d.getEnumFallback(), "非法枚举应标黄");
    }

    @Test
    void lowercaseEnumNormalized() {
        String raw = "[{\"caseName\":\"A\",\"caseType\":\"smoke\",\"priority\":\"p0\"}]";
        CaseDraftDTO d = parser.parse(raw).getDrafts().get(0);
        assertEquals("SMOKE", d.getCaseType());
        assertEquals("P0", d.getPriority());
        assertFalse(d.getEnumFallback());
    }

    @Test
    void invalidJsonThrows() {
        assertThrows(BusinessException.class, () -> parser.parse("这不是JSON"));
        assertThrows(BusinessException.class, () -> parser.parse("{\"a\":1}"));
        assertThrows(BusinessException.class, () -> parser.parse("[]"));
        assertThrows(BusinessException.class, () -> parser.parse(null));
    }

    @Test
    void entriesWithoutNameDropped() {
        String raw = "[{\"preCondition\":\"x\"},{\"caseName\":\"有效\"}]";
        List<CaseDraftDTO> drafts = parser.parse(raw).getDrafts();
        assertEquals(1, drafts.size());
        assertEquals("有效", drafts.get(0).getCaseName());
    }

    @Test
    void parseNewContractObject() {
        String raw = "{\"test_cases\":[{\"caseName\":\"登录成功\",\"caseType\":\"FUNCTION\",\"priority\":\"P0\"}],"
                + "\"uncertainties\":[\"需求未明确超时阈值（秒），建议确认以设计边界值测试\"]}";
        DraftGenResult<CaseDraftDTO> result = parser.parse(raw);
        assertEquals(1, result.getDrafts().size());
        assertEquals("登录成功", result.getDrafts().get(0).getCaseName());
        assertEquals(1, result.getUncertainties().size());
        assertTrue(result.getUncertainties().get(0).contains("超时阈值"));
    }

    @Test
    void newContractToleratesMarkdownFence() {
        String raw = "以下是生成结果：\n```json\n{\"test_cases\":[{\"caseName\":\"A\"}],\"uncertainties\":[]}\n```";
        DraftGenResult<CaseDraftDTO> result = parser.parse(raw);
        assertEquals(1, result.getDrafts().size());
        assertTrue(result.getUncertainties().isEmpty());
    }

    @Test
    void legacyArrayHasEmptyUncertainties() {
        DraftGenResult<CaseDraftDTO> result = parser.parse("[{\"caseName\":\"A\"}]");
        assertEquals(1, result.getDrafts().size());
        assertTrue(result.getUncertainties().isEmpty());
    }

    @Test
    void objectMissingTestCasesThrows() {
        assertThrows(BusinessException.class, () -> parser.parse("{\"uncertainties\":[]}"));
        assertThrows(BusinessException.class, () -> parser.parse("{\"test_cases\":[]}"));
    }

    @Test
    void mapperProducesValidEntity() {
        String raw = "[{\"caseName\":\"登录\",\"testSteps\":[{\"step\":\"s1\",\"expected\":\"e1\"}],"
                + "\"caseType\":\"API\",\"priority\":\"P1\"}]";
        CaseDraftDTO draft = parser.parse(raw).getDrafts().get(0);
        TestCase entity = mapper.toEntity(draft, 10, 99, 3);
        assertEquals(10, entity.getProjectId());
        assertEquals(99, entity.getRequirementId());
        assertEquals(3, entity.getModuleId());
        assertEquals("DRAFT", entity.getStatus());
        assertEquals("API", entity.getCaseType());
        assertEquals(1, entity.getTestSteps().size());
        assertEquals("s1", entity.getTestSteps().get(0).getStep());
    }
}
