import axios from 'axios';
import type {RouteRecordNormalized} from 'vue-router';
import {UserState} from '@/store/modules/user/types';
import {UserVO} from "@/types/vo/UserVO";
import {UserQueryDTO} from "@/types/dto/queryDTO/UserQueryDTO";

export interface LoginData {
    username: string;
    password: string;
}

export interface LoginRes {
    token: string;
}

export function login(data: LoginData) {
    return axios.post<LoginRes>('/api/user/login', data);
}

export function logout() {
    return axios.post<LoginRes>('/api/user/logout');
}

export function register(registerData: LoginData) {
    return axios.post<LoginRes>('/api/user/register', registerData);
}

export function getUserInfo() {
    return axios.post<UserState>('/api/user/getInfo');
}

export function getMenuList() {
    return axios.post<RouteRecordNormalized[]>('/api/user/menu');
}

export function updateUserInfo(user: UserState) {
    return axios.post<boolean>('/api/user/updateUserInfo', user);
}

export function updatePassword(data: { oldPassword: string, newPassword: string }) {
    return axios.post<boolean>('/api/user/updatePwd', null, {
        params: {
            oldPwd: data.oldPassword,
            newPwd: data.newPassword
        }
    });
}

export function getUserList() {
    return axios.post<UserVO[]>('/api/user/getUserList');
}

/**
 * 分页获取用户列表
 */
export function getUserListByPage(queryDto:UserQueryDTO){
    return axios.post<UserVO[]>('/api/user/getUserListByPage',queryDto);
}

/**
 * 获取可邀请加入指定团队的用户列表
 */
export function getInviteUserList(teamId: string | number) {
    return axios.get<UserVO[]>('/api/user/getInviteUserList', {
        params: { teamId }
    });
}

/**
 * 超管新建用户
 */
export function adminCreateUser(data: {
    username: string;
    password?: string;
    nickname?: string;
    email?: string;
    phone?: string;
}) {
    return axios.post('/api/user/adminCreateUser', data);
}

/**
 * 超管重置用户密码
 */
export function adminResetPwd(data: { userId: number; newPassword?: string }) {
    return axios.post('/api/user/adminResetPwd', data);
}

/**
 * 超管启用/禁用用户（禁用后无法登录，现有会话立即失效）
 */
export function updateUserStatus(userId: number, status: number) {
    return axios.post('/api/user/updateUserStatus', null, {
        params: { userId, status },
    });
}

/**
 * 上传头像
 */
export function uploadAvatar(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return axios.post<string>('/api/user/uploadAvatar', formData, {
        headers: {
            'Content-Type': 'multipart/form-data',
        },
    });
}

