import axios from "axios";
import {Plan} from "@/types/domain/Plan";


/**
 * 获取所有计划
 */
export function getAllPlan(projetcId: string) {
    return axios.get<Plan[]>('/api/plan/allPlan', {
        params: {
            projectId: projetcId
        }
    })
}

/**
 * 根据id获取计划
 */
export function getPlanById(planId: number) {
    return axios.get<Plan>('/api/plan/getPlanById', {
        params: {
            planId: planId
        }
    })
}

/**
 * 更新计划
 */
export function updatePlan(plan: Plan) {
    return axios.post<boolean>('/api/plan/updatePlan', plan)
}

/**
 * 新建计划
 */
export function add(plan: Plan) {
    return axios.post<boolean>('/api/plan/addPlan', plan)
}

/**
 * 删除计划
 */
export function deletePlan(planId: number) {
    return axios.get<boolean>('/api/plan/deletePlan', {
        params: {
            planId: planId
        }
    })
}

/**
 * 更新计划运行配置
 */
export function updatePlanRunningConfig(planId: any, planRunningSetting: any) {
    return axios.post<boolean>('/api/plan/updatePlanRunningConfig', {
        planId,
        planRunningSetting
    })
}







