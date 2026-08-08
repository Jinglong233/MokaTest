import { ref } from 'vue';

/**
 * 数据加载状态管理：区分「加载中 / 加载失败 / 空数据」。
 *
 * 背景：平台大量页面在请求失败时静默 catch，用户无法区分
 * 「真的没有数据」和「加载失败」。统一约定：
 * - 请求必须包在 track() 里
 * - loadError 为 true 时渲染 <LoadError @retry="重新加载" />
 * - 空状态（暂无数据）只能在「请求成功且返回为空」时展示
 */
export default function useLoadState() {
  const loading = ref(false);
  const loadError = ref(false);

  /** 包裹一次请求；失败时置 loadError 并返回 null，成功返回响应 */
  async function track<T>(promise: Promise<T>): Promise<T | null> {
    loading.value = true;
    loadError.value = false;
    try {
      return await promise;
    } catch (e) {
      loadError.value = true;
      console.error(e);
      return null;
    } finally {
      loading.value = false;
    }
  }

  /** 局部重试时手动复位（不经过 track 的场景） */
  function reset() {
    loadError.value = false;
    loading.value = false;
  }

  return { loading, loadError, track, reset };
}
