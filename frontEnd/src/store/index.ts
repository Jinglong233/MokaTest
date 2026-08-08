import {createPinia} from 'pinia';
import useAppStore from './modules/app';
import useUserStore from './modules/user';
import useTabBarStore from './modules/tab-bar';
import useProjectStore from './modules/project';
import useTeamStore from './modules/team';
import usePermissionStore from './modules/permission';
import useWorkspaceStore from './modules/workspace';
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate';

const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);

export {useAppStore, useUserStore, useTabBarStore, useProjectStore, useTeamStore, usePermissionStore, useWorkspaceStore};
export default pinia;
