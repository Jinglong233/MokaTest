import axios from "axios";

// ==================== 需求池 ====================

// 需求统计（全项目口径，供顶部统计卡片）
export function getRequirementStats(projectId: number) {
    return axios.get('/api/qa/requirement/stats', { params: { projectId } });
}

// BUG 统计
export function getBugStats(projectId: number) {
    return axios.get('/api/qa/bug/stats', { params: { projectId } });
}

// 用例统计
export function getTestCaseStats(projectId: number) {
    return axios.get('/api/qa/testCase/stats', { params: { projectId } });
}

// 测试计划统计
export function getTestPlanStats(projectId: number) {
    return axios.get('/api/qa/testPlan/stats', { params: { projectId } });
}

export function getRequirementList(projectId: number, keyword?: string, status?: string, moduleId?: number, reqType?: string, source?: string, page?: number, pageSize?: number) {
    return axios.get('/api/qa/requirement/list', {
        params: { projectId, keyword, status, moduleId, reqType, source, page, pageSize }
    });
}

export function saveRequirement(data: any) {
    return axios.post('/api/qa/requirement/save', data);
}

export function updateRequirement(data: any) {
    return axios.post('/api/qa/requirement/update', data);
}

// 需求-用例/BUG 取消关联（需求预览抽屉操作列）
export function unbindRequirementCase(requirementId: number, caseId: number) {
    return axios.post('/api/qa/requirement/unbindCase', null, { params: { requirementId, caseId } });
}

export function unbindRequirementBug(requirementId: number, bugId: number) {
    return axios.post('/api/qa/requirement/unbindBug', null, { params: { requirementId, bugId } });
}

export function deleteRequirement(id: number) {
    return axios.post(`/api/qa/requirement/delete/${id}`);
}

export function batchDeleteRequirement(ids: number[]) {
    return axios.post('/api/qa/requirement/batchDelete', ids);
}

export function getRequirementDetail(id: number) {
    return axios.get(`/api/qa/requirement/${id}`);
}

export function transitionRequirementStatus(requirementId: number, targetStatus: string) {
    return axios.post('/api/qa/requirement/transition', null, {
        params: { requirementId, targetStatus }
    });
}

export function getRequirementTraceability(id: number) {
    return axios.get(`/api/qa/requirement/${id}/trace`);
}

// ==================== BUG池 ====================

export function getBugList(projectId: number, keyword?: string, status?: string, severity?: string, priority?: string, requirementId?: number, caseId?: number, moduleId?: number, environment?: string, reproduceRate?: string, closeReason?: string, page?: number, pageSize?: number, requirementScopeId?: number) {
    return axios.get('/api/qa/bug/list', {
        params: { projectId, keyword, status, severity, priority, requirementId, testCaseId: caseId, moduleId, environment, reproduceRate, closeReason, requirementScopeId, page, pageSize }
    });
}

export function saveBug(data: any) {
    return axios.post('/api/qa/bug/save', data);
}

export function updateBug(data: any) {
    return axios.post('/api/qa/bug/update', data);
}

// BUG 关联/解绑用例（testCaseId 不传表示解绑）
export function bindBugCase(bugId: number, testCaseId?: number | null) {
    return axios.post('/api/qa/bug/bindCase', null, {
        params: { bugId, testCaseId: testCaseId ?? undefined }
    });
}

export function bindBugCases(bugId: number, testCaseIds: number[]) {
    return axios.post('/api/qa/bug/bindCases', testCaseIds, {
        params: { bugId }
    });
}

export function unbindBugCase(bugId: number, testCaseId: number) {
    return axios.post('/api/qa/bug/unbindCase', null, {
        params: { bugId, testCaseId }
    });
}

// BUG 关联/解绑计划用例（planCaseId 传 null 表示解绑）
export function bindBugPlanCase(bugId: number, planCaseId?: number | null) {
    return axios.post('/api/qa/bug/bindPlanCase', null, {
        params: { bugId, planCaseId: planCaseId ?? undefined }
    });
}

export function getBugLinkedCases(bugId: number) {
    return axios.get('/api/qa/bug/linkedCases', {
        params: { bugId }
    });
}

// 查询测试计划执行中关联某用例的 BUG（plan_case_id 维度）
export function getBugListByPlanCase(testCaseId: number) {
    return axios.get('/api/qa/bug/listByPlanCase', {
        params: { testCaseId }
    });
}

