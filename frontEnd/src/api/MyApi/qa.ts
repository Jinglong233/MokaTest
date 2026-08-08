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

export function getBugList(projectId: number, keyword?: string, status?: string, severity?: string, priority?: string, requirementId?: number, caseId?: number, moduleId?: number, environment?: string, reproduceRate?: string, closeReason?: string, page?: number, pageSize?: number) {
    return axios.get('/api/qa/bug/list', {
        params: { projectId, keyword, status, severity, priority, requirementId, caseId, moduleId, environment, reproduceRate, closeReason, page, pageSize }
    });
}

export function saveBug(data: any) {
    return axios.post('/api/qa/bug/save', data);
}

export function updateBug(data: any) {
    return axios.post('/api/qa/bug/update', data);
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

export function getTestCaseList(projectId: number, moduleId?: number, setId?: number, requirementId?: number, keyword?: string, lastResult?: string, page?: number, pageSize?: number, excludePlanId?: number) {
    return axios.get('/api/qa/testCase/list', {
        params: { projectId, moduleId, setId, requirementId, keyword, lastResult, page, pageSize, excludePlanId }
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

export function getTestCaseDetail(id: number) {
    return axios.get(`/api/qa/testCase/${id}`);
}

export function transitionTestCaseStatus(testCaseId: number, targetStatus: string) {
    return axios.post('/api/qa/testCase/transition', null, {
        params: { testCaseId, targetStatus }
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

export function getQaModuleList(projectId: number) {
    return axios.get('/api/qa/module/list', {
        params: { projectId }
    });
}

export function getQaModuleTree(projectId: number) {
    return axios.get('/api/qa/module/tree', {
        params: { projectId }
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

export function getTestPlanList(projectId: number, keyword?: string, status?: string, page?: number, pageSize?: number) {
    return axios.get('/api/qa/testPlan/list', {
        params: { projectId, keyword, status, page, pageSize }
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

// ==================== 自动化概览 ====================

export function getAutoOverview(projectId: number) {
    return axios.get(`/api/auto/overview/${projectId}`);
}
