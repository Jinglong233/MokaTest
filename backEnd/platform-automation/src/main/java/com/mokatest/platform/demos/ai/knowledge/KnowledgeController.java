package com.mokatest.platform.demos.ai.knowledge;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档接口（项目级）
 *
 * 权限：knowledge:view（查询）/ create / update / delete
 * projectId 一律来自请求头上下文（X-Project-Id），不信前端传参。
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /** 文档列表（含分块数/引用数/索引状态） */
    @SaCheckPermission("knowledge:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam(required = false) String keyword) {
        return SaResult.ok().setData(knowledgeService.listByProject(keyword));
    }

    /** 文档详情（编辑用，含全文） */
    @SaCheckPermission("knowledge:view")
    @GetMapping("/detail")
    public SaResult detail(@RequestParam Long id) {
        return SaResult.ok().setData(knowledgeService.getDetail(id));
    }

    /** 新建/更新文档（内容变更后事务提交自动调度索引） */
    @SaCheckPermission(value = {"knowledge:create", "knowledge:update"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    @PostMapping("/save")
    public SaResult save(@RequestBody KnowledgeDoc body) {
        return SaResult.ok("保存成功，索引构建中").setData(knowledgeService.save(body));
    }

    /** 上传文档（.md/.txt） */
    @SaCheckPermission("knowledge:create")
    @PostMapping("/upload")
    public SaResult upload(@RequestParam("file") MultipartFile file) {
        return SaResult.ok("上传成功，索引构建中").setData(knowledgeService.upload(file));
    }

    /** 删除文档（逻辑删除，分块物理清理；不影响已生成用例） */
    @SaCheckPermission("knowledge:delete")
    @PostMapping("/delete/{id}")
    public SaResult delete(@org.springframework.web.bind.annotation.PathVariable Long id) {
        knowledgeService.delete(id);
        return SaResult.ok("已删除");
    }

    /** 分块预览 */
    @SaCheckPermission("knowledge:view")
    @GetMapping("/chunks")
    public SaResult chunks(@RequestParam Long docId) {
        return SaResult.ok().setData(knowledgeService.listChunks(docId));
    }

    /** 手动重建索引（FAILED 恢复 / 模型配置变更后） */
    @SaCheckPermission("knowledge:update")
    @PostMapping("/rebuildIndex/{id}")
    public SaResult rebuildIndex(@org.springframework.web.bind.annotation.PathVariable Long id) {
        knowledgeService.rebuildIndex(id);
        return SaResult.ok("已触发重建");
    }
}
