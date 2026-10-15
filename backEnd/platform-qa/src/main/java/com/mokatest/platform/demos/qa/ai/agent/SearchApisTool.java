package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiNodeType;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具：搜索当前项目的 API 接口（接口用例生成的候选来源）
 *
 * 按名称/路径模糊匹配，返回候选列表供前端推送接口候选卡片，
 * 用户点击候选后由前端直接唤起接口用例生成弹窗（不经过 LLM）。
 */
@Component
public class SearchApisTool implements AgentTool {

    @Autowired
    private ApiRequestMapper apiRequestMapper;

    @Override public String name() { return "search_apis"; }
    @Override public String description() { return "搜索当前项目中的API接口，按接口名称或请求路径模糊匹配，返回候选接口列表（最多10条，含id/名称/方法/路径）。"
            + "当用户想为接口生成测试用例但未指明具体接口时调用，列出候选请用户选择一个；用户提供了接口关键词时也调用本工具。"
            + "一次只为一个接口生成用例，禁止批量生成。"; }
    @Override public String paramSchema() { return "{\"keyword\": \"接口名称或路径关键词（必填）\"}"; }
    @Override public String requiredPermission() { return "auto:api:view"; }

    @Override
    public String execute(AgentContext ctx, JSONObject args) {
        String keyword = args != null ? args.getString("keyword") : null;
        if (keyword == null || keyword.isBlank()) {
            return "{\"error\": \"keyword 必填\"}";
        }
        List<ApiRequest> apis = apiRequestMapper.selectList(new QueryWrapper<ApiRequest>()
                .eq("project_id", ctx.getProjectId())
                .eq("api_node", ApiNodeType.INTERFACE)
                .and(w -> w.like("api_name", keyword).or().like("request_path", keyword))
                .select("id", "api_name", "request_method", "request_path")
                .last("limit 10"));
        JSONArray result = new JSONArray();
        for (ApiRequest api : apis) {
            JSONObject obj = new JSONObject();
            obj.put("id", api.getId());
            obj.put("name", api.getApiName());
            obj.put("method", api.getRequestMethod() != null ? api.getRequestMethod().name() : null);
            obj.put("path", api.getRequestPath());
            result.add(obj);
        }
        return result.toJSONString();
    }
}
