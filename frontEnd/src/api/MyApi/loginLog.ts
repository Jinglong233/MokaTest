import axios from "axios";

export function getLoginLogList(params: any) {
    return axios.get('/api/loginLog/list', { params });
}
