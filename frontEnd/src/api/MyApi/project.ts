import axios from "axios";
import {Project} from "@/types/domain/Project";

/**
 * 添加项目
 */
export function addProject(project: Project) {
    return axios.post<Project[]>('/api/project/addProject', project);
}

/**
 * 更新项目
 */
export function updateProject(project: Project) {
    return axios.post<Project[]>('/api/project/updateProject', project);
}

/**
 * 删除项目（逻辑删除）
 */
export function deleteProject(projectId: string | number) {
    return axios.post(`/api/project/deleteProject/${projectId}`);
}

/**
 * 获取项目列表(all)
 */
export function allProject() {
    return axios.get<Project[]>('/api/project/allProject');
}


/**
 * 获取指定团队的项目
 */
export function getProjectListByTeamId(teamId: string) {
    return axios.get<Project[]>('/api/project/getProjectListByTeamId/' + teamId);
}


export function getProjectById(projectId: string) {
    return axios.get<Project>('/api/project/getProjectById/' + projectId);
}


// ============== 项目成员管理 ==============

/**
 * 获取项目成员列表
 */
export function getProjectMembers(projectId: string | number, config?: any) {
    return axios.get(`/api/project/member/list/${projectId}`, config);
}

/**
 * 分配项目角色（项目管理员 / 自定义模板，新增或修改）
 */
export function assignProjectRole(
    data: { projectId: number; userId: number; roleId: number; expireTime?: string },
    config?: any
) {
    return axios.post('/api/project/member/assign', data, config);
}

/**
 * 移除项目成员
 */
export function removeProjectMember(
    data: { projectId: number; userId: number },
    config?: any
) {
    return axios.post('/api/project/member/remove', data, config);
}

/**
 * 判断当前用户是否为该项目管理员
 */
export function isProjectAdmin(projectId: string | number, config?: any) {
    return axios.get(`/api/project/member/isAdmin/${projectId}`, config);
}

/**
 * 项目可分配的角色选项（自定义模板，项目级鉴权）
 */
export function getProjectRoleOptions(projectId: string | number, config?: any) {
    return axios.get(`/api/project/member/roleOptions/${projectId}`, config);
}









