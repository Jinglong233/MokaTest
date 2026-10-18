package com.mokatest.platform.demos.api.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.GlobalVar;
import com.mokatest.platform.demos.api.domain.apiEnum.GlobalRequestVarType;
import com.mokatest.platform.demos.api.domain.apiEnum.RuleSource;
import com.mokatest.platform.demos.api.domain.requestModel.AssertParameter;
import com.mokatest.platform.demos.api.domain.requestModel.RequestExecuteInfo;
import com.mokatest.platform.demos.api.domain.requestModel.RequestParameter;
import com.mokatest.platform.demos.api.mapper.GlobalVarMapper;
import com.mokatest.platform.demos.domain.ui.Project;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 全局参数（global_var）合并支持。
 *
 * 背景：全局参数有四种类型 —— VARIABLE（变量）、HEADER、COOKIE、ASSERT（断言），
 * 此前只有单接口调试链路（ApiRequestServiceImpl）合并了这四种，API 场景与 UI 场景链路
 * 仅把全局参数当变量灌进变量池，既不按类型过滤（HEADER/COOKIE/ASSERT 被当成变量，
 * 造成 ${Content-Type} 这类伪变量），也完全丢失了全局 Header/Cookie/断言，
 * 表现为「同一个接口单接口调试能带全局 Header，放进场景跑就不带」。
 *
 * 本类是四条执行链路（单接口调试、场景单步调试、API 场景/计划、UI 场景步骤）的统一入口。
 *
 * 优先级约定（从低到高）：全局 &lt; 环境 &lt; 场景 &lt; 接口。
 * Header/Cookie 必须在低层合并完成后**最后**调用本类，此时全局项插到列表最前，
 * 再统一按名去重保留最高优先级的一条。
 */
@Slf4j
@Component
public class GlobalVarSupport {

    @Resource
    private GlobalVarMapper globalVarMapper;

    @Resource
    private ProjectMapper projectMapper;

    /**
     * 按团队加载启用的全局参数；teamId 为空时返回空列表（按无全局参数执行）。
     */
    public List<GlobalVar> loadEnabled(Integer teamId) {
        if (teamId == null) {
            return new ArrayList<>();
        }
        return globalVarMapper.selectList(
                new QueryWrapper<GlobalVar>()
                        .eq("team_id", teamId)
                        .eq("disabled", false)
        );
    }

    /**
     * 便捷入口：团队归属取自请求对象，用于无法直接拿到场景团队 ID 的执行链路。
     *
     * teamId 缺失时按 projectId 反查所属团队 —— UI 场景步骤等链路只有请求副本，
     * 早期实现因此在 teamId 为空时静默跳过全部全局参数。
     */
    public void mergeIntoRequest(ApiRequest apiRequest) {
        if (apiRequest == null) {
            return;
        }
        Integer teamId = apiRequest.getTeamId();
        if (teamId == null) {
            teamId = resolveTeamIdByProject(apiRequest.getProjectId());
        }
        mergeInto(apiRequest, loadEnabled(teamId));
    }

    private Integer resolveTeamIdByProject(Integer projectId) {
        if (projectId == null) {
            return null;
        }
        Project project = projectMapper.selectById(projectId);
        return project != null ? project.getTeamId() : null;
    }

