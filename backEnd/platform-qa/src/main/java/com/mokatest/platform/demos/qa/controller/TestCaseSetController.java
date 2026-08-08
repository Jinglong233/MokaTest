package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.domain.TestCaseSet;
import com.mokatest.platform.demos.qa.service.TestCaseSetService;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 测试集管理接口
 */
@Slf4j
@RestController
@RequestMapping("/qa/testCaseSet")
@RequiredArgsConstructor
public class TestCaseSetController {

    private final TestCaseSetService testCaseSetService;

    /**
     * 测试集列表
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer projectId) {
        return testCaseSetService.listByProject(projectId);
    }

    /**
     * 测试集下拉选项
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/options")
    public SaResult options(@RequestParam Integer projectId) {
        return testCaseSetService.listOptions(projectId);
    }

    /**
     * 查询用例所属的测试集
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/byCase/{caseId}")
    public SaResult listByCase(@PathVariable Integer caseId) {
        return testCaseSetService.listByCaseId(caseId);
    }

    /**
     * 保存/更新测试集
     * 权限：创建用 qa:testcase:create，更新用 qa:testcase:update
     */
    @SaCheckPermission("qa:testcase:create")
    @OperationLog(module = "qa", type = OperateType.CREATE, targetType = "testCaseSet", targetId = "#set.id", targetName = "#set.setName")
    @PostMapping("/save")
    public SaResult save(@RequestBody TestCaseSet set) {
        return testCaseSetService.saveOrUpdateSet(set);
    }

    /**
     * 更新测试集
     */
    @SaCheckPermission("qa:testcase:update")
    @OperationLog(module = "qa", type = OperateType.UPDATE, targetType = "testCaseSet", targetId = "#set.id", targetName = "#set.setName", compareClass = TestCaseSet.class)
    @PostMapping("/update")
    public SaResult update(@RequestBody TestCaseSet set) {
        return testCaseSetService.saveOrUpdateSet(set);
    }

    /**
     * 删除测试集
     * 权限：qa:testcase:delete
     */
    @SaCheckPermission("qa:testcase:delete")
    @OperationLog(module = "qa", type = OperateType.DELETE, targetType = "testCaseSet", targetId = "#id", compareClass = TestCaseSet.class)
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return testCaseSetService.deleteSet(id);
    }

    /**
     * 绑定用例与测试集关系
     * 权限：qa:testcase:update
     */
    @SaCheckPermission("qa:testcase:update")
    @PostMapping("/bind/{caseId}")
    public SaResult bindSets(@PathVariable Integer caseId, @RequestBody List<Integer> setIds) {
        return testCaseSetService.bindSets(caseId, setIds);
    }
}
