
import {defineStore} from 'pinia';
import {getProjectListByTeamId} from "@/api/MyApi/project";
import useTeamStore from "@/store/modules/team";
import {Project} from "@/types/domain/Project";
import {ProjectListState} from "@/store/modules/nav/type";


export const useDataStore = defineStore('data', {
    state: (): ProjectListState => ({
        data: [],
    }),

    getters: {
        getData(): Project[] {
            return this.data;
        },
        getProjectById: (state) => (id: number | string) => {
            return state.data.find((item) => String(item.id) === String(id));
        },
    },
    actions: {
        async fetchData() {
            const result = await getProjectListByTeamId(useTeamStore().teamId as string);
            this.data = result.data;
        },
    },
    persist: true


})
export default useDataStore;