export function deleteBug(id: number) {
    return axios.post(`/api/qa/bug/delete/${id}`);
}

export function batchDeleteBug(ids: number[]) {
    return axios.post('/api/qa/bug/batchDelete', ids);
}

export function getBugDetail(id: number) {
    return axios.get(`/api/qa/bug/${id}`);
}

export function transitionBugStatus(bugId: number, targetStatus: string) {
    return axios.post('/api/qa/bug/transition', null, {
        params: { bugId, targetStatus }
    });
}

// ==================== 用例列表 ====================

export function getTestCaseList(projectId: number, moduleId?: number, setId?: number, requirementId?: number, keyword?: string, lastResult?: string, page?: number, pageSize?: number, excludePlanId?: number, status?: string) {
    return axios.get('/api/qa/testCase/list', {
        params: { projectId, moduleId, setId, requirementId, keyword, lastResult, page, pageSize, excludePlanId, status }
    });
}

// ==================== Bug评论 ====================

export function getBugCommentList(bugId: number) {
    return axios.get('/api/qa/bug/comment/list', {
        params: { bugId }
    });
}

export function saveBugComment(data: any) {
    return axios.post('/api/qa/bug/comment/save', data);
}

export function deleteBugComment(id: number) {
    return axios.post(`/api/qa/bug/comment/delete/${id}`);
}

// ==================== Bug操作日志 ====================

export function getBugOperationLogList(bugId: number) {
    return axios.get('/api/qa/bug/operationLog/list', {
        params: { bugId }
    });
}

// ==================== 用例执行历史 ====================

export function getTestCaseExecutionHistory(testCaseId: number) {
    return axios.get('/api/qa/testPlan/executionHistory', {
        params: { testCaseId }
    });
}

// ==================== 用例评论 ====================

export function getTestCaseCommentList(testCaseId: number) {
    return axios.get('/api/qa/testCase/comment/list', {
        params: { testCaseId }
    });
}

export function saveTestCaseComment(data: any) {
    return axios.post('/api/qa/testCase/comment/save', data);
}

export function updateTestCaseComment(data: any) {
    return axios.post('/api/qa/testCase/comment/update', data);
}

export function deleteTestCaseComment(id: number) {
    return axios.post(`/api/qa/testCase/comment/delete/${id}`);
}

export function saveTestCase(data: any) {
    return axios.post('/api/qa/testCase/save', data);
}

export function updateTestCase(data: any) {
    return axios.post('/api/qa/testCase/update', data);
}

export function deleteTestCase(id: number) {
    return axios.post(`/api/qa/testCase/delete/${id}`);
}

export function batchDeleteTestCase(ids: number[]) {
    return axios.post('/api/qa/testCase/batchDelete', ids);
}

export function batchMoveTestCase(ids: number[], moduleId: number) {
    return axios.post('/api/qa/testCase/batchMove', null, {
        params: { ids: ids.join(','), moduleId }
    });
}

// 批量排序：按传入 id 顺序重排 sort 值（脑图兄弟节点顺序持久化）
export function sortTestCase(ids: number[]) {
    return axios.post('/api/qa/testCase/sort', ids);
}

export function getTestCaseDetail(id: number) {
    return axios.get(`/api/qa/testCase/${id}`);
}

export function transitionTestCaseStatus(testCaseId: number, targetStatus: string, comment?: string) {
    return axios.post('/api/qa/testCase/transition', null, {
        params: { testCaseId, targetStatus, comment }
    });
}

// ==================== 用例操作日志 ====================

export function getTestCaseOperationLogList(caseId: number) {
    return axios.get('/api/qa/testCase/operationLog/list', {
        params: { caseId }
    });
}

// ==================== 用例版本 ====================

export function getTestCaseVersionList(caseId: number) {
    return axios.get('/api/qa/testCase/version/list', {
        params: { caseId }
    });
}

export function getTestCaseVersionDetail(id: number) {
    return axios.get(`/api/qa/testCase/version/${id}`);
}

export function rollbackTestCaseVersion(versionId: number) {
    return axios.post('/api/qa/testCase/version/rollback', null, {
        params: { versionId }
    });
}

export function exportTestCase(projectId: number, moduleId?: number, setId?: number) {
    return axios.get('/api/qa/testCase/export', {
        params: { projectId, moduleId, setId },
        responseType: 'blob'
    });
}

// ==================== 用例文件夹 ====================

