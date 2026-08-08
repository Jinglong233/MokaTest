package com.mokatest.platform.demos.api.service;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.api.domain.DataTemplate;
import com.mokatest.platform.demos.api.domain.vo.DataTemplateTreeNode;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 数据模板 Service
 *
 * @author JingLong
 * @since 2026-06-17
 */
public interface DataTemplateService {

    /**
     * 保存或更新数据模板
     */
    SaResult saveOrUpdateTemplate(DataTemplate dataTemplate);

    /**
     * 保存或更新文件夹
     */
    SaResult saveOrUpdateFolder(DataTemplate folder);

    /**
     * 根据项目id查询模板列表（仅 TEMPLATE 节点）
     */
    SaResult listByProject(Integer projectId);

    /**
     * 查询项目下文件夹树（仅 FOLDER 节点）
     */
    SaResult folderList(Integer projectId);

    /**
     * 查询项目下文件夹+模板的混合树
     */
    SaResult tree(Integer projectId);

    /**
     * 根据id查询详情
     */
    SaResult getById(Integer id);

    /**
     * 复制数据模板（仅 TEMPLATE 节点可复制）
     */
    SaResult copyTemplate(Integer id);

    /**
     * 删除节点（逻辑删除）
     * 文件夹会递归删除所有后代节点
     */
    SaResult deleteById(Integer id);

    /**
     * 统一拖拽排序/移动
     */
    SaResult updateSort(List<DataTemplateTreeNode> treeNodes);

    /**
     * 根据模板生成单条数据
     */
    SaResult generate(Integer id);

    /**
     * 根据模板批量生成数据
     */
    SaResult batchGenerate(Integer id, Integer count);

    /**
     * 批量生成并导出（JSON/CSV/EXCEL）
     */
    void batchGenerateExport(Integer id, Integer count, String format, HttpServletResponse response) throws IOException;
}
