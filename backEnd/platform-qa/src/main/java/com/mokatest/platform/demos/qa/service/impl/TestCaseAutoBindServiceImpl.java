package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.mapper.SceneMapper;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.qa.common.ProjectEntityGuard;
import com.mokatest.platform.demos.qa.domain.TestCaseAutoBind;
import com.mokatest.platform.demos.qa.mapper.TestCaseAutoBindMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import com.mokatest.platform.demos.qa.service.TestCaseAutoBindService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 用例与自动化绑定关系 Service 实现
 */
@Service
public class TestCaseAutoBindServiceImpl extends ServiceImpl<TestCaseAutoBindMapper, TestCaseAutoBind> implements TestCaseAutoBindService {

    @Resource
    private SceneMapper sceneMapper;

    @Resource
    private ApiRequestMapper apiRequestMapper;

    @Resource
    private TestCaseMapper testCaseMapper;

    @Override
    public SaResult bind(Integer testCaseId, String autoType, Integer autoId, String bindRemark) {
        if (testCaseId == null || autoType == null || autoId == null) {
            return SaResult.error("缺少必要参数");
        }
        if (!"UI_SCENE".equals(autoType) && !"API_CASE".equals(autoType)) {
            return SaResult.error("无效的自动化类型: " + autoType);
        }
        // 数据边界：用例与自动化资源均须归属当前项目（双向校验，IDOR）
        Integer ctxProjectId = ProjectContextHolder.getProjectId();
        if (ctxProjectId == null) {
            return SaResult.error("缺少项目上下文（X-Project-Id）");
        }
        ProjectEntityGuard.requireInProject(testCaseMapper.selectById(testCaseId));
        if ("UI_SCENE".equals(autoType)) {
            Scene scene = sceneMapper.selectById(autoId);
            if (scene == null || !ctxProjectId.equals(scene.getProjectId())) {
                return SaResult.error("场景不存在或不属于当前项目");
            }
        } else {
            ApiRequest api = apiRequestMapper.selectById(autoId);
            if (api == null || !ctxProjectId.equals(api.getProjectId())) {
                return SaResult.error("接口不存在或不属于当前项目");
            }
        }

        // 查询是否已绑定
        QueryWrapper<TestCaseAutoBind> wrapper = new QueryWrapper<>();
        wrapper.eq("test_case_id", testCaseId)
                .eq("auto_type", autoType)
                .eq("auto_id", autoId);
        TestCaseAutoBind existing = baseMapper.selectOne(wrapper);
        if (existing != null) {
            return SaResult.error("该自动化已绑定到此用例");
        }

        // 获取自动化名称
        String autoName = getAutoName(autoType, autoId);

        TestCaseAutoBind bind = new TestCaseAutoBind();
        bind.setTestCaseId(testCaseId);
        bind.setAutoType(autoType);
        bind.setAutoId(autoId);
        bind.setAutoName(autoName);
        bind.setBindRemark(bindRemark);
        bind.setCreateTime(new Date());

        boolean success = save(bind);
        return success ? SaResult.ok("绑定成功").setData(bind.getId()) : SaResult.error("绑定失败");
    }

    @Override
    public SaResult unbind(Integer bindId) {
        if (bindId == null) {
            return SaResult.error("缺少绑定ID");
        }
        // 数据边界：绑定记录须经其所属用例校验归属当前项目（IDOR）
        TestCaseAutoBind bind = baseMapper.selectById(bindId);
        if (bind == null) {
            return SaResult.error("绑定不存在");
        }
        ProjectEntityGuard.requireInProject(testCaseMapper.selectById(bind.getTestCaseId()));
        boolean success = removeById(bindId);
        return success ? SaResult.ok("解绑成功") : SaResult.error("解绑失败");
    }

    @Override
    public SaResult listByCaseId(Integer caseId) {
        if (caseId == null) {
            return SaResult.error("缺少用例ID");
        }
        // 数据边界：用例须归属当前项目（IDOR）
        ProjectEntityGuard.requireInProject(testCaseMapper.selectById(caseId));
        QueryWrapper<TestCaseAutoBind> wrapper = new QueryWrapper<>();
        wrapper.eq("test_case_id", caseId).orderByDesc("create_time");
        List<TestCaseAutoBind> list = baseMapper.selectList(wrapper);
        return SaResult.ok().setData(list);
    }

    @Override
    public SaResult listAutoOptions(String autoType, Integer projectId) {
        if (autoType == null) {
            return SaResult.error("缺少自动化类型");
        }
        // 数据边界：projectId 改为必传（以当前项目上下文为准，忽略入参防跨项目泄露，IDOR）
        Integer ctxProjectId = ProjectContextHolder.getProjectId();
        if (ctxProjectId == null) {
            return SaResult.error("缺少项目上下文（X-Project-Id）");
        }
        List<Map<String, Object>> options = new ArrayList<>();

        if ("UI_SCENE".equals(autoType)) {
            // 查询场景列表（按当前项目隔离）
            QueryWrapper<Scene> wrapper = new QueryWrapper<>();
            wrapper.eq("project_id", ctxProjectId);
            wrapper.orderByDesc("create_at");
            List<Scene> scenes = sceneMapper.selectList(wrapper);
            for (Scene scene : scenes) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", scene.getId());
                item.put("name", scene.getName());
                item.put("type", "UI_SCENE");
                options.add(item);
            }
        } else if ("API_CASE".equals(autoType)) {
            // 查询API接口/用例列表（按当前项目隔离，避免跨项目泄露）
            QueryWrapper<ApiRequest> wrapper = new QueryWrapper<>();
            wrapper.eq("project_id", ctxProjectId);
            wrapper.orderByDesc("create_time");
            List<ApiRequest> apis = apiRequestMapper.selectList(wrapper);
            for (ApiRequest api : apis) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", api.getId());
                item.put("name", api.getApiName());
                item.put("type", "API_CASE");
                options.add(item);
            }
        }

        return SaResult.ok().setData(options);
    }

    private String getAutoName(String autoType, Integer autoId) {
        try {
            if ("UI_SCENE".equals(autoType) && sceneMapper != null) {
                Scene scene = sceneMapper.selectById(autoId);
                return scene != null ? scene.getName() : null;
            } else if ("API_CASE".equals(autoType) && apiRequestMapper != null) {
                ApiRequest api = apiRequestMapper.selectById(autoId);
                return api != null ? api.getApiName() : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
