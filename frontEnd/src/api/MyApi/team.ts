import axios from "axios";
import {Team} from "@/types/domain/Team";
import {AddTeamMemberDTO} from "@/types/dto/addDTO/AddTeamMemberDTO";

export function getTeamList() {
    return axios.get<Team[]>('/api/team/getTeamList');
}


export function createTeam(team: Team) {
    return axios.post('/api/team/createTeam', team);
}

export function updateTeam(team: Team) {
    return axios.post('/api/team/updateTeam', team);
}

/**
 * 解散团队（逻辑删除）
 */
export function deleteTeam(teamId: string | number) {
    return axios.post(`/api/team/deleteTeam/${teamId}`);
}

/**
 * 添加团队成员
 */
export function addTeamMember(data: AddTeamMemberDTO) {
    return axios.post('/api/team/addTeamMember', data);
}

/**
 * 获取团队成员列表
 */
export function getTeamMembers(teamId: string | number) {
    return axios.get(`/api/team/members/${teamId}`);
}

/**
 * 修改成员角色
 */
export function updateTeamMemberRole(data: { teamId: string | number; userId: string | number; roleId: number }) {
    return axios.post('/api/team/member/updateRole', data);
}

/**
 * 移除团队成员
 */
export function removeTeamMember(data: { teamId: string | number; userId: string | number }) {
    return axios.post('/api/team/member/remove', data);
}

/**
 * 启用/禁用团队成员
 */
export function updateTeamMemberStatus(data: { teamId: string | number; userId: string | number; status: number }) {
    return axios.post('/api/team/member/updateStatus', data);
}