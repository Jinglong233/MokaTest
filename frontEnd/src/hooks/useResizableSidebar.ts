import { computed, ref } from 'vue';

export interface ResizableSidebarOptions {
  defaultWidth?: number;
  minWidth?: number;
  maxWidth?: number;
  // 宽度低于该值时松开鼠标即折叠
  collapseThreshold?: number;
}

/**
 * 左侧目录树侧栏：拖拽伸缩 + 拖到极窄自动折叠 + 折叠态点击展开
 * （场景库 scene/index.vue 与元素库 element/index.vue 共用）
 */
export function useResizableSidebar(options: ResizableSidebarOptions = {}) {
  const DEFAULT_WIDTH = options.defaultWidth ?? 240;
  const MIN_WIDTH = options.minWidth ?? 200;
  const MAX_WIDTH = options.maxWidth ?? 480;
  const COLLAPSE_THRESHOLD = options.collapseThreshold ?? 20;

  const sidebarWidth = ref(DEFAULT_WIDTH);
  const isSidebarCollapsed = ref(false);
  const lastSidebarWidth = ref(DEFAULT_WIDTH);

  const sideColStyle = computed(() => ({
    width: isSidebarCollapsed.value ? '0px' : `${sidebarWidth.value}px`,
    overflow: 'hidden',
  }));

  const collapseSidebar = () => {
    if (!isSidebarCollapsed.value) {
      lastSidebarWidth.value = Math.max(sidebarWidth.value, MIN_WIDTH);
    }
    isSidebarCollapsed.value = true;
    sidebarWidth.value = 0;
  };

  const expandSidebar = () => {
    isSidebarCollapsed.value = false;
    const expandedWidth = Math.round((lastSidebarWidth.value || DEFAULT_WIDTH) * 1.25);
    sidebarWidth.value = Math.min(expandedWidth, MAX_WIDTH);
  };

  const startResizeSidebar = (e: MouseEvent) => {
    const startX = e.clientX;
    const wasCollapsed = isSidebarCollapsed.value;
    const startWidth = wasCollapsed ? 0 : sidebarWidth.value;
    let moved = false;

    const onMove = (ev: MouseEvent) => {
      if (Math.abs(ev.clientX - startX) > 3) moved = true;
      if (wasCollapsed) {
        isSidebarCollapsed.value = false;
      }
      const newWidth = startWidth + ev.clientX - startX;
      sidebarWidth.value = Math.min(Math.max(newWidth, 0), MAX_WIDTH);
    };

    const onUp = () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      document.body.style.userSelect = '';

      if (!moved && wasCollapsed) {
        expandSidebar();
        return;
      }

      if (sidebarWidth.value <= COLLAPSE_THRESHOLD) {
        collapseSidebar();
      } else if (sidebarWidth.value < MIN_WIDTH) {
        sidebarWidth.value = MIN_WIDTH;
      }
    };

    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    document.body.style.userSelect = 'none';
  };

  return {
    sidebarWidth,
    isSidebarCollapsed,
    sideColStyle,
    collapseSidebar,
    expandSidebar,
    startResizeSidebar,
  };
}