// ==================== 用例文件夹（已废弃，保留接口兼容）====================

// ==================== 测试集 ====================

export function getTestCaseSetList(projectId: number) {
    return axios.get('/api/qa/testCaseSet/list', {
        params: { projectId }
    });
}

export function getTestCaseSetOptions(projectId: number) {
    return axios.get('/api/qa/testCaseSet/options', {
        params: { projectId }
    });
}

export function getTestCaseSetByCaseId(caseId: number) {
    return axios.get(`/api/qa/testCaseSet/byCase/${caseId}`);
}

export function saveTestCaseSet(data: any) {
    return axios.post('/api/qa/testCaseSet/save', data);
}

export function updateTestCaseSet(data: any) {
    return axios.post('/api/qa/testCaseSet/update', data);
}

export function deleteTestCaseSet(id: number) {
    return axios.post(`/api/qa/testCaseSet/delete/${id}`);
}

export function bindTestCaseSets(caseId: number, setIds: number[]) {
    return axios.post(`/api/qa/testCaseSet/bind/${caseId}`, setIds);
}

// ==================== 关联自动化 ====================

export function bindAuto(testCaseId: number, autoType: string, autoId: number, bindRemark?: string) {
    return axios.post('/api/qa/testCase/bindAuto', null, {
        params: { testCaseId, autoType, autoId, bindRemark }
    });
}

export function unbindAuto(bindId: number) {
    return axios.post('/api/qa/testCase/unbindAuto', null, {
        params: { bindId }
    });
}

export function getBindAutoList(caseId: number) {
    return axios.get(`/api/qa/testCase/bindAuto/${caseId}`);
}

export function getAutoOptions(autoType: string, projectId?: number) {
    return axios.get('/api/qa/testCase/autoOptions', {
        params: { autoType, projectId }
    });
}

// ==================== 模块管理 ====================

export function getQaModuleList(projectId: number, moduleType?: string) {
    return axios.get('/api/qa/module/list', {
        params: { projectId, moduleType: moduleType || 'CASE' }
    });
}

export function getQaModuleTree(projectId: number, moduleType?: string) {
    return axios.get('/api/qa/module/tree', {
        params: { projectId, moduleType: moduleType || 'CASE' }
    });
}

export function getProjectOverview(projectId: number) {
    return axios.get(`/api/qa/overview/${projectId}`);
}

export function saveQaModule(data: any) {
    return axios.post('/api/qa/module/save', data);
}

export function updateQaModule(data: any) {
    return axios.post('/api/qa/module/update', data);
}

export function deleteQaModule(id: number) {
    return axios.post(`/api/qa/module/delete/${id}`);
}

export function sortQaModule(moduleId: number, targetParentId: number, targetIndex: number) {
    return axios.post('/api/qa/module/sort', null, {
        params: { moduleId, targetParentId, targetIndex }
    });
}

// ==================== 测试计划 ====================

export function getTestPlanList(projectId: number, keyword?: string, status?: string, moduleId?: number, page?: number, pageSize?: number) {
    return axios.get('/api/qa/testPlan/list', {
        params: { projectId, keyword, status, moduleId, page, pageSize }
    });
}

export function saveTestPlan(data: any) {
    return axios.post('/api/qa/testPlan/save', data);
}

export function updateTestPlan(data: any) {
    return axios.post('/api/qa/testPlan/update', data);
}

export function deleteTestPlan(id: number) {
    return axios.post(`/api/qa/testPlan/delete/${id}`);
}

export function getTestPlanDetail(id: number) {
    return axios.get(`/api/qa/testPlan/${id}`);
}

export function addCasesToPlan(planId: number, caseIds: number[]) {
    return axios.post('/api/qa/testPlan/addCases', caseIds, {
        params: { planId }
    });
}

export function removeCaseFromPlan(planCaseId: number) {
    return axios.post('/api/qa/testPlan/removeCase', null, {
        params: { planCaseId }
    });
}

export function updateCaseTestPoint(planId: number, planCaseId: number, testPointId: number | null) {
    return axios.post('/api/qa/testPlan/updateCaseTestPoint', null, {
        params: { planId, planCaseId, testPointId }
    });
}

export function executePlanCase(planCaseId: number, result: string, remark?: string) {
    return axios.post('/api/qa/testPlan/execute', null, {
        params: { planCaseId, result, remark }
    });
}

