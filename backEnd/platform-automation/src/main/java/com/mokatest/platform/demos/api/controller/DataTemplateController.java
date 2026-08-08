package com.mokatest.platform.demos.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.api.domain.DataTemplate;
import com.mokatest.platform.demos.api.domain.vo.DataTemplateTreeNode;
import com.mokatest.platform.demos.api.service.DataTemplateService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 数据模板管理接口（单表化改造后）
 *
 * @author JingLong
 * @since 2026-06-17
 */
@RestController
@RequestMapping("/dataTemplate")
public class DataTemplateController {

    @Resource
    private DataTemplateService dataTemplateService;

    /**
     * 保存或更新数据模板
     */
    @SaCheckPermission("auto:template:update")
    @PostMapping("/save")
    public SaResult save(@RequestBody DataTemplate dataTemplate) {
        return dataTemplateService.saveOrUpdateTemplate(dataTemplate);
    }

    /**
     * 保存或更新文件夹
     */
    @SaCheckPermission("auto:template:update")
    @PostMapping("/folder/save")
    public SaResult saveFolder(@RequestBody DataTemplate folder) {
        return dataTemplateService.saveOrUpdateFolder(folder);
    }

    /**
     * 根据项目id查询模板列表（仅 TEMPLATE 节点）
     */
    @SaCheckPermission("auto:template:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer projectId) {
        return dataTemplateService.listByProject(projectId);
    }

    /**
     * 查询项目下文件夹树（仅 FOLDER 节点）
     */
    @SaCheckPermission("auto:template:view")
    @GetMapping("/folder/list")
    public SaResult folderList(@RequestParam Integer projectId) {
        return dataTemplateService.folderList(projectId);
    }

    /**
     * 查询项目下文件夹+模板的混合树
     */
    @SaCheckPermission("auto:template:view")
    @GetMapping("/tree")
    public SaResult tree(@RequestParam Integer projectId) {
        return dataTemplateService.tree(projectId);
    }

    /**
     * 根据id查询详情
     */
    @SaCheckPermission("auto:template:view")
    @GetMapping("/{id}")
    public SaResult detail(@PathVariable Integer id) {
        return dataTemplateService.getById(id);
    }

    /**
     * 删除节点（逻辑删除）
     * 文件夹会递归删除所有后代节点
     */
    @SaCheckPermission("auto:template:delete")
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return dataTemplateService.deleteById(id);
    }

    /**
     * 删除文件夹（逻辑删除，递归删除所有后代）
     */
    @SaCheckPermission("auto:template:delete")
    @PostMapping("/folder/delete/{id}")
    public SaResult deleteFolder(@PathVariable Integer id) {
        return dataTemplateService.deleteById(id);
    }

    /**
     * 统一拖拽排序/移动
     */
    @SaCheckPermission("auto:template:update")
    @PostMapping("/sort")
    public SaResult sort(@RequestBody List<DataTemplateTreeNode> treeNodes) {
        return dataTemplateService.updateSort(treeNodes);
    }

    /**
     * 复制数据模板（仅 TEMPLATE 节点可复制）
     */
    @SaCheckPermission("auto:template:create")
    @PostMapping("/copy/{id}")
    public SaResult copy(@PathVariable Integer id) {
        return dataTemplateService.copyTemplate(id);
    }

    /**
     * 根据模板生成单条数据
     */
    @SaCheckPermission("auto:template:view")
    @PostMapping("/generate")
    public SaResult generate(@RequestParam Integer id) {
        return dataTemplateService.generate(id);
    }

    /**
     * 根据模板批量生成数据
     */
    @SaCheckPermission("auto:template:view")
    @PostMapping("/batchGenerate")
    public SaResult batchGenerate(@RequestParam Integer id, @RequestParam Integer count) {
        return dataTemplateService.batchGenerate(id, count);
    }

    /**
     * 批量生成并导出（JSON/CSV/EXCEL）
     */
    @SaCheckPermission("auto:template:view")
    @PostMapping("/batchGenerate/export")
    public void batchGenerateExport(@RequestParam Integer id,
                                    @RequestParam Integer count,
                                    @RequestParam String format,
                                    HttpServletResponse response) throws IOException {
        dataTemplateService.batchGenerateExport(id, count, format, response);
    }
}
