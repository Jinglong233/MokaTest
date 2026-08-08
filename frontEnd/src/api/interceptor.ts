import axios from 'axios';
import type {AxiosRequestConfig, AxiosResponse} from 'axios';
import {Message} from '@arco-design/web-vue';
import {useUserStore, useTeamStore, useProjectStore} from '@/store';
import {getToken} from '@/utils/auth';
import router from "@/router";

export interface HttpResponse<T = unknown> {
    status: number;
    msg: string;
    code: number;
    data: T;
}

/**
 * 登录失效处理防抖
 * 避免后端重启/会话过期时，多个并发请求同时触发多次提示和跳转
 */
let lastLogoutTime = 0;
const LOGOUT_DEBOUNCE_MS = 3000; // 3 秒内只处理一次

function handleLogout(responseMsg?: string) {
    const now = Date.now();
    if (now - lastLogoutTime < LOGOUT_DEBOUNCE_MS) {
        return; // 防抖：短时间内已处理过，直接忽略
    }
    lastLogoutTime = now;

    // 先清除登录状态，防止路由守卫认为仍已登录导致跳转失败
    const userStore = useUserStore();
    userStore.logoutCallBack();

    // 已在登录页时，不弹"登录状态失效"提示（例如刷新登录页时 token 过期）
    // router.currentRoute 在非组件作用域也能读取
    if (router.currentRoute.value.name !== 'login') {
        Message.warning({
            content: responseMsg || '登录状态失效，请重新登录',
            duration: 5 * 1000,
        });
    }

    // 登录状态非法，跳转登录页
    router.push('/login').catch((err) => {
        // 忽略导航取消/重复导航等错误，避免控制台报错
        if (err && err.name !== 'NavigationCancelled' && err.name !== 'NavigationDuplicated') {
            console.error('跳转登录页失败', err);
        }
    });
}

if (import.meta.env.VITE_API_BASE_URL) {
    axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL;
}

axios.interceptors.request.use(
    (config: AxiosRequestConfig) => {
        // let each request carry token
        // this example using the JWT token
        // Authorization is a custom headers key
        // please modify it according to the actual situation
        const token = getToken();
        if (token) {
            if (!config.headers) {
                config.headers = {};
            }
            config.headers.Authorization = token;
        }

        // 每次请求携带当前选中的团队ID，供后端 @SaCheckPermission 注解鉴权时定位团队上下文
        const teamStore = useTeamStore();
        const teamId = teamStore.teamId;
        if (teamId) {
            if (!config.headers) {
                config.headers = {};
            }
            config.headers['X-Team-Id'] = String(teamId);
        }

        // 携带当前选中的项目ID，供后端按「团队 + 项目」精确解析项目级权限（不同项目权限清晰区分）
        const projectStore = useProjectStore();
        const projectId = projectStore.projectId;
        if (projectId) {
            if (!config.headers) {
                config.headers = {};
            }
            config.headers['X-Project-Id'] = String(projectId);
        }

        return config;
    },
    (error) => {
        // do something
        return Promise.reject(error);
    }
);


// add response interceptors
axios.interceptors.response.use(
    (response: AxiosResponse<HttpResponse>) => {
        const res = response.data;

        // if the custom code is not 20000, it is judged as an error.
        if ([11001, 11011, 11012, 11013, 11014, 11015, 11016].includes(res.code)) {
            handleLogout('登录状态失效');
            return Promise.reject(res);
        }

        // 403 无权操作：统一提示，不跳转登录页
        if (res.code === 403) {
            Message.warning({
                content: res.msg || '无权执行该操作',
                duration: 5 * 1000,
            });
            return Promise.reject(res);
        }

        if (res.code !== 200) {
            Message.error({
                content: res.msg || 'Error',
                duration: 5 * 1000,
            });
            return Promise.reject(res);
        }




        return res;
    },
    (error) => {
        console.log('err' + error)
        // HTTP 401 也视为登录失效，做防抖处理
        if (error.response && error.response.status === 401) {
            handleLogout('登录状态失效');
            return Promise.reject(error);
        }
        // HTTP 403 无权限
        if (error.response && error.response.status === 403) {
            Message.warning({
                content: error.response.data?.msg || '无权执行该操作',
                duration: 5 * 1000,
            });
            return Promise.reject(error);
        }
        Message.error({
            content: error.msg || 'Request Error',
            duration: 5 * 1000,
        });
        return Promise.reject(error);
    }
);
