package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.TestCaseReview;
import com.mokatest.platform.demos.qa.dto.TestCaseReviewSaveDTO;
import com.mokatest.platform.demos.qa.dto.TestCaseReviewSubmitDTO;

import java.util.List;

/**
 * 用例评审 Service
 */
public interface TestCaseReviewService extends IService<TestCaseReview> {

    /**
     * 评审单列表（分页+筛选），返回评审单及进度
     */
    SaResult listByProject(Integer projectId, Integer moduleId, String status, String keyword, Integer page, Integer pageSize);

    /**
     * 待我评审的评审单列表（当前登录人）
     */
    SaResult myTodo();

    /**
     * 待我评审的评审单列表（当前登录人，限指定项目）
     */
    SaResult myTodo(Integer projectId);

    /**
     * 创建评审单（圈选用例批量置为 REVIEWING，站内信通知评审人）
     */
    SaResult saveReview(TestCaseReviewSaveDTO dto);

    /**
     * 评审单详情（评审单 + 用例清单 + 各评审人结论 + 评审人列表）
     */
    SaResult getDetail(Integer id);

    /**
     * 提交评审结论（当前登录人），触发单用例定结与整单自动完成
     */
    SaResult submit(TestCaseReviewSubmitDTO dto);

    /**
     * 批量提交评审结论（当前登录人），逐条复用单条提交逻辑，返回每条处理结果摘要
     */
    SaResult batchSubmit(com.mokatest.platform.demos.qa.dto.TestCaseReviewBatchSubmitDTO dto);

    /**
     * 取消评审单（仅创建人），圈中仍 REVIEWING 的用例回退 DRAFT
     */
    SaResult cancel(Integer id);

    /**
     * 删除评审单（仅创建人，任意状态）：关联数据物理删除，评审单本体逻辑删除；
     * 评审中的单先按取消语义回退圈中用例状态
     */
    SaResult deleteReview(Integer id);

    /** 批量删除评审单（仅创建人，逐条复用单条删除逻辑） */
    SaResult batchDeleteReview(List<Integer> ids);

    /** 批量移动评审单到指定模块 */
    SaResult batchMoveModule(List<Integer> ids, Integer moduleId);

    /**
     * 从评审单中移除关联用例（仅创建人），同时删除该用例的评审明细
     */
    SaResult removeCase(Integer reviewId, Integer testCaseId);

    /**
     * 重新评审：将单条用例的评审结果与所有评审明细重置为待评审（仅创建人；已完结评审单自动重开）
     */
    SaResult resetCase(Integer reviewId, Integer testCaseId);

    /**
     * 向已有评审单追加用例（仅创建人，评审单须为 REVIEWING），自动为已有评审人创建 PENDING 明细
     */
    SaResult addCases(Integer reviewId, List<Integer> caseIds);

    /**
     * 调整单条用例的评审人（仅创建人，评审单须为 REVIEWING），diff 评审明细并重算该用例 finalResult
     */
    SaResult updateCaseReviewers(Integer reviewId, Integer testCaseId, List<Integer> reviewerIds);

    /**
     * 用例删除时的评审清理：将用例从所有「评审中」的评审单移除（删关联+明细、重算 finalResult、
     * 必要时完结评审单）；已完成/已取消的评审单保留历史记录
     */
    void removeFromActiveReviews(List<Integer> testCaseIds);

    /**
     * 编辑评审单（仅创建人，评审单须为 REVIEWING），更新基本信息并 diff 评审人 / 用例
     */
    SaResult updateReview(Integer id, TestCaseReviewSaveDTO dto);

    /**
     * 查询指定用例的评审历史（跨所有评审单）
     */
    SaResult getCaseReviewHistory(Integer testCaseId);
}
