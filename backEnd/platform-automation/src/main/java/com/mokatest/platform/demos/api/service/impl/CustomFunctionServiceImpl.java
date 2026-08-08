package com.mokatest.platform.demos.api.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.api.domain.CustomFunction;
import com.mokatest.platform.demos.api.mapper.CustomFunctionMapper;
import com.mokatest.platform.demos.api.service.CustomFunctionService;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import com.mokatest.platform.demos.util.CustomFunctionExecutor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义公共函数 Service 实现
 *
 * @author JingLong
 * @since 2026-07-31
 */
@Service
public class CustomFunctionServiceImpl extends ServiceImpl<CustomFunctionMapper, CustomFunction>
        implements CustomFunctionService {

    @Resource
    private ProjectPermissionChecker permissionChecker;

    /**
     * 校验当前登录用户是否有指定项目的访问权限
     */
    private boolean hasProjectAccess(Integer projectId) {
        if (projectId == null) {
            return false;
        }
        String loginId = StpUtil.getLoginIdAsString();
        return permissionChecker.isSuperAdmin(loginId)
                || permissionChecker.hasPermissionByProjectId(projectId, loginId);
    }

    @Override
    public SaResult saveOrUpdateFunction(CustomFunction customFunction) {
        if (customFunction == null) {
            return SaResult.error("函数数据不能为空");
        }
        if (customFunction.getProjectId() == null) {
            return SaResult.error("所属项目不能为空");
        }
        if (customFunction.getTeamId() == null) {
            return SaResult.error("所属团队不能为空");
        }
        if (!hasProjectAccess(customFunction.getProjectId())) {
            return SaResult.error("无权限访问该项目");
        }
        if (customFunction.getFuncName() == null || customFunction.getFuncName().trim().isEmpty()) {
            return SaResult.error("函数名称不能为空");
        }
        // 名称不能含逗号/括号：@fn(名称, 参数) 显示语法按逗号/括号切分，含这些字符会导致解析错位
        if (customFunction.getFuncName().matches(".*[,()（），].*")) {
            return SaResult.error("函数名称不能包含逗号和括号");
        }
        // 项目内名称唯一（@fn(名称) 显示层按名识读，重名会让用户分不清；底层调用靠 id 不受影响）
        if (isFuncNameExists(customFunction.getProjectId(), customFunction.getFuncName().trim(), customFunction.getId())) {
            return SaResult.error("当前项目已存在同名函数，请换一个名称");
        }
        if (customFunction.getFuncCode() == null || customFunction.getFuncCode().trim().isEmpty()) {
            return SaResult.error("函数体不能为空");
        }
        // 参数定义只保留合法标识符，防止拼接 function() 时注入
        String funcParams = sanitizeParams(customFunction.getFuncParams());
        if (funcParams == null) {
            return SaResult.error("参数定义不合法：只支持逗号分隔的标识符（字母/数字/下划线/$，字母开头）");
        }

        Integer userId = StpUtil.getLoginIdAsInt();
        Date now = new Date();
        customFunction.setFuncName(customFunction.getFuncName().trim());
        customFunction.setFuncParams(funcParams);

        if (customFunction.getId() == null) {
            customFunction.setCreateUserId(userId);
            customFunction.setCreateTime(now);
            customFunction.setUpdateUserId(userId);
            customFunction.setUpdateTime(now);
            if (customFunction.getSort() == null) {
                customFunction.setSort(calcNextSort(customFunction.getProjectId()));
            }
            baseMapper.insert(customFunction);
        } else {
            CustomFunction existed = baseMapper.selectById(customFunction.getId());
            if (existed == null) {
                return SaResult.error("函数不存在");
            }
            if (!hasProjectAccess(existed.getProjectId())) {
                return SaResult.error("无权限访问该函数");
            }
            customFunction.setUpdateUserId(userId);
            customFunction.setUpdateTime(now);
            customFunction.setCreateUserId(null);
            customFunction.setCreateTime(null);
            baseMapper.updateById(customFunction);
        }
        return SaResult.ok("保存成功").setData(customFunction);
    }

    @Override
    public SaResult listByProject(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("项目id不能为空");
        }
        if (!hasProjectAccess(projectId)) {
            return SaResult.error("无权限访问该项目");
        }
        QueryWrapper<CustomFunction> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId).orderByAsc("sort").orderByAsc("id");
        List<CustomFunction> list = baseMapper.selectList(wrapper);
        return SaResult.ok().setData(list);
    }

    @Override
    public SaResult getById(Integer id) {
        if (id == null) {
            return SaResult.error("函数id不能为空");
        }
        CustomFunction function = baseMapper.selectById(id);
        if (function == null) {
            return SaResult.error("函数不存在");
        }
        if (!hasProjectAccess(function.getProjectId())) {
            return SaResult.error("无权限访问该函数");
        }
        return SaResult.ok().setData(function);
    }

    @Override
    public SaResult deleteById(Integer id) {
        if (id == null) {
            return SaResult.error("函数id不能为空");
        }
        CustomFunction function = baseMapper.selectById(id);
        if (function == null) {
            return SaResult.error("函数不存在");
        }
        if (!hasProjectAccess(function.getProjectId())) {
            return SaResult.error("无权限访问该函数");
        }
        // 逻辑删除（@TableLogic），已引用该函数的表达式在执行时会原样保留并打告警日志
        baseMapper.deleteById(id);
        return SaResult.ok("删除成功");
    }

    @Override
    public SaResult testRun(Integer id, List<Object> args) {
        if (id == null) {
            return SaResult.error("函数id不能为空");
        }
        CustomFunction function = baseMapper.selectById(id);
        if (function == null) {
            return SaResult.error("函数不存在");
        }
        if (!hasProjectAccess(function.getProjectId())) {
            return SaResult.error("无权限访问该函数");
        }
        CustomFunctionExecutor.RunResult result =
                CustomFunctionExecutor.execute(function.getFuncParams(), function.getFuncCode(), args);
        Map<String, Object> data = new HashMap<>();
        data.put("success", result.isSuccess());
        data.put("value", result.getValue());
        data.put("errorMessage", result.getErrorMessage());
        data.put("consoleLogs", result.getConsoleLogs());
        data.put("executionTimeMs", result.getExecutionTimeMs());
        return SaResult.ok().setData(data);
    }

    /**
     * 校验并规范化参数定义：逗号分隔的合法 JS 标识符；不合法返回 null
     */
    private String sanitizeParams(String funcParams) {
        if (funcParams == null || funcParams.trim().isEmpty()) {
            return "";
        }
        String[] parts = funcParams.split(",");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (!p.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                return null;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(p);
        }
        return sb.toString();
    }

    private int calcNextSort(Integer projectId) {
        QueryWrapper<CustomFunction> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId).orderByDesc("sort").last("LIMIT 1");
        CustomFunction last = baseMapper.selectOne(wrapper);
        return last != null && last.getSort() != null ? last.getSort() + 1 : 0;
    }

    /**
     * 项目内函数名称是否已存在（@TableLogic 自动排除已删除）
     */
    private boolean isFuncNameExists(Integer projectId, String funcName, Integer excludeId) {
        QueryWrapper<CustomFunction> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId).eq("func_name", funcName);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        return baseMapper.selectCount(wrapper) > 0;
    }
}
