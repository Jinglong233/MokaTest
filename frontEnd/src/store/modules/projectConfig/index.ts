import {defineStore} from 'pinia';
import {getProjectConfigList, ProjectConfigItem} from '@/api/MyApi/projectConfig';

interface ProjectConfigState {
    /** 当前已加载配置所属的项目ID */
    projectId: number | null;
    /** 项目配置差量记录（无记录 = 平台默认行为） */
    configs: ProjectConfigItem[];
    loading: boolean;
}

/** 前端业务对象 key → 后端 FIELD_VISIBLE config_key */
const BIZ_KEY_MAP: Record<string, string> = {
    bug: 'bug',
    requirement: 'requirement',
    testCase: 'test_case',
};

/**
 * 项目级统一配置 store（通知规则 / 字段显隐）。
 * 进项目时随 projectStore.reloadProjectPermissions 同款时机一次拉取，各页面消费。
 */
export const useProjectConfigStore = defineStore('projectConfig', {
    state: (): ProjectConfigState => ({
        projectId: null,
        configs: [],
        loading: false,
    }),

    getters: {
        /** 某通知场景的差量规则（解析后的 JSON），无记录返回 null */
        notifyRule(): (eventType: string) => any | null {
            return (eventType: string) => {
                const row = this.configs.find(
                    (c) => c.configType === 'NOTIFY_RULE' && c.configKey === eventType
                );
                if (!row || !row.configValue) {
                    return null;
                }
                try {
                    return JSON.parse(row.configValue);
                } catch {
                    return null;
                }
            };
        },

        /** 某业务对象被隐藏的字段 key 列表（无记录 = 全部显示） */
        hiddenFields(): (biz: string) => string[] {
            return (biz: string) => {
                const configKey = BIZ_KEY_MAP[biz] || biz;
                const row = this.configs.find(
                    (c) => c.configType === 'FIELD_VISIBLE' && c.configKey === configKey
                );
                if (!row || !row.configValue) {
                    return [];
                }
                try {
                    const parsed = JSON.parse(row.configValue);
                    return Array.isArray(parsed?.hiddenFields) ? parsed.hiddenFields : [];
                } catch {
                    return [];
                }
            };
        },

        /** 字段是否可见（展示层显隐，默认可见） */
        isFieldVisible(): (biz: string, fieldKey: string) => boolean {
            return (biz: string, fieldKey: string) => {
                const configKey = BIZ_KEY_MAP[biz] || biz;
                const row = this.configs.find(
                    (c) => c.configType === 'FIELD_VISIBLE' && c.configKey === configKey
                );
                if (!row || !row.configValue) {
                    return true;
                }
                try {
                    const parsed = JSON.parse(row.configValue);
                    const hidden: string[] = Array.isArray(parsed?.hiddenFields) ? parsed.hiddenFields : [];
                    return !hidden.includes(fieldKey);
                } catch {
                    return true;
                }
            };
        },
    },

    actions: {
        /** 拉取项目全部配置差量（同项目已加载则跳过，force 可强制刷新） */
        async load(projectId: number | null | undefined, force = false) {
            if (!projectId) {
                this.clear();
                return;
            }
            if (!force && this.projectId === projectId) {
                return;
            }
            this.loading = true;
            try {
                const res: any = await getProjectConfigList(projectId);
                this.configs = (res?.data as ProjectConfigItem[]) || [];
                this.projectId = projectId;
            } catch {
                // 拉取失败保持默认行为（全部显示 / 全部通知），不阻塞页面
                this.configs = [];
                this.projectId = projectId;
            } finally {
                this.loading = false;
            }
        },

        clear() {
            this.projectId = null;
            this.configs = [];
        },
    },
});

export default useProjectConfigStore;