export function batchExecutePlanCases(planId: number, planCaseIds: number[], result: string) {
    return axios.post('/api/qa/testPlan/batchExecute', planCaseIds, {
        params: { planId, result }
    });
}

export function generateBugFromPlanCase(planCaseId: number, bugData: any) {
    return axios.post('/api/qa/testPlan/generateBug', bugData, {
        params: { planCaseId }
    });
}

export function getTestPlanReport(planId: number) {
    return axios.get(`/api/qa/testPlan/${planId}/report`);
}

// ===== 测试计划报告（快照） =====
export function getPlanReportList(projectId: number, keyword?: string, page?: number, pageSize?: number) {
    return axios.get('/api/qa/testPlanReport/list', {
        params: { projectId, keyword, page, pageSize }
    });
}

export function generatePlanReport(planId: number) {
    return axios.post('/api/qa/testPlanReport/generate', null, {
        params: { planId }
    });
}

export function getPlanReportDetail(id: number) {
    return axios.get(`/api/qa/testPlanReport/${id}`);
}

export function updatePlanReportSummary(id: number, summary: string) {
    return axios.post('/api/qa/testPlanReport/summary', { id, summary });
}

export function deletePlanReport(id: number) {
    return axios.post(`/api/qa/testPlanReport/delete/${id}`);
}

// 测试点：获取计划测试点树（含关联用例）
export function getTestPoints(planId: number) {
    return axios.get(`/api/qa/testPlan/${planId}/testPoints`);
}

// 测试点：保存测试点树（增删改 + 用例关联）
export function saveTestPoints(planId: number, testPoints: any[]) {
    return axios.post(`/api/qa/testPlan/${planId}/testPoints/save`, testPoints);
}

// 计划用例列表（含执行人 executorId/executorName、阻塞原因 blockReason）
export function getTestPlanCaseList(planId: number) {
    return axios.get('/api/qa/testPlan/caseList', {
        params: { planId }
    });
}

// 分配计划用例执行人
export function assignPlanCaseExecutor(planCaseId: number, executorId: number) {
    return axios.post('/api/qa/testPlan/assignExecutor', { planCaseId, executorId });
}

// 执行计划用例（携带步骤结果 stepResults / 阻塞原因 blockReason）
export function executePlanCaseWithSteps(planCaseId: number, result: string, remark?: string, blockReason?: string, stepResults?: any[]) {
    return axios.post('/api/qa/testPlan/execute', stepResults || [], {
        params: { planCaseId, result, remark, blockReason }
    });
}

// 计划维度执行记录（执行动态时间线）
export function getTestPlanExecutionHistory(planId: number) {
    return axios.get('/api/qa/testPlan/executionHistory', {
        params: { planId }
    });
}

// ==================== 计划关联自动化 ====================

export function getTestPlanAutoRefList(planId: number) {
    return axios.get('/api/qa/testPlan/autoRef/list', {
        params: { planId }
    });
}

export function saveTestPlanAutoRef(data: { planId: number; refType: string; refId: number }) {
    return axios.post('/api/qa/testPlan/autoRef/save', data);
}

export function deleteTestPlanAutoRef(id: number) {
    return axios.post(`/api/qa/testPlan/autoRef/delete/${id}`);
}

export function executeTestPlanAutoRef(id: number) {
    return axios.post(`/api/qa/testPlan/autoRef/execute/${id}`);
}

// ==================== 用例评审 ====================

// 评审单列表
export function getTestCaseReviewList(projectId: number, status?: string, keyword?: string, page?: number, pageSize?: number, moduleId?: number) {
    return axios.get('/api/qa/testCaseReview/list', {
        params: { projectId, status, keyword, page, pageSize, moduleId }
    });
}

// 待我评审的评审单
export function getTestCaseReviewMyTodo() {
    return axios.get('/api/qa/testCaseReview/myTodo');
}

// 新建评审单 {projectId,title,description,passRule,deadline,caseIds[],reviewerIds[]}
export function saveTestCaseReview(data: any) {
    return axios.post('/api/qa/testCaseReview/save', data);
}

// 编辑评审单
export function updateTestCaseReview(id: number, data: any) {
    return axios.post(`/api/qa/testCaseReview/update/${id}`, data);
}

// 评审单详情（含用例、各评审人结论）
export function getTestCaseReviewDetail(id: number) {
    return axios.get(`/api/qa/testCaseReview/detail/${id}`);
}

