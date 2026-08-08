package com.mokatest.platform.demos.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.api.domain.CustomFunction;
import com.mokatest.platform.demos.api.service.CustomFunctionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 自定义公共函数管理接口
 *
 * @author JingLong
 * @since 2026-07-31
 */
@RestController
@RequestMapping("/customFunction")
public class CustomFunctionController {

    @Resource
    private CustomFunctionService customFunctionService;

    /**
     * 保存或更新自定义函数
     */
    @SaCheckPermission("auto:function:update")
    @PostMapping("/save")
    public SaResult save(@RequestBody CustomFunction customFunction) {
        return customFunctionService.saveOrUpdateFunction(customFunction);
    }

    /**
     * 根据项目id查询函数列表
     */
    @SaCheckPermission("auto:function:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer projectId) {
        return customFunctionService.listByProject(projectId);
    }

    /**
     * 根据id查询详情
     */
    @SaCheckPermission("auto:function:view")
    @GetMapping("/{id}")
    public SaResult detail(@PathVariable Integer id) {
        return customFunctionService.getById(id);
    }

    /**
     * 删除（逻辑删除）
     */
    @SaCheckPermission("auto:function:delete")
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return customFunctionService.deleteById(id);
    }

    /**
     * 试运行：用示例参数在沙箱中执行函数体
     */
    @SaCheckPermission("auto:function:view")
    @PostMapping("/testRun")
    public SaResult testRun(@RequestBody Map<String, Object> body) {
        Integer id = body.get("id") != null ? Integer.valueOf(String.valueOf(body.get("id"))) : null;
        @SuppressWarnings("unchecked")
        List<Object> args = body.get("args") instanceof List ? (List<Object>) body.get("args") : List.of();
        return customFunctionService.testRun(id, args);
    }
}
