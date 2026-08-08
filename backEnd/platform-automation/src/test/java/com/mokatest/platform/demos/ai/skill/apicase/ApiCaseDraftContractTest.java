package com.mokatest.platform.demos.ai.skill.apicase;

import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiAssertType;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiNodeType;
import com.mokatest.platform.demos.api.domain.apiEnum.RequestMethod;
import com.mokatest.platform.demos.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API 用例草稿「解析 → 映射」契约测试（防腐层）
 */
class ApiCaseDraftContractTest {

    private final ApiCaseDraftParser parser = new ApiCaseDraftParser();
    private final ApiCaseDraftMapper mapper = new ApiCaseDraftMapper();

    private ApiRequest baseApi() {
        ApiRequest api = new ApiRequest();
        api.setId(10);
        api.setApiNode(ApiNodeType.INTERFACE);
        api.setRequestMethod(RequestMethod.POST);
        api.setRequestPath("/api/order/create");
        api.setApiName("创建订单");
        return api;
    }

    @Test
    void parseCleanOutput() {
        String raw = "[{\"caseName\":\"正常创建\",\"description\":\"正常流程\","
                + "\"bodyJson\":\"{\\\"name\\\":\\\"@cname()\\\"}\","
                + "\"assertions\":[{\"apiAssertType\":\"STATUS_CODE\",\"field\":\"status\","
                + "\"assertRelationship\":\"EQUALS\",\"assertValue\":\"200\"}],"
                + "\"extractions\":[{\"type\":\"JSON_PATH\",\"expression\":\"$.data.id\",\"variableName\":\"orderId\"}]}]";
        List<ApiCaseDraftDTO> drafts = parser.parse(raw).getDrafts();
        assertEquals(1, drafts.size());
        ApiCaseDraftDTO d = drafts.get(0);
        assertEquals("正常创建", d.getCaseName());
        assertEquals(1, d.getAssertions().size());
        assertEquals("STATUS_CODE", d.getAssertions().get(0).getApiAssertType());
        assertEquals(1, d.getExtractions().size());
    }

    @Test
    void invalidAssertEnumFallback() {
        String raw = "[{\"caseName\":\"A\",\"assertions\":[{\"apiAssertType\":\"XXX\",\"assertRelationship\":\"LIKE\"}]}]";
        ApiCaseDraftDTO d = parser.parse(raw).getDrafts().get(0);
        assertEquals("STATUS_CODE", d.getAssertions().get(0).getApiAssertType());
        assertEquals("EQUALS", d.getAssertions().get(0).getAssertRelationship());
    }

    @Test
    void invalidJsonThrows() {
        assertThrows(BusinessException.class, () -> parser.parse("not json"));
        assertThrows(BusinessException.class, () -> parser.parse("[]"));
    }

    @Test
    void mapperClonesBaseAndOverrides() {
        String raw = "[{\"caseName\":\"异常场景\",\"query\":[{\"name\":\"page\",\"value\":\"-1\",\"type\":\"INTEGER\"}],"
                + "\"assertions\":[{\"apiAssertType\":\"BODY\",\"field\":\"$.code\",\"assertRelationship\":\"NOT_EQUALS\",\"assertValue\":\"0\"}]}]";
        ApiCaseDraftDTO draft = parser.parse(raw).getDrafts().get(0);
        ApiRequest entity = mapper.toEntity(draft, baseApi(), 1, 2);
        assertEquals("异常场景", entity.getApiName());
        assertEquals(RequestMethod.POST, entity.getRequestMethod(), "method 沿用接口");
        assertEquals("/api/order/create", entity.getRequestPath(), "path 沿用接口");
        assertEquals(1, entity.getQuery().size());
        assertEquals(1, entity.getApiResultAssert().size());
        assertEquals(ApiAssertType.BODY, entity.getApiResultAssert().get(0).getApiAssertType());
    }

    @Test
    void mapperBodyOverride() {
        String raw = "[{\"caseName\":\"A\",\"bodyJson\":\"{\\\"a\\\":1}\"}]";
        ApiRequest entity = mapper.toEntity(parser.parse(raw).getDrafts().get(0), baseApi(), 1, 2);
        assertNotNull(entity.getBody());
        assertEquals("{\"a\":1}", entity.getBody().getJson());
    }
}
