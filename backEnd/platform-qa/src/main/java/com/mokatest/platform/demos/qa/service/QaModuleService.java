package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.QaModule;

public interface QaModuleService extends IService<QaModule> {

    SaResult listByProject(Integer projectId);

    /**
     * 获取模块树
     * 
     * 按项目查询模块列表并构建树形结构，返回虚拟根节点「全部用例」。
     *
     * @param projectId 项目ID
     * @return 树形结构
     */
    SaResult tree(Integer projectId);

    /**
     * 保存或更新模块
     * 
     * 当 sort 为空时，自动计算为同级节点末尾。
     *
     * @param module 模块实体
     * @return 操作结果
     */
    SaResult saveOrUpdateModule(QaModule module);

    /**
     * 删除模块
     * 
     * 逻辑删除模块，并解绑关联的需求、BUG、用例的 module_id。
     *
     * @param id 模块ID
     * @return 操作结果
     */
    SaResult deleteModule(Integer id);

    /**
     * 拖拽排序模块
     * 
     * 支持调整模块的父级和同级顺序。
     *
     * @param moduleId       被拖动的模块ID
     * @param targetParentId 目标父模块ID（0 表示根模块）
     * @param targetIndex    目标位置索引
     * @return 操作结果
     */
    SaResult sortModule(Integer moduleId, Integer targetParentId, Integer targetIndex);
}
