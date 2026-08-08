/**
 * IF 条件不成立时容器步骤标记 SKIPPED，其子步骤不会执行、后端也不会推送它们的调试结果。
 * 这里收集这些「被跳过的后代步骤」id，前端据此把它们的调试状态视为 SKIPPED，
 * 否则步骤行/画布节点会一直显示加载中（等不到结果）。
 *
 * @param stepDebugList 调试结果列表（WS 推送的 DebugStepResult）
 * @param stepsTree     步骤树（含 children 嵌套）
 * @returns 被跳过容器（含嵌套容器）覆盖的所有后代步骤 id 集合（含容器自身 id）
 */
export const collectSkippedDescendantIds = (
    stepDebugList: any[],
    stepsTree: any[]
): Set<string | number> => {
  const ids = new Set<string | number>();
  if (!stepDebugList?.length) return ids;

  // 找出调试结果为 SKIPPED 的容器步骤 id（含 childrenResults 里的嵌套容器）
  const skippedContainerIds = new Set<string | number>();
  const markSkipped = (arr: any[]) => {
    for (const item of arr || []) {
      if (item?.step?.id != null && (item.result?.status || item.status) === 'SKIPPED') {
        skippedContainerIds.add(item.step.id);
      }
      if (item?.childrenResults?.length) markSkipped(item.childrenResults);
    }
  };
  markSkipped(stepDebugList);
  if (!skippedContainerIds.size) return ids;

  // 在步骤树中定位这些容器，收集其全部后代 id
  const collect = (nodes: any[], inside: boolean) => {
    for (const node of nodes || []) {
      const hit = skippedContainerIds.has(node.id);
      if (inside || hit) ids.add(node.id);
      if (node.children?.length) collect(node.children, inside || hit);
    }
  };
  collect(stepsTree || [], false);
  return ids;
};
