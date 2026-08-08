package com.mokatest.platform.demos.api.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.api.domain.Environment;
import com.mokatest.platform.demos.api.domain.GlobalVar;
import com.mokatest.platform.demos.api.domain.requestModel.RequestParameter;
import com.mokatest.platform.demos.api.mapper.EnvironmentMapper;
import com.mokatest.platform.demos.api.mapper.GlobalVarMapper;
import com.mokatest.platform.demos.domain.ui.Project;
import com.mokatest.platform.demos.domain.ui.SceneSetting;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * UI 场景（Playwright 引擎）环境支持。
 *
 * 背景：UI 场景里插入的 HTTP/SQL 步骤由
 * {@link ApiStepExecutor} 执行，需要与 API 场景一致的环境能力（环境变量、baseUrl、
 * 环境 Header/Cookie、SQL 环境级数据库连接）。本类负责在会话初始化阶段：
 * 1. 从场景配置（sceneSetting.apiSceneConfig.environmentId，UI/API 场景共用该字段）解析环境；
 * 2. 把全局变量（按场景所属项目 → 团队）和环境变量注入会话共享变量池，
 *    供 UI 步骤 ${var} 引用以及 API/SQL 步骤执行时合并。
 *
 * 优先级：全局变量 &lt; 环境变量（同名覆盖）；步骤提取变量在执行期回写，优先级最高。
 */
@Slf4j
@Component
public class SceneEnvironmentSupport {

    @Resource
    private EnvironmentMapper environmentMapper;

    @Resource
    private GlobalVarMapper globalVarMapper;

    @Resource
    private ProjectMapper projectMapper;

    /**
     * 从场景配置解析环境；未配置或环境已删除时返回 null（按无环境执行，不阻塞）。
     */
    public Environment resolveSceneEnvironment(SceneSetting sceneSetting) {
        if (sceneSetting == null || sceneSetting.getApiSceneConfig() == null) {
            return null;
        }
        Integer environmentId = sceneSetting.getApiSceneConfig().getEnvironmentId();
        if (environmentId == null) {
            return null;
        }
        Environment env = environmentMapper.selectById(environmentId);
        if (env == null) {
            log.warn("[UI场景环境] 环境配置不存在, environmentId={}", environmentId);
        }
        return env;
    }

    /**
     * 把全局变量（按项目→团队）和环境变量注入共享变量池。
     *
     * @param variables 会话共享变量池
     * @param env       场景环境，可为 null（null 时仅注入全局变量）
     * @param projectId 场景所属项目ID（Scene.projectId 为 String），用于解析团队级全局变量
     */
    public void injectEnvironmentVariables(Map<String, Object> variables, Environment env, String projectId) {
        if (variables == null) {
            return;
        }
        // 1. 全局变量（优先级最低）
        Integer teamId = resolveTeamId(projectId);
        if (teamId != null) {
            List<GlobalVar> globalVars = globalVarMapper.selectList(
                    new QueryWrapper<GlobalVar>()
                            .eq("team_id", teamId)
                            .eq("disabled", false)
            );
            for (GlobalVar gv : globalVars) {
                if (gv.getName() != null) {
                    variables.put(gv.getName(), gv.getValue());
                }
            }
        }
        // 2. 环境变量（覆盖同名全局变量）
        if (env != null && env.getEnvVar() != null) {
            for (RequestParameter varParam : env.getEnvVar()) {
                if (varParam != null && !varParam.isDisabled() && varParam.getName() != null) {
                    variables.put(varParam.getName(), varParam.getValue());
                }
            }
        }
    }

    private Integer resolveTeamId(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return null;
        }
        try {
            Project project = projectMapper.selectById(Integer.valueOf(projectId));
            return project != null ? project.getTeamId() : null;
        } catch (NumberFormatException e) {
            log.warn("[UI场景环境] 项目ID格式异常, projectId={}", projectId);
            return null;
        }
    }
}
