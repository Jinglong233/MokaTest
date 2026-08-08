import type {Router} from 'vue-router';
import {Message} from '@arco-design/web-vue';
import {useTeamStore} from '@/store';
import {getTeamList} from '@/api/MyApi/team';

/**
 * 团队上下文路由守卫
 *
 * 访问需要团队上下文的页面时，若当前未选择团队，则自动拉取团队列表并选中第一个。
 * 若用户没有任何团队，则提示并跳转到团队工作台。
 */
export default function setupTeamGuard(router: Router) {
    router.beforeEach(async (to, from, next) => {
        const teamStore = useTeamStore();

        // 登录页、团队管理、个人中心相关页面不需要自动选团队
        if (to.name === 'login' || to.path.startsWith('/login') || to.path.startsWith('/team') || to.path.startsWith('/user')) {
            next();
            return;
        }

        // 已有团队上下文，直接放行
        if (teamStore.hasTeamSelected) {
            next();
            return;
        }

        // 尝试自动选中第一个团队
        try {
            const res: any = await getTeamList();
            const teamList = res?.data || [];
            if (teamList.length > 0) {
                const firstTeam = teamList[0];
                if (firstTeam.id != null) {
                    teamStore.setTeam(String(firstTeam.id), firstTeam.teamName || '个人团队');
                    next();
                    return;
                }
            }
        } catch (e) {
            // 静默处理，后续会提示用户选择或创建团队
        }

        // 无团队可用，引导到团队工作台
        Message.warning({
            content: '请先选择或创建一个团队',
            duration: 1500,
        });
        next({name: 'TeamWorkspace'});
    });
}
