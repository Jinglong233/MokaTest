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
