import axios from "axios";
import {Role} from "@/types/domain/Role";
import {Permission} from "@/types/domain/Permission";

/**
 * 获取角色列表
 */
export function getRoleList(teamId?: number) {
    return axios.get('/api/rbac/roles', {
        params: teamId !== undefined && teamId !== null ? { teamId } : undefined
    });
}

/**
 * 创建角色
 */
export function createRole(data: Role) {
    return axios.post('/api/rbac/role/create', data);
}

/**
 * 更新角色
 */
export function updateRole(data: Role) {
    return axios.post('/api/rbac/role/update', data);
}

/**
 * 删除角色
 */
export function deleteRole(id: number) {
    return axios.post(`/api/rbac/role/delete/${id}`);
}

/**
 * 获取角色已分配的权限ID列表
 */
export function getRolePermissions(roleId: number) {
    return axios.get(`/api/rbac/role/permissions/${roleId}`);
}

/**
 * 为角色分配权限
 */
export function assignRolePermissions(roleId: number, permissionIds: number[]) {
    return axios.post('/api/rbac/role/assignPermissions', { roleId, permissionIds });
}

/**
 * 获取权限树
 */
export function getPermissionTree() {
    return axios.get('/api/rbac/permissions/tree');
}

/**
 * 获取所有权限
 */
export function getAllPermissions() {
    return axios.get('/api/rbac/permissions');
}

/**
 * 获取当前登录用户在「指定团队 + 指定项目」下的权限编码列表
 * @param teamId 团队ID（可选，超级管理员可不传）
 * @param projectId 项目ID（可选，传入时按该项目精确解析项目级权限）
 */
export function getUserPermissions(teamId?: number | string, projectId?: number | string) {
    const params: Record<string, any> = {};
    if (teamId !== undefined && teamId !== null) params.teamId = teamId;
    if (projectId !== undefined && projectId !== null) params.projectId = projectId;
    return axios.get('/api/rbac/user/permissions', {
        params: Object.keys(params).length ? params : undefined
    });
}
