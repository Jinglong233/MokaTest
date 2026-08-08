import {defineStore} from 'pinia';
import {TeamState} from "@/store/modules/team/type";
import {usePermissionStore} from "@/store/modules/permission";
import {useProjectStore} from "@/store/modules/project";

export const useTeamStore = defineStore('team', {
    state: (): TeamState => ({
        teamId: null,
        teamName: null,
    }),

    getters: {
        getTeamId(): number | null {
            return this.teamId;
        },

        getTeamName(): string | null {
            return this.teamName;
        },
        hasTeamSelected(): boolean {
            return !!this.teamId;
        }
    },

    actions: {
        setTeamId(id: string) {
            this.teamId = id ? Number(id) : null;
            // 切换团队必须清空当前项目，避免沿用上一个团队的项目（串团队/串项目）
            useProjectStore().clearProject();
            this.loadTeamPermissions();
        },

        setTeamName(name: string) {
            this.teamName = name;
        },

        setTeam(id: string, name: string): Promise<void> {
            this.teamId = id ? Number(id) : null;
            this.teamName = name;
            // 切换团队必须清空当前项目，避免沿用上一个团队的项目（串团队/串项目）
            useProjectStore().clearProject();
            return this.loadTeamPermissions();
        },

        clearTeam() {
            this.teamId = null;
            this.teamName = null;
            useProjectStore().clearProject();
            const permissionStore = usePermissionStore();
            permissionStore.clearPermissions();
        },

        /**
         * 切换团队后自动刷新权限列表
         */
        loadTeamPermissions(): Promise<void> {
            if (this.teamId) {
                const permissionStore = usePermissionStore();
                return permissionStore.loadPermissions(this.teamId);
            }
            return Promise.resolve();
        }
    },
    persist: true

});

export default useTeamStore;
