import type { Router } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import { useProjectStore } from '@/store';

export default function setupProjectGuard(router: Router) {
  router.beforeEach((to, from, next) => {
    const projectStore = useProjectStore();
    if (to.meta?.requiresProject && !projectStore.hasProjectSelected) {
      Message.warning({
        content: '请先创建或选择一个项目',
        duration: 1500,
      });
      next({
        name: 'ProjectInfo',
        query: { noProject: '1' },
      });
      return;
    }
    next();
  });
}
