import {defineStore} from 'pinia';
import {ProjectState} from './type';
import {usePermissionStore} from '@/store/modules/permission';
import {useProjectConfigStore} from '@/store/modules/projectConfig';
import {useTeamStore} from '@/store/modules/team';

export const useProjectStore = defineStore('project', {
    state: (): ProjectState => ({
        projectId: null,
        projectName: null,
    }),

    getters: {
        getProjectId(): number | null {
            return this.projectId;
        },

        getProjectName(): string | null {
            return this.projectName;
        },
        hasProjectSelected(): boolean {
            return !!this.projectId;
        }
    },

    actions: {
        setProjectId(id: number) {
            this.projectId = id;
            this.reloadProjectPermissions();
        },

        setProjectName(name: string) {
            this.projectName = name;
        },

        setProject(id: number | undefined, name: string | undefined) {
            this.projectId = id ?? null;
            this.projectName = name ?? null;
            this.reloadProjectPermissions();
        },

        clearProject() {
            this.projectId = null;
            this.projectName = null;
        },

        /**
         * 切换项目后，按「当前团队 + 该项目」重新加载权限，
         * 避免沿用上一个项目的权限（串项目）。权限 store 以 team+project 为缓存键，未变化时自动跳过。
         * 同时重拉项目级统一配置（通知规则 / 字段显隐）。
         */
        reloadProjectPermissions(): Promise<void> {
            const teamStore = useTeamStore();
            const projectConfigStore = useProjectConfigStore();
            projectConfigStore.load(this.projectId);
            if (!teamStore.teamId) {
                return Promise.resolve();
            }
            const permissionStore = usePermissionStore();
            return permissionStore.loadPermissions(
                teamStore.teamId,
                this.projectId ?? undefined
            );
        }
    },
    persist: true

});

export default useProjectStore;
