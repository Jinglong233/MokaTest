import axios from "axios";
import {StepVO} from "@/types/vo/StepVO";
import {Report} from "@/types/domain/Report";
import {ReportQueryDTO} from "@/types/dto/queryDTO/ReportQueryDTO";

/**
 * 获取项目下的所有报告
 * @param sceneId
 */
export function getReportList(projectId: string) {
    return axios.get<StepVO[]>('/api/step/stepList', {
        params: {
            sceneId: projectId
        }
    })
}


/**
 * 获取所有报告
 */
export function getAllReport() {
    return axios.get<Report[]>('/api/report/allReport')
}

/**
 * 根据id获取报告详情
 */
export function getReportDetail(reportId: number) {
    return axios.get<Report>('/api/report/reportDetail', {
        params: {
            reportId: reportId
        }
    })
}

/**
 * 分页查询 报告列表
 */
export function reportPageList(queryDTO: ReportQueryDTO) {
    return axios.post<Report[]>('/api/report/reportPageList', queryDTO)
}


/**
 * 删除报告
 * @param reportId
 */
export function deleteReport(reportId: number) {
    return axios.get<boolean>('/api/report/deleteReport', {
        params: {
            reportId
        }
    })
}


/**
 * 重新执行失败场景
 * @param reRun
 */
export function reRunFailScene(failScenes: any) {
    return axios.post<Report>('/api/task/reRun', {
        reTrySceneIds: failScenes.reTrySceneIds,
        sourceReportId: failScenes.sourceReportId
    })
}



