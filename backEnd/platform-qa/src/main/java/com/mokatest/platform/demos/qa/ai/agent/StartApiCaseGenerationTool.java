package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiNodeType;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 工具：确认接口用例生成目标（文字选定候选后触发）
 *
 * 用户通过文字从 search_apis 候选中选定某个接口（如"第二个"/"ID 13 那个"）时调用，
 * 返回接口摘要供前端推送候选卡片并自动唤起接口用例生成弹窗。
 */
@Component
public class StartApiCaseGenerationTool implements AgentTool {

    @Autowired
    private ApiRequestMapper apiRequestMapper;

    @Override public String name() { return "start_api_case_generation"; }
    @Override public String description() { return "当用户已明确要为某个具体接口生成测试用例时调用（如用户从候选列表中选定了某个接口，或直接给出了接口ID），传入接口ID，系统会自动打开生成入口。"
            + "一次只能为一个接口生成，禁止批量调用；若用户尚未指明具体接口，不要调用本工具，应先用 search_apis 列出候选。"; }
    @Override public String paramSchema() { return "{\"apiId\": \"接口ID（必填，数字）\"}"; }
    @Override public String requiredPermission() { return "qa:ai:use"; }

    @Override
    public String execute(AgentContext ctx, JSONObject args) {
        if (args == null || !args.containsKey("apiId")) {
            return "{\"error\": \"apiId 必填\"}";
        }
        Long apiId = args.getLong("apiId");
        ApiRequest api = apiRequestMapper.selectOne(new QueryWrapper<ApiRequest>()
                .eq("id", apiId).eq("project_id", ctx.getProjectId())
                .eq("api_node", ApiNodeType.INTERFACE)
                .select("id", "api_name", "request_method", "request_path"));
        if (api == null) return "{\"error\": \"接口不存在或无权限\"}";
        JSONObject result = new JSONObject();
        result.put("apiId", api.getId());
        result.put("name", api.getApiName());
        result.put("method", api.getRequestMethod() != null ? api.getRequestMethod().name() : null);
        result.put("path", api.getRequestPath());
        return result.toJSONString();
    }
}
