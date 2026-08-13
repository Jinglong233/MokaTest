import axios from "axios";

export function getLoginLogList(params: any) {
    return axios.get('/api/loginLog/list', { params });
}

export function deleteLoginLog(id: number) {
    return axios.post(`/api/loginLog/delete/${id}`);
}

export function batchDeleteLoginLog(ids: number[]) {
    return axios.post('/api/loginLog/batchDelete', ids);
}

export function clearLoginLog() {
    return axios.post('/api/loginLog/clear');
}

export function exportLoginLog(params: any) {
    return axios.get('/api/loginLog/export', {
        params,
        responseType: 'blob'
    });
}
