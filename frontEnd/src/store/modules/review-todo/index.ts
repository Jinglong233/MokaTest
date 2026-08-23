import {defineStore} from 'pinia';
import {getTestCaseReviewMyTodo} from '@/api/MyApi/qa';

interface ReviewTodoState {
    /** 待我评审的评审单数量 */
    count: number;
    loaded: boolean;
}

/**
 * 用例评审待办徽标 store。
 * 菜单「用例评审」标题旁展示 a-badge；提交评审/进入页面后调用 refresh 刷新。
 */
export const useReviewTodoStore = defineStore('reviewTodo', {
    state: (): ReviewTodoState => ({
        count: 0,
        loaded: false,
    }),

    actions: {
        async refresh(force = false) {
            if (this.loaded && !force) return;
            try {
                const res: any = await getTestCaseReviewMyTodo();
                if (res.code === 200) {
                    const list = res.data?.records || res.data || [];
                    this.count = Array.isArray(list) ? list.length : 0;
                    this.loaded = true;
                }
            } catch (e) {
                // 无权限/未登录等场景静默失败，不显示徽标
                this.count = 0;
            }
        },
        clear() {
            this.count = 0;
            this.loaded = false;
        },
    },
});

export default useReviewTodoStore;
