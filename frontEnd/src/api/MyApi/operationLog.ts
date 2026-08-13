import axios from "axios";

export function getOperationLogList(params: any) {
    return axios.get('/api/operationLog/list', { params });
}

export function getOperationLogDetail(id: number) {
    return axios.get(`/api/operationLog/${id}`);
}

export function getOperationLogModuleOptions() {
    return axios.get('/api/operationLog/moduleOptions');
}

export function getOperationLogTypeOptions() {
    return axios.get('/api/operationLog/typeOptions');
}

export function getOperationLogTargetTypeOptions() {
    return axios.get('/api/operationLog/targetTypeOptions');
}

export function deleteOperationLog(id: number) {
    return axios.post(`/api/operationLog/delete/${id}`);
}

export function batchDeleteOperationLog(ids: number[]) {
    return axios.post('/api/operationLog/batchDelete', ids);
}

export function clearOperationLog() {
    return axios.post('/api/operationLog/clear');
}

export function exportOperationLog(params: any) {
    return axios.get('/api/operationLog/export', {
        params,
        responseType: 'blob'
    });
}
