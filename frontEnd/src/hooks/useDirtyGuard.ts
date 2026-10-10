import {onBeforeRouteLeave} from 'vue-router';
import {onMounted, onUnmounted} from 'vue';
import {Modal} from '@arco-design/web-vue';

/**
 * 未保存内容离开拦截
 *
 * 同时处理：
 * 1. 浏览器关闭/刷新前的 beforeunload 提示（浏览器原生，文案不可自定义）
 * 2. Vue Router 路由离开前的弹窗提示（Arco Modal）
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
            return new Promise<boolean>((resolve) => {
                Modal.confirm({
                    title: '有未保存的修改',
                    content: message,
                    okText: '离开',
                    cancelText: '留在当前页',
                    onOk: () => resolve(true),
                    onCancel: () => resolve(false),
                });
            });
        }
        return true;
    });
}

/**
 * 有未保存修改时弹窗确认，没修改直接执行
 *
 * 用于弹窗关闭、抽屉关闭、tab 切换等场景。
 * 与 useDirtyGuard 分工：useDirtyGuard 处理路由离开 + beforeunload，
 * confirmIfDirty 处理组件内操作（弹窗/抽屉/tab）。
 *
 * @param isDirty 是否有未保存修改
 * @param onConfirm 没有修改或用户确认放弃后执行
 * @param message 确认提示文案
 */
export function confirmIfDirty(
    isDirty: boolean,
    onConfirm: () => void,
    message = '当前有未保存的修改，继续将丢失这些修改，确定离开吗？'
) {
    if (isDirty) {
        Modal.confirm({
            title: '有未保存的修改',
            content: message,
            okText: '放弃修改',
            cancelText: '继续编辑',
            onOk: onConfirm,
        });
    } else {
        onConfirm();
    }
}
