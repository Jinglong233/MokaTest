package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.service.TestCaseAutoBindService;
import com.mokatest.platform.demos.qa.service.TestCaseService;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 文字用例管理接口
 *
 * 提供用例的增删改查、树形结构、关联自动化等能力。
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link SaCheckPermission} 注解声明。
 *   查看列表/树/详情/导出：qa:testcase:view
 *   创建用例：qa:testcase:create
 *   编辑用例：qa:testcase:update
 *   删除用例：qa:testcase:delete
 *   状态流转：qa:testcase:update（与编辑共用）
 *   绑定/解绑自动化：qa:testcase:update
 * admin 角色默认拥有上述所有权限。
 */
@Slf4j
@RestController
@RequestMapping("/qa/testCase")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService testCaseService;
    private final TestCaseAutoBindService autoBindService;

    /**
     * 用例列表
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer projectId,
                         @RequestParam(required = false) Integer moduleId,
                         @RequestParam(required = false) Integer setId,
                         @RequestParam(required = false) Integer requirementId,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String lastResult,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "10") Integer pageSize,
                         @RequestParam(required = false) Integer excludePlanId) {
        return testCaseService.listByProject(projectId, moduleId, setId, requirementId, keyword, lastResult, page, pageSize, excludePlanId);
    }

    /**
     * 用例统计（全项目口径，供顶部统计卡片）
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/stats")
    public SaResult stats(@RequestParam Integer projectId) {
        return testCaseService.stats(projectId);
    }

    /**
     * 创建用例
     * 权限：qa:testcase:create
     */
    @SaCheckPermission("qa:testcase:create")
    @OperationLog(module = "qa", type = OperateType.CREATE, targetType = "testCase", targetId = "#testCase.id", targetName = "#testCase.caseName")
    @PostMapping("/save")
    public SaResult save(@RequestBody TestCase testCase) {
        return testCaseService.saveOrUpdateCase(testCase);
    }

    /**
     * 编辑用例
     * 权限：qa:testcase:update
     */
    @SaCheckPermission("qa:testcase:update")
    @OperationLog(module = "qa", type = OperateType.UPDATE, targetType = "testCase", targetId = "#testCase.id", targetName = "#testCase.caseName", compareClass = TestCase.class)
    @PostMapping("/update")
    public SaResult update(@RequestBody TestCase testCase) {
        return testCaseService.saveOrUpdateCase(testCase);
    }

    /**
     * 删除用例
     * 权限：qa:testcase:delete
     */
    @SaCheckPermission("qa:testcase:delete")
    @OperationLog(module = "qa", type = OperateType.DELETE, targetType = "testCase", targetId = "#id", compareClass = TestCase.class)
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return testCaseService.deleteCase(id);
    }

    /**
     * 批量删除用例
     * 权限：qa:testcase:delete
     */
    @SaCheckPermission("qa:testcase:delete")
    @OperationLog(module = "qa", type = OperateType.BATCH_DELETE, targetType = "testCase", targetId = "#ids")
    @PostMapping("/batchDelete")
    public SaResult batchDelete(@RequestBody List<Integer> ids) {
        return testCaseService.batchDeleteCase(ids);
    }

    /**
     * 用例详情
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/{id}")
    public SaResult detail(@PathVariable Integer id) {
        return testCaseService.getDetail(id);
    }

    /**
     * 绑定自动化（UI场景 / API用例）
     * 权限：qa:testcase:update
     */
    @SaCheckPermission("qa:testcase:update")
    @OperationLog(module = "qa", type = OperateType.BIND, targetType = "testCase", targetId = "#testCaseId", compareClass = TestCase.class,
            description = "'绑定自动化（' + (#autoType == 'UI_SCENE' ? 'UI场景' : 'API用例') + ' #' + #autoId + '）'")
    @PostMapping("/bindAuto")
    public SaResult bindAuto(@RequestParam Integer testCaseId,
                              @RequestParam String autoType,
                              @RequestParam Integer autoId,
                              @RequestParam(required = false) String bindRemark) {
        return autoBindService.bind(testCaseId, autoType, autoId, bindRemark);
    }

    /**
     * 解绑自动化
     * 权限：qa:testcase:update
     */
    @SaCheckPermission("qa:testcase:update")
    @OperationLog(module = "qa", type = OperateType.UNBIND, targetType = "testCase",
            description = "'解绑自动化（绑定记录#' + #bindId + '）'")
    @PostMapping("/unbindAuto")
    public SaResult unbindAuto(@RequestParam Integer bindId) {
        return autoBindService.unbind(bindId);
    }

    /**
     * 查询已绑定的自动化列表
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/bindAuto/{caseId}")
    public SaResult listBindAuto(@PathVariable Integer caseId) {
        return autoBindService.listByCaseId(caseId);
    }

    /**
     * 查询可绑定的自动化选项（UI场景 / API用例 下拉列表）
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/autoOptions")
    public SaResult autoOptions(@RequestParam String autoType,
                                 @RequestParam(required = false) Integer projectId) {
        return autoBindService.listAutoOptions(autoType, projectId);
    }

    /**
     * 用例状态流转
     * 权限：qa:testcase:update
     */
    @SaCheckPermission("qa:testcase:update")
    @OperationLog(module = "qa", type = OperateType.TRANSITION, targetType = "testCase", targetId = "#testCaseId", compareClass = TestCase.class)
    @PostMapping("/transition")
    public SaResult transition(@RequestParam Integer testCaseId, @RequestParam String targetStatus) {
        return testCaseService.transitionStatus(testCaseId, targetStatus);
    }

    /**
     * 查询所有符合条件的用例ID（用于弹窗全选）
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/listIds")
    public SaResult listIds(@RequestParam Integer projectId,
                            @RequestParam(required = false) Integer moduleId,
                            @RequestParam(required = false) Integer setId,
                            @RequestParam(required = false) Integer requirementId,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String lastResult,
                            @RequestParam(required = false) Integer excludePlanId) {
        return testCaseService.listIdsByProject(projectId, moduleId, setId, requirementId, keyword, lastResult, excludePlanId);
    }

    /**
     * 导出用例为Excel
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/export")
    public void export(@RequestParam Integer projectId,
                        @RequestParam(required = false) Integer moduleId,
                        @RequestParam(required = false) Integer setId,
                        HttpServletResponse response) throws IOException {
        testCaseService.exportExcel(projectId, moduleId, setId, response);
    }
}
