package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.dto.TestCaseReviewSaveDTO;
import com.mokatest.platform.demos.qa.dto.TestCaseReviewSubmitDTO;
import com.mokatest.platform.demos.qa.service.TestCaseReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用例评审接口
 *
 * 权限：查看 qa:review:view，创建 qa:review:create，编辑 qa:review:update，删除 qa:review:delete；
 * 编辑/取消/删除/调整评审人等写操作另由业务层校验「创建人」身份，
 * 提交评审结论由业务层校验「该用例在册评审人」身份。
 */
@Slf4j
@RestController
@RequestMapping("/qa/testCaseReview")
@RequiredArgsConstructor
public class TestCaseReviewController {

    private final TestCaseReviewService testCaseReviewService;

    /**
     * 评审单列表（分页+筛选）
     */
    @SaCheckPermission("qa:review:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer projectId,
                         @RequestParam(required = false) Integer moduleId,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return testCaseReviewService.listByProject(projectId, moduleId, status, keyword, page, pageSize);
    }

    /**
     * 待我评审的评审单列表（当前登录人）
     */
    @SaCheckPermission("qa:review:view")
    @GetMapping("/myTodo")
    public SaResult myTodo() {
        return testCaseReviewService.myTodo();
    }

    /**
     * 创建评审单
     */
    @SaCheckPermission("qa:review:create")
    @PostMapping("/save")
    public SaResult save(@RequestBody TestCaseReviewSaveDTO dto) {
        return testCaseReviewService.saveReview(dto);
    }

    /**
     * 编辑评审单（仅创建人，评审单须为评审中）
     */
    @SaCheckPermission("qa:review:update")
    @PostMapping("/update/{id}")
    public SaResult update(@PathVariable Integer id, @RequestBody TestCaseReviewSaveDTO dto) {
        return testCaseReviewService.updateReview(id, dto);
    }

    /**
     * 评审单详情（评审单 + 用例清单 + 各评审人结论）
     */
    @SaCheckPermission("qa:review:view")
    @GetMapping("/detail/{id}")
    public SaResult detail(@PathVariable Integer id) {
        return testCaseReviewService.getDetail(id);
    }

    /**
     * 提交评审结论
     */
    @SaCheckPermission("qa:review:update")
    @PostMapping("/submit")
    public SaResult submit(@RequestBody TestCaseReviewSubmitDTO dto) {
        return testCaseReviewService.submit(dto);
    }

    /**
     * 批量提交评审结论（对多条用例给出同一结论）
     */
    @SaCheckPermission("qa:review:update")
    @PostMapping("/batchSubmit")
    public SaResult batchSubmit(@RequestBody com.mokatest.platform.demos.qa.dto.TestCaseReviewBatchSubmitDTO dto) {
        return testCaseReviewService.batchSubmit(dto);
    }

    /**
     * 取消评审单（仅创建人）
     */
    @SaCheckPermission("qa:review:update")
    @PostMapping("/cancel/{id}")
    public SaResult cancel(@PathVariable Integer id) {
        return testCaseReviewService.cancel(id);
    }

    /**
     * 删除评审单（仅创建人，任意状态可删；关联数据物理删除，本体逻辑删除）
     */
    @SaCheckPermission("qa:review:delete")
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return testCaseReviewService.deleteReview(id);
    }

    /**
     * 批量删除评审单（仅创建人，逐条校验）
     */
    @SaCheckPermission("qa:review:delete")
    @PostMapping("/batchDelete")
    public SaResult batchDelete(@RequestBody java.util.List<Integer> ids) {
        return testCaseReviewService.batchDeleteReview(ids);
    }

    /**
     * 批量移动评审单到指定模块
     */
    @SaCheckPermission("qa:review:update")
    @PostMapping("/batchMove")
    public SaResult batchMove(@RequestParam java.util.List<Integer> ids, @RequestParam Integer moduleId) {
        return testCaseReviewService.batchMoveModule(ids, moduleId);
    }

    /**
     * 从评审单中移除关联用例（仅创建人）
     */
    @SaCheckPermission("qa:review:update")
    @PostMapping("/removeCase")
    public SaResult removeCase(@RequestParam Integer reviewId, @RequestParam Integer testCaseId) {
        return testCaseReviewService.removeCase(reviewId, testCaseId);
    }

    /**
     * 向已有评审单追加用例（仅创建人）
     */
    @SaCheckPermission("qa:review:update")
    @PostMapping("/addCases")
    public SaResult addCases(@RequestParam Integer reviewId, @RequestBody java.util.List<Integer> caseIds) {
        return testCaseReviewService.addCases(reviewId, caseIds);
    }

    /**
     * 调整单条用例的评审人（仅创建人，评审单须为评审中）
     */
    @SaCheckPermission("qa:review:update")
    @PostMapping("/updateCaseReviewers")
    public SaResult updateCaseReviewers(@RequestParam Integer reviewId, @RequestParam Integer testCaseId,
                                        @RequestBody java.util.List<Integer> reviewerIds) {
        return testCaseReviewService.updateCaseReviewers(reviewId, testCaseId, reviewerIds);
    }

    /**
     * 重新评审：重置单条用例的评审结果与明细为待评审（仅创建人；已完结评审单自动重开）
     */
    @SaCheckPermission("qa:review:update")
    @PostMapping("/resetCase")
    public SaResult resetCase(@RequestParam Integer reviewId, @RequestParam Integer testCaseId) {
        return testCaseReviewService.resetCase(reviewId, testCaseId);
    }

    /**
     * 查询指定用例的评审历史（跨所有评审单）
     */
    @SaCheckPermission("qa:review:view")
    @GetMapping("/caseHistory/{testCaseId}")
    public SaResult caseHistory(@PathVariable Integer testCaseId) {
        return testCaseReviewService.getCaseReviewHistory(testCaseId);
    }
}
