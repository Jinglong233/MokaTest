package com.mokatest.platform.demos.ai.skill;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.domain.AiUsageLog;
import com.mokatest.platform.demos.ai.mapper.AiUsageLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AI 能力配置与用量统计（内置场景静态列表，不依赖 SkillRegistry）
 */
@RestController
@RequestMapping("/ai/skill-config")
@RequiredArgsConstructor
public class SkillConfigController {

    private final AiUsageLogMapper usageLogMapper;

    /** 内置 AI 场景列表（usage 统计按 scene 聚合） */
    private static final String[] BUILTIN_SCENES = {
            "GENERATE_CASE", "EXTRACT_TEST_POINTS", "GENERATE_API_CASE", "REQUIREMENT_PATCH"
    };

    @GetMapping("/list")
    public SaResult list(@RequestHeader(value = "X-Project-Id", required = false) Integer projectId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String scene : BUILTIN_SCENES) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", scene);
            item.put("name", scene);
            QueryWrapper<AiUsageLog> qw = new QueryWrapper<>();
            if (projectId != null) qw.eq("project_id", projectId);
            qw.eq("scene", scene);
            List<AiUsageLog> logs = usageLogMapper.selectList(qw);
            long totalTokens = logs.stream().mapToLong(l -> l.getTokens() != null ? l.getTokens() : 0).sum();
            long totalCalls = logs.size();
            item.put("totalTokens", totalTokens);
            item.put("totalCalls", totalCalls);
            result.add(item);
        }
        return SaResult.ok().setData(result);
    }

    @GetMapping("/usage")
    public SaResult usage(@RequestHeader(value = "X-Project-Id", required = false) Integer projectId) {
        QueryWrapper<AiUsageLog> qw = new QueryWrapper<>();
        if (projectId != null) qw.eq("project_id", projectId);
        qw.orderByDesc("create_time").last("LIMIT 100");
        return SaResult.ok().setData(usageLogMapper.selectList(qw));
    }
}
