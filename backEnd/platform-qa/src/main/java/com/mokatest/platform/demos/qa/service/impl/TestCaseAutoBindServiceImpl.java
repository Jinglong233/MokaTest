package com.mokatest.platform.demos.qa.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.mapper.SceneMapper;
import com.mokatest.platform.demos.qa.domain.TestCaseAutoBind;
import com.mokatest.platform.demos.qa.mapper.TestCaseAutoBindMapper;
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

    @Override
    public SaResult bind(Integer testCaseId, String autoType, Integer autoId, String bindRemark) {
        if (testCaseId == null || autoType == null || autoId == null) {
            return SaResult.error("缺少必要参数");
        }
        if (!"UI_SCENE".equals(autoType) && !"API_CASE".equals(autoType)) {
            return SaResult.error("无效的自动化类型: " + autoType);
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
        boolean success = removeById(bindId);
        return success ? SaResult.ok("解绑成功") : SaResult.error("解绑失败");
    }

    @Override
    public SaResult listByCaseId(Integer caseId) {
        if (caseId == null) {
            return SaResult.error("缺少用例ID");
        }
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
        List<Map<String, Object>> options = new ArrayList<>();

        if ("UI_SCENE".equals(autoType)) {
            // 查询场景列表
            QueryWrapper<Scene> wrapper = new QueryWrapper<>();
            if (projectId != null) {
                wrapper.eq("project_id", projectId);
            }
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
            // 查询API接口/用例列表
            QueryWrapper<ApiRequest> wrapper = new QueryWrapper<>();
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
