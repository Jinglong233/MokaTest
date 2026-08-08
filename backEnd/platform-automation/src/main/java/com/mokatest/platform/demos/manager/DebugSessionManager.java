package com.mokatest.platform.demos.manager;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.config.DebugWebSocketHandler;
import com.mokatest.platform.demos.debug.PlaywrightDebugSession;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.domain.ui.SceneSetting;
import com.mokatest.platform.demos.domain.ui.uiEnum.DebuggerState;
import com.mokatest.platform.demos.mapper.SceneMapper;
import com.mokatest.platform.demos.step.Node.StepBuilder;
import com.mokatest.platform.demos.step.Node.StepTreeNode;
import com.google.gson.Gson;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DebugSessionManager {
    private final Map<String, PlaywrightDebugSession> debugSessionMap = new ConcurrentHashMap<>();

    public void createSession(String sessionId) {
        debugSessionMap.put(sessionId, new PlaywrightDebugSession());
    }


    @Resource
    private StepBuilder stepBuilder;


    @Resource
    private SceneMapper sceneMapper;

    @Resource
    private com.mokatest.platform.demos.api.service.SceneEnvironmentSupport sceneEnvironmentSupport;

    public boolean startDebugSession(String sessionId, Integer sceneId, Integer untilStepId) {
        PlaywrightDebugSession playwrightDebugSession = debugSessionMap.get(sessionId);
        if (playwrightDebugSession != null) {
            try {
                initSceneInfo(playwrightDebugSession, sceneId);
                // 「执行到此步骤」目标：到达该步骤前自动暂停
                playwrightDebugSession.setRunUntilStepId(untilStepId);
                return playwrightDebugSession.startSession(sessionId);
            } catch (Exception e) {
                log.error("初始化调试场景失败, sessionId={}, sceneId={}", sessionId, sceneId, e);
                DebugWebSocketHandler.sendToSession(sessionId,
                        new Gson().toJson("启动失败: " + e.getMessage()));
                return false;
            }
        }
        return false;
    }

    public void sendCommand(String sessionId, String command) {
        PlaywrightDebugSession session = debugSessionMap.get(sessionId);
        if (session != null) {
            session.sendCommand(command);
        }
    }

    public void closeDebugSession(String sessionId) {
        PlaywrightDebugSession session = debugSessionMap.remove(sessionId);
        if (session != null) {
            session.forceStop();
        }
    }

    /**
     * 获取指定场景当前活跃的调试状态；无活跃调试会话（或会话已结束）时返回 null。
     * 用于步骤写接口在调试运行中拦截修改，暂停/失败挂起状态放行以支持热加载。
     */
    public DebuggerState getDebugStateBySceneId(Integer sceneId) {
        if (sceneId == null) {
            return null;
        }
        for (PlaywrightDebugSession session : debugSessionMap.values()) {
            if (sceneId.equals(session.getScenarioId())) {
                DebuggerState state = session.getState();
                if (state != DebuggerState.FINISHED && state != DebuggerState.FAILED) {
                    return state;
                }
            }
        }
        return null;
    }


    private void initSceneInfo(PlaywrightDebugSession playwrightDebugSession, Integer sceneId) {
        // 设置场景id
        playwrightDebugSession.setScenarioId(sceneId);
        QueryWrapper<Scene> sceneQueryWrapper = new QueryWrapper<>();
        sceneQueryWrapper.eq("id", sceneId);
        Scene scene = sceneMapper.selectOne(sceneQueryWrapper);
        // 设置场景名称，便于日志识别
        playwrightDebugSession.setSceneName(scene != null ? scene.getName() : null);
        // 查询场景关联的配置
        SceneSetting sceneSetting = new Gson().fromJson(scene.getSceneSetting(), SceneSetting.class);
        playwrightDebugSession.setSceneSetting(sceneSetting);
        // 解析场景级环境并注入共享变量池（全局变量 + 环境变量），
        // 供 UI 步骤 ${var} 引用以及 HTTP/SQL 步骤执行时合并（baseUrl/环境Header/数据库连接）
        com.mokatest.platform.demos.api.domain.Environment sceneEnv =
                sceneEnvironmentSupport.resolveSceneEnvironment(sceneSetting);
        playwrightDebugSession.setSceneEnvironment(sceneEnv);
        sceneEnvironmentSupport.injectEnvironmentVariables(
                playwrightDebugSession.getVariables(), sceneEnv, scene != null ? scene.getProjectId() : null);
        List<StepTreeNode> steps = stepBuilder.buildScenarioSteps(sceneId);
        // 添加测试步骤
        steps.forEach(playwrightDebugSession::addStep);
    }
}