// 提交评审结论 {reviewId, testCaseId, conclusion, comment}
export function submitTestCaseReview(data: any) {
    return axios.post('/api/qa/testCaseReview/submit', data);
}

// 取消评审单
export function cancelTestCaseReview(id: number) {
    return axios.post(`/api/qa/testCaseReview/cancel/${id}`);
}

// 从评审单中移除关联用例
export function removeReviewCase(reviewId: number, testCaseId: number) {
    return axios.post('/api/qa/testCaseReview/removeCase', null, {
        params: { reviewId, testCaseId }
    });
}

// 向已有评审单追加用例
export function addReviewCases(reviewId: number, caseIds: number[]) {
    return axios.post('/api/qa/testCaseReview/addCases', caseIds, {
        params: { reviewId }
    });
}

// 调整单条用例的评审人
export function updateReviewCaseReviewers(reviewId: number, testCaseId: number, reviewerIds: number[]) {
    return axios.post('/api/qa/testCaseReview/updateCaseReviewers', reviewerIds, {
        params: { reviewId, testCaseId }
    });
}

// 重新评审：重置单条用例的评审结果与明细为待评审
export function resetReviewCase(reviewId: number, testCaseId: number) {
    return axios.post('/api/qa/testCaseReview/resetCase', null, {
        params: { reviewId, testCaseId }
    });
}

// 删除评审单
export function deleteTestCaseReview(id: number) {
    return axios.post(`/api/qa/testCaseReview/delete/${id}`);
}

// 批量删除评审单
export function batchDeleteTestCaseReview(ids: number[]) {
    return axios.post('/api/qa/testCaseReview/batchDelete', ids);
}

// 批量移动评审单到指定模块
export function batchMoveTestCaseReview(ids: number[], moduleId: number) {
    return axios.post('/api/qa/testCaseReview/batchMove', null, {
        params: { ids: ids.join(','), moduleId }
    });
}

// 查询指定用例的评审历史
export function getCaseReviewHistory(testCaseId: number) {
    return axios.get(`/api/qa/testCaseReview/caseHistory/${testCaseId}`);
}

// ==================== 自动化概览 ====================

export function getAutoOverview(projectId: number) {
    return axios.get(`/api/auto/overview/${projectId}`);
}

// ==================== BUG 回归沉淀 ====================

// BUG 沉淀为回归用例 body: {bugId, folderId?}，返回新用例ID
export function generateRegressionCase(data: { bugId: number; folderId?: number }) {
    return axios.post('/api/qa/bug/generateRegressionCase', data);
}

// 用例文件夹树（根节点 id=0 为虚拟节点"全部用例"）
export function getTestCaseFolderTree(projectId: number) {
    return axios.get('/api/qa/testCaseFolder/tree', { params: { projectId } });
}

// ==================== 测试计划报告 ====================

export function generateTestPlanReport(planId: number) {
    return axios.post('/api/qa/testPlan/report/generate', { planId });
}

export function getTestPlanReportList(planId: number) {
    return axios.get('/api/qa/testPlan/report/list', { params: { planId } });
}

export function getTestPlanReportDetail(id: number) {
    return axios.get(`/api/qa/testPlan/report/${id}`);
}

export function saveTestPlanReportConclusion(data: { reportId: number; conclusion: string; conclusionNote?: string }) {
    return axios.post('/api/qa/testPlan/report/conclusion', data);
}

export function archiveTestPlan(planId: number) {
    return axios.post(`/api/qa/testPlan/report/archive/${planId}`);
}

// ==================== 质量大盘 ====================

export function getQualityOverview(projectId: number) {
    return axios.get('/api/qa/overview/quality', { params: { projectId } });
}

// ==================== 测试工作台 ====================

// 项目级测试工作台待办聚合
export function getQaWorkbenchTodo(projectId: number) {
    return axios.get('/api/qa/workbench/todo', { params: { projectId } });
}

// ==================== 评审批量提交 / 计划用例关联BUG ====================

// 批量提交评审结论 body: {reviewId, testCaseIds[], conclusion, comment}
export function batchReviewSubmit(data: { reviewId: number; testCaseIds: number[]; conclusion: string; comment?: string }) {
    return axios.post('/api/qa/testCaseReview/batchSubmit', data);
}

// 计划用例关联已有BUG body: {planCaseId, bugId}
export function associatePlanBug(data: { planCaseId: number; bugId: number }) {
    return axios.post('/api/qa/testPlan/associateBug', data);
}
