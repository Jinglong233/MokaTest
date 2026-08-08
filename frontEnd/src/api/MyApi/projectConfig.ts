import axios from "axios";

// ==================== 项目级统一配置（通知规则 / 字段显隐，差量存储） ====================

export interface ProjectConfigItem {
    id?: number;
    projectId?: number;
    configType: 'NOTIFY_RULE' | 'FIELD_VISIBLE';
    configKey: string;
    configValue: string | null;
}

export function getProjectConfigList(projectId: number) {
    return axios.get('/api/qa/projectConfig/list', {
        params: { projectId }
    });
}

export function saveProjectConfigAll(projectId: number, configs: ProjectConfigItem[]) {
    return axios.post('/api/qa/projectConfig/saveAll', { projectId, configs });
}

export function resetProjectConfig(projectId: number) {
    return axios.post('/api/qa/projectConfig/reset', { projectId });
}
