import {onBeforeRouteLeave} from 'vue-router';
import {onMounted, onUnmounted} from 'vue';

/**
 * 未保存内容离开拦截
 *
 * 同时处理：
 * 1. 浏览器关闭/刷新前的 beforeunload 提示
 * 2. Vue Router 路由离开前的 confirm 提示
 *
 * @param isDirty 返回当前是否有未保存更改
 * @param message 确认提示文案
 */
export function useDirtyGuard(
    isDirty: () => boolean,
    message = '您有未保存的更改，确定要离开吗？'
) {
    const beforeUnloadHandler = (e: BeforeUnloadEvent) => {
        if (isDirty()) {
            e.preventDefault();
            e.returnValue = '';
        }
    };

    onMounted(() => {
        window.addEventListener('beforeunload', beforeUnloadHandler);
    });

    onUnmounted(() => {
        window.removeEventListener('beforeunload', beforeUnloadHandler);
    });

    onBeforeRouteLeave(() => {
        if (isDirty()) {
            return confirm(message);
        }
        return true;
    });
}
