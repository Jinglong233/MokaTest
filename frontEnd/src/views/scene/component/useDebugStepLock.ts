import {computed, unref, type Ref} from 'vue'

/**
 * 场景调试期间的步骤编辑锁定。
 *
 * 规则（与后端 PlaywrightDebugSession 的热加载/写拦截配套）：
 * - 未调试：不锁定；
 * - 运行中（含 API 场景调试全程）：所有步骤锁定，批量操作禁用；
 * - 手动暂停：仅「未执行」步骤可修改/删除/禁用/勾选；
 * - 失败挂起：「未执行」步骤 + 失败步骤本身可改（修复失败步骤再重试是核心路径）；
 *
 * 「已执行」= stepDebugList（含 childrenResults 递归）中存在该步骤的执行结果。
 * 暂停期间修改未执行步骤后，继续执行时后端会热加载最新步骤；
 * 已执行步骤是续跑锚点（lastExecutedStepId），因此被锁定。
 */
export function useDebugStepLock(options: {
  isDebugging: Ref<boolean>
  debugStatus: Ref<string>
  stepDebugList: Ref<any[]>
  pausedFailureStepId?: Ref<number | null>
  isApiScene?: Ref<boolean>
}) {
  const isApiScene = options.isApiScene ?? computed(() => false)
  const pausedFailureStepId = options.pausedFailureStepId ?? computed(() => null)

  /**
   * 调试编辑模式：
   * none    未调试，不锁定
   * running 调试运行中，全部锁定
   * paused  手动暂停/失败挂起，仅未执行步骤（及失败步骤）可改
   */
  const debugEditMode = computed<'none' | 'running' | 'paused'>(() => {
    if (!unref(options.isDebugging)) return 'none'
    // API 场景调试不支持暂停，全程按运行中处理
    if (unref(isApiScene)) return 'running'
    const status = unref(options.debugStatus)
    if (status === '暂停' || status === '失败挂起') return 'paused'
    return 'running'
  })

  /** 运行中：禁止一切步骤修改（含批量操作/拖拽/新增） */
  const mutationsLocked = computed(() => debugEditMode.value === 'running')

  /** 已执行步骤 id 集合（递归 childrenResults 扁平化） */
  const executedStepIds = computed<Set<number>>(() => {
    const ids = new Set<number>()
    const walk = (list: any[]) => {
      if (!Array.isArray(list)) return
      for (const item of list) {
        const id = item?.step?.id
        if (id != null) ids.add(Number(id))
        if (Array.isArray(item?.childrenResults)) walk(item.childrenResults)
      }
    }
    walk(unref(options.stepDebugList) || [])
    return ids
  })

  /**
   * 单个步骤是否锁定（不可修改/删除/禁用/勾选）。
   * 失败挂起时失败步骤本身放行，便于修复后重试。
   */
  const isStepLocked = (stepId: number | string | null | undefined) => {
    if (stepId == null) return false
    if (debugEditMode.value === 'none') return false
    if (debugEditMode.value === 'running') return true
    const id = Number(stepId)
    const failureId = unref(pausedFailureStepId)
    if (failureId != null && id === Number(failureId)) return false
    return executedStepIds.value.has(id)
  }

  return {debugEditMode, mutationsLocked, executedStepIds, isStepLocked}
}