    /**
     * 把四类全局参数合并进请求（仅修改内存对象，不写库）。
     *
     * @param apiRequest 目标请求
     * @param globalVars 已由 {@link #loadEnabled} 加载的全局参数列表
     */
    public void mergeInto(ApiRequest apiRequest, List<GlobalVar> globalVars) {
        if (apiRequest == null || globalVars == null) {
            return;
        }

        if (apiRequest.getEnvInfo() == null) {
            apiRequest.setEnvInfo(new RequestExecuteInfo());
        }
        RequestExecuteInfo envInfo = apiRequest.getEnvInfo();

        // 1. VARIABLE → envVariables（全局最低，已有同名变量时不覆盖）
        boolean variablesChanged = false;
        for (GlobalVar globalVar : globalVars) {
            if (globalVar.getType() != GlobalRequestVarType.VARIABLE || globalVar.getName() == null) {
                continue;
            }
            Map<String, String> currentVariables = envInfo.getEnvVariables();
            if (currentVariables != null && currentVariables.containsKey(globalVar.getName())) {
                continue;
            }
            if (currentVariables == null) {
                currentVariables = new HashMap<>();
                envInfo.setEnvVariables(currentVariables);
            }
            currentVariables.put(globalVar.getName(), globalVar.getValue());
            variablesChanged = true;
        }
        if (variablesChanged) {
            log.debug("[全局参数] 变量合并完成, name 数量={}", envInfo.getEnvVariables().size());
        }

        // 2. HEADER → envHeaders：全局项插到最前，整体按名去重保留优先级最高的后者
        //    （执行器对 envHeaders 用的是追加语义，不去重会真的发出两个同名 Header）
        List<RequestParameter> mergedHeaders = collectAsParameters(globalVars, GlobalRequestVarType.HEADER);
        if (envInfo.getEnvHeaders() != null) {
            mergedHeaders.addAll(envInfo.getEnvHeaders());
        }
        envInfo.setEnvHeaders(dedupeKeepingHighestPriority(mergedHeaders));

        // 3. COOKIE → envCookies，同上
        List<RequestParameter> mergedCookies = collectAsParameters(globalVars, GlobalRequestVarType.COOKIE);
        if (envInfo.getEnvCookies() != null) {
            mergedCookies.addAll(envInfo.getEnvCookies());
        }
        envInfo.setEnvCookies(dedupeKeepingHighestPriority(mergedCookies));

        // 4. ASSERT → apiResultAssert 最前（接口断言后执行，优先级更高）
        mergeGlobalAssertions(apiRequest, globalVars);
    }

    /**
     * 取出指定类型的全局参数并转换为请求参数项
     */
    private List<RequestParameter> collectAsParameters(List<GlobalVar> globalVars, GlobalRequestVarType type) {
        List<RequestParameter> result = new ArrayList<>();
        for (GlobalVar globalVar : globalVars) {
            if (globalVar.getType() != type || globalVar.getName() == null) {
                continue;
            }
            RequestParameter parameter = new RequestParameter();
            parameter.setName(globalVar.getName());
            parameter.setValue(globalVar.getValue());
            parameter.setDisabled(false);
            result.add(parameter);
        }
        return result;
    }

    /**
     * 按名称去重，保留优先级最高的一条。
     *
     * 入参列表约定为「优先级低 → 高」，因此从后往前首次出现的名称即为最高优先级值。
     * Header 名称大小写不敏感，故用 CASE_INSENSITIVE_ORDER 比较；输出保持原有相对顺序。
     * 禁用项原样保留但不抢占名称，避免「被禁用的环境 Header」把同名的启用全局 Header 挤掉。
     */
    private List<RequestParameter> dedupeKeepingHighestPriority(List<RequestParameter> parameters) {
        Set<String> keptNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<RequestParameter> kept = new ArrayList<>();
        for (int i = parameters.size() - 1; i >= 0; i--) {
            RequestParameter parameter = parameters.get(i);
            if (parameter == null || parameter.getName() == null) {
                continue;
            }
            if (parameter.isDisabled()) {
                kept.add(parameter);
                continue;
            }
            if (keptNames.add(parameter.getName())) {
                kept.add(parameter);
            }
        }
        Collections.reverse(kept);
        return kept;
    }

    /**
     * 合并全局断言。
     *
     * 幂等：请求上已存在 GLOBAL 来源断言时跳过，避免重复执行路径把同一条断言叠加多次。
     */
    private void mergeGlobalAssertions(ApiRequest apiRequest, List<GlobalVar> globalVars) {
        List<AssertParameter> current = apiRequest.getApiResultAssert();
        if (current != null) {
            for (AssertParameter assertParameter : current) {
                if (assertParameter != null && assertParameter.getSource() == RuleSource.GLOBAL) {
                    return;
                }
            }
        }

        List<AssertParameter> globalAssertions = new ArrayList<>();
        for (GlobalVar globalVar : globalVars) {
            if (globalVar.getType() != GlobalRequestVarType.ASSERT || globalVar.getGlobalAssert() == null) {
                continue;
            }
            for (AssertParameter assertParameter : globalVar.getGlobalAssert()) {
                if (assertParameter != null) {
                    assertParameter.setSource(RuleSource.GLOBAL);
                    globalAssertions.add(assertParameter);
                }
            }
        }
        if (globalAssertions.isEmpty()) {
            return;
        }

        List<AssertParameter> merged = new ArrayList<>(globalAssertions);
        if (current != null) {
            for (AssertParameter assertParameter : current) {
                if (assertParameter != null) {
                    assertParameter.setSource(RuleSource.API);
                    merged.add(assertParameter);
                }
            }
        }
        apiRequest.setApiResultAssert(merged);
    }
}
