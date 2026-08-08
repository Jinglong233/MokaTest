import { DirectiveBinding, watchEffect, WatchStopHandle } from 'vue';
import { usePermissionStore } from '@/store';

/**
 * 权限指令 v-permission
 *
 * <p>支持两种用法：
 *   <li>基于权限编码：\<button v-permission="'qa:bug:create'">创建BUG\</button></li>
 *   <li>基于权限编码数组（任意一个满足即可）：\<button v-permission="['qa:bug:create', 'qa:bug:update']">操作BUG\</button></li>
 *   <li>兼容旧角色用法：\<button v-permission="['admin']">管理员操作\</button>（admin 拥有所有权限）</li>
 *
 * <p>当用户无权限时，元素通过 display:none 隐藏，保留在 DOM 中以便权限变化后恢复显示。
 * 使用 watchEffect 监听权限 store 变化，实现异步权限加载后的自动响应。
 */
function checkPermission(
  el: HTMLElement,
  binding: DirectiveBinding<string | string[]>
): boolean {
  const { value } = binding;
  const permissionStore = usePermissionStore();

  if (!value || (Array.isArray(value) && value.length === 0)) {
    return true;
  }

  const permissionCodes = Array.isArray(value) ? value : [value];
  return permissionCodes.some((code) => permissionStore.hasPermission(code));
}

export default {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const stop = watchEffect(() => {
      el.style.display = checkPermission(el, binding) ? '' : 'none';
    });
    // 将 watcher 停止函数挂载到元素上，便于卸载时清理
    (el as any).__permissionWatcher__ = stop;
  },
  updated(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    // binding 值变化时由 watchEffect 自动重新计算，无需手动处理
    el.style.display = checkPermission(el, binding) ? '' : 'none';
  },
  unmounted(el: HTMLElement) {
    const stop = (el as any).__permissionWatcher__ as WatchStopHandle | undefined;
    if (stop) {
      stop();
      delete (el as any).__permissionWatcher__;
    }
  },
};
