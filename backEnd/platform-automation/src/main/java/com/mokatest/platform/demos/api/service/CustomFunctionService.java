package com.mokatest.platform.demos.api.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.api.domain.CustomFunction;

import java.util.List;

/**
 * 自定义公共函数 Service
 *
 * @author JingLong
 * @since 2026-07-31
 */
public interface CustomFunctionService extends IService<CustomFunction> {

    /**
     * 保存或更新自定义函数
     */
    SaResult saveOrUpdateFunction(CustomFunction customFunction);

    /**
     * 按项目查询函数列表（按 sort 升序）
     */
    SaResult listByProject(Integer projectId);

    /**
     * 按 id 查询详情
     */
    SaResult getById(Integer id);

    /**
     * 删除（逻辑删除）
     */
    SaResult deleteById(Integer id);

    /**
     * 试运行：用示例参数在沙箱中执行函数体，返回结果/日志/耗时
     *
     * @param id   函数 id
     * @param args 示例参数（按 funcParams 顺序）
     */
    SaResult testRun(Integer id, List<Object> args);
}
