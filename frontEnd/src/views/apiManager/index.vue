<template>
  <div class="api-manager-page" :class="{ 'focus-mode': focusMode }">
    <div v-if="!focusMode" class="page-header">
      <Breadcrumb :items="['menu.interfaceTest', 'menu.interfaceTest.apiList']" />
      <a-tooltip content="进入聚焦模式">
        <a-button type="text" size="small" class="focus-btn" @click="focusMode = true">
          <template #icon>
            <icon-fullscreen />
          </template>
        </a-button>
      </a-tooltip>
    </div>
    <a-tooltip v-else content="退出聚焦模式">
      <a-button type="primary" size="small" class="focus-exit-btn" @click="focusMode = false">
        <template #icon>
          <icon-fullscreen-exit />
        </template>
      </a-button>
    </a-tooltip>
    <div class="api-manager-row">
      <div class="api-manager-col api-manager-side-col" :style="sideColStyle">
        <ApiListSide
            ref="apiFolderAndInterfaceRef"
            @add="handleAddTab"
            @updateInterface="handleUpdateInterface"
            @deleteInterfaceTab="handleCloseTab"
        />
      </div>
      <div
          class="api-manager-resizer"
          :class="{ 'is-collapsed': isSidebarCollapsed }"
          @mousedown="startResizeSidebar"
      >
        <div v-if="isSidebarCollapsed" class="sidebar-expand-btn">
          <icon-right class="sidebar-expand-icon" />
        </div>
        <span v-else class="resizer-line" />
      </div>
      <div class="api-manager-col api-manager-content-col">
        <a-spin class="api-manager-spin" :loading="loading" tip="正在调试...">
          <a-card class="api-manager-card">
            <ApiTabs
                v-if="tabList.length > 0"
                ref="apiTabsRef"
                v-model:model-value="activeKey"
                v-model:tabs="tabList"
                @add="handleAddTab"
                @close="handleCloseTab"
            >
              <template
                  v-for="tab in tabList"
                  :key="tab.key"
                  #[`tab-${tab.key}`]="{ onChange }"
              >
                <div class="api-debug-layout">
                  <!-- 请求表单：始终挂载，占据剩余空间 -->
                  <div class="debug-form-region">
                    <ApiDetailPanel
                        v-model="tabDataMap[tab.key]"
                        @change="(hasChange) => onChange(hasChange)"
                        @save="handleSaveTab(tab)"
                        @save-and-debug="(data) => saveAndDebugApi(tab, data)"
                        @save-as-case="(data) => handleSaveAsCase(tab, data)"
                    />
                  </div>

                  <!-- 响应区始终显示 -->
                  <!-- 折叠态：底部长条（本身可拖拽伸缩，也可点击展开） -->
                  <div
                      v-if="responseCollapsed"
                      class="response-collapsed-bar"
                      @mousedown="startResize"
                  >
                    <span class="bar-title">
                      <icon-up />
                      返回响应
                    </span>
                    <div class="bar-meta" v-if="currentDebugResult">
                      <a-tag :color="resultStatusColor" size="small">
                        {{ resultStatusCode != null ? resultStatusCode : (resultIsError ? '错误' : '-') }}
                      </a-tag>
                      <span v-if="resultTimeMs != null" class="meta-item">{{ resultTimeMs }} ms</span>
                      <span v-if="resultSize" class="meta-item">{{ resultSize }}</span>
                    </div>
                    <span v-else class="bar-meta" style="color: var(--color-text-3);">暂无结果</span>
                  </div>

                  <!-- 展开态：可拖拽分隔条 + 响应面板 -->
                  <template v-else>
                    <div class="response-resizer" @mousedown="startResize">
                      <span class="resizer-grip" />
                      <a-tooltip content="收起响应">
                        <icon-down
                            class="resizer-collapse"
                            @mousedown.stop
                            @click.stop="collapseResponse"
                        />
                      </a-tooltip>
                    </div>
                    <div class="debug-result-region" :style="{ height: responseHeight + 'px' }">
                      <ApiDebugResult :debug-result="currentDebugResult" :api-type="tabDataMap[activeKey]?.apiType"/>
                    </div>
                  </template>
                </div>
              </template>
            </ApiTabs>

            <!-- 无编辑接口时的空状态 -->
            <div v-else class="api-empty-state">
              <div class="empty-cards">
                <EmptyActionCard
                    v-if="hasApiCreatePermission"
                    title="新建 HTTP 接口"
                    :icon="IconCode"
                    @click="handleAddTab(ApiType.HTTP)"
                />
                <EmptyActionCard
                    v-if="hasApiCreatePermission"
                    title="新建 SQL 接口"
                    :icon="IconStorage"
                    @click="handleAddTab(ApiType.SQL)"
                />
                <EmptyActionCard
                    v-if="hasApiCreatePermission"
                    title="导入 Swagger 接口"
                    swagger
                    @click="handleOpenImportModal"
                />
                <div v-if="!hasApiCreatePermission" class="empty-tip">
                  暂无接口编辑权限，请联系管理员开通
                </div>
              </div>
            </div>
          </a-card>
        </a-spin>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'ApiList' };
</script>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue';
import ApiListSide from '@/views/apiManager/component/ApiListSide.vue';
import ApiTabs from '@/views/apiManager/component/ApiTabs.vue';
import ApiDetailPanel from '@/views/apiManager/component/ApiDetailPanel.vue';
import {ApiRequest} from '@/types/domain/api/ApiRequest';
import {ApiNodeType} from '@/types/domain/api/apiEnum/ApiNodeType';
import {ApiType} from '@/types/domain/api/apiEnum/ApiType';
import {AddApiInterfaceDTO} from '@/types/domain/api/dto/AddApiInterfaceDTO';
import {useProjectStore} from '@/store';
import useTeamStore from '@/store/modules/team';
import {getEnvList} from '@/api/MyApi/environment';
import {Environment} from '@/types/domain/api/Environment';
import {debug, getApiById} from '@/api/MyApi/apiInterface';
import {saveApi, saveCase} from '../../api/MyApi/apiInterface';
import {Message} from '@arco-design/web-vue';
import {IconRight, IconCode, IconStorage} from '@arco-design/web-vue/es/icon';
import ApiDebugResult from "@/views/apiManager/component/ApiDebugResult.vue";
import Breadcrumb from '@/components/breadcrumb/index.vue';
import EmptyActionCard from '@/components/empty-action-card/index.vue';
import useLoading from "@/hooks/loading";
import usePermission from '@/hooks/permission';

interface TabItem extends ApiRequest {
  key: string;
  apiName: '未命名接口';
  unsavedChanges?: boolean;
}


const {loading, setLoading} = useLoading(false);
const teamStore = useTeamStore();
const permission = usePermission();
const hasEnvViewPermission = computed(() => permission.hasPermission('auto:env:view'));
const hasApiCreatePermission = computed(() => permission.hasPermission('auto:api:create'));
const apiTabsRef = ref();
const envList = ref<Environment[]>([]);
const focusMode = ref(false);

const tabDebugResultMap = reactive<Record<string, any>>({});
const currentDebugResult = computed(() => tabDebugResultMap[activeKey.value]);

// ===== 响应面板：可拖拽 + 折叠为底部长条 =====
const RESPONSE_DEFAULT_HEIGHT = 320; // 默认展开高度
const RESPONSE_MIN_HEIGHT = 120;     // 最小展开高度
const FORM_MIN_HEIGHT = 140;         // 上方表单保留的最小高度
const COLLAPSE_THRESHOLD = 80;       // 拖到底部小于此高度即折叠

const responseCollapsed = ref(false);
const responseHeight = ref(RESPONSE_DEFAULT_HEIGHT);
const lastResponseHeight = ref(RESPONSE_DEFAULT_HEIGHT);

// 折叠长条上展示的状态码/耗时/大小
const resultIsError = computed(() => currentDebugResult.value?.status === 'error');
const resultStatusCode = computed(() => currentDebugResult.value?.statusCode);
const resultStatusColor = computed(() => {
  const c = resultStatusCode.value;
  if (c == null) return resultIsError.value ? 'red' : 'gray';
  if (c >= 200 && c < 300) return 'green';
  if (c >= 300 && c < 400) return 'orange';
  if (c >= 400) return 'red';
  return 'gray';
});
const resultTimeMs = computed(() => currentDebugResult.value?.responseTimeMs);
const resultSize = computed(() => {
  const b = currentDebugResult.value?.responseBytes;
  if (b == null) return '';
  if (b < 1024) return b + ' B';
  if (b < 1024 * 1024) return (b / 1024).toFixed(2) + ' KB';
  return (b / 1024 / 1024).toFixed(2) + ' MB';
});

// 折叠 / 展开
const collapseResponse = () => {
  if (responseHeight.value >= RESPONSE_MIN_HEIGHT) {
    lastResponseHeight.value = responseHeight.value;
  }
  responseCollapsed.value = true;
};
const expandResponse = () => {
  responseCollapsed.value = false;
  responseHeight.value = lastResponseHeight.value || RESPONSE_DEFAULT_HEIGHT;
};

// 拖拽分隔条/折叠条调整响应区高度：
// - 展开态向下拖到底部自动折叠；折叠态向上拖动直接展开伸缩
// - 折叠条上纯点击（未拖动）则展开到上次高度
const startResize = (e: MouseEvent) => {
  const el = e.currentTarget as HTMLElement;
  const container = el.parentElement as HTMLElement;
  if (!container) return;
  const rect = container.getBoundingClientRect();
  const startY = e.clientY;
  let moved = false;
  let rafId = 0;

  const applyMove = (ev: MouseEvent) => {
    if (Math.abs(ev.clientY - startY) > 3) moved = true;
    const newHeight = rect.bottom - ev.clientY;
    if (newHeight < COLLAPSE_THRESHOLD) {
      // 拖到接近底部：仅视觉折叠，不覆盖记忆高度
      responseCollapsed.value = true;
      return;
    }
    // 高度达标：展开并跟随鼠标伸缩
    if (responseCollapsed.value) responseCollapsed.value = false;
    const maxHeight = rect.height - FORM_MIN_HEIGHT;
    const clamped = Math.min(Math.max(newHeight, RESPONSE_MIN_HEIGHT), maxHeight);
    responseHeight.value = clamped;
    lastResponseHeight.value = clamped;
  };

  // rAF 节流：mousemove 触发频率可能高于屏幕刷新率，合并到每帧最多一次更新，避免表格反复重排
  const onMove = (ev: MouseEvent) => {
    if (rafId) return;
    rafId = requestAnimationFrame(() => {
      rafId = 0;
      applyMove(ev);
    });
  };
  const cleanup = () => {
    if (rafId) {
      cancelAnimationFrame(rafId);
      rafId = 0;
    }
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    document.body.style.userSelect = '';
  };
  const onUp = () => {
    // 折叠条上的纯点击（未拖动）→ 展开到上次高度
    if (!moved && responseCollapsed.value) {
      expandResponse();
    }
    cleanup();
  };
  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
  document.body.style.userSelect = 'none';
};

// ===== 左侧目录树：可拖拽伸缩 + 拖拽隐藏 =====
const SIDEBAR_DEFAULT_WIDTH = 240;
const SIDEBAR_MIN_WIDTH = 200;
const SIDEBAR_MAX_WIDTH = 480;
const SIDEBAR_COLLAPSE_THRESHOLD = 20; // 拖到接近左边缘才收起

const sidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);
const isSidebarCollapsed = ref(false);
const lastSidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);

const sideColStyle = computed(() => ({
  width: isSidebarCollapsed.value ? '0px' : `${sidebarWidth.value}px`,
  overflow: 'hidden',
}));

const collapseSidebar = () => {
  if (!isSidebarCollapsed.value) {
    lastSidebarWidth.value = Math.max(sidebarWidth.value, SIDEBAR_MIN_WIDTH);
  }
  isSidebarCollapsed.value = true;
  sidebarWidth.value = 0;
};

const expandSidebar = () => {
  isSidebarCollapsed.value = false;
  // 点击展开时宽度比上次记忆值宽 1/4，最高不超过最大值
  const expandedWidth = Math.round((lastSidebarWidth.value || SIDEBAR_DEFAULT_WIDTH) * 1.25);
  sidebarWidth.value = Math.min(expandedWidth, SIDEBAR_MAX_WIDTH);
};

const startResizeSidebar = (e: MouseEvent) => {
  const startX = e.clientX;
  const wasCollapsed = isSidebarCollapsed.value;
  const startWidth = wasCollapsed ? 0 : sidebarWidth.value;
  let moved = false;

  const onMove = (ev: MouseEvent) => {
    if (Math.abs(ev.clientX - startX) > 3) moved = true;
    if (wasCollapsed) {
      // 从收起态开始拖动时立即展开，并跟随鼠标
      isSidebarCollapsed.value = false;
    }
    const newWidth = startWidth + ev.clientX - startX;
    sidebarWidth.value = Math.min(Math.max(newWidth, 0), SIDEBAR_MAX_WIDTH);
  };

  const onUp = () => {
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    document.body.style.userSelect = '';

    if (!moved && wasCollapsed) {
      // 收起状态下点击分隔条 -> 展开
      expandSidebar();
      return;
    }

    if (sidebarWidth.value <= SIDEBAR_COLLAPSE_THRESHOLD) {
      // 拖到接近左边缘 -> 收起
      collapseSidebar();
    } else if (sidebarWidth.value < SIDEBAR_MIN_WIDTH) {
      // 停在最小宽度附近但未到底 -> 恢复到最小宽度
      sidebarWidth.value = SIDEBAR_MIN_WIDTH;
    }
  };

  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
  document.body.style.userSelect = 'none';
};

onMounted(async () => {
  // 获取全局环境列表
  if (hasEnvViewPermission.value) {
    try {
      const {data} = await getEnvList(teamStore.teamId);
      envList.value = data || [];
    } catch (e) {
      envList.value = [];
    }
  }
});
const apiFolderAndInterfaceRef = ref();

const activeKey = ref<string>('');
const tabList = ref<TabItem[]>([]);
const tabDataMap = reactive<Record<string, AddApiInterfaceDTO>>({});

// 新响应到达（或切换标签）时自动展开（放在 activeKey 之后，避免 TDZ）
watch(currentDebugResult, (val, old) => {
  if (val && val !== old) {
    responseCollapsed.value = false;
    if (responseHeight.value < RESPONSE_MIN_HEIGHT) {
      responseHeight.value = RESPONSE_DEFAULT_HEIGHT;
    }
  }
});

// 添加新标签页
const handleAddTab = (apiType: ApiType = ApiType.HTTP) => {
  const newApi = new AddApiInterfaceDTO(ApiNodeType.INTERFACE);
  newApi.apiType = apiType;
  if (apiType === ApiType.SQL) {
    newApi.apiName = 'SQL查询';
    newApi.requestMethod = undefined as any;
    newApi.requestPath = undefined;
    newApi.requestHeader = undefined;
    newApi.cookies = undefined;
    newApi.query = undefined;
    newApi.body = undefined;
    newApi.envInfo = undefined;
    newApi.mockResponse = undefined;
    newApi.sqlConfig = { sql: '', dbConnectionName: undefined, dbConfig: undefined, timeout: 30, maxRows: 1000, params: [], sqlExtractions: [], sqlAssertions: [] };
  }
  const key =
      'temporary_' +
      Date.now().toString(36) +
      Math.random().toString(36).substr(2, 3);
  const newTab: TabItem = {
    key: key,
    unsavedChanges: true,
  };

  tabList.value.push(newTab);
  tabDataMap[key] = JSON.parse(JSON.stringify(newApi));
  activeKey.value = key;

  console.log('添加标签页:', tabList.value);
  console.log('tabDataMap:', tabDataMap);
  console.log('activeKey.value', activeKey.value);
};

// 打开 Swagger 导入弹窗
const handleOpenImportModal = () => {
  apiFolderAndInterfaceRef.value?.openImportModal();
};

const handleUpdateInterface = async (id: number) => {
  // 获取该接口的详细信息
  const {data} = await getApiById(id);
  const idStr = String(id);
  // 这里需要判断改接口是否已经打开
  if (tabList.value.some((item) => item.key === idStr)) {
    // 如果已经打开那就只需处理activeKey
    activeKey.value = idStr;
    // 同步左侧树状列表选中状态
    apiFolderAndInterfaceRef.value?.setSelectedApi(id);
    return;
  }

  const key = String(data.id);
  const tab: TabItem = {
    key: key,
    apiName: data.apiName,
    unsavedChanges: false,
  };
  tabList.value.push(tab);
  tabDataMap[key] = JSON.parse(JSON.stringify(data));
  if (tabList.value.length > 0) {
    activeKey.value = key;
  }

};

// 保存标签页数据
const handleSaveTab = async (tab: TabItem) => {
  const key = tab.key;
  const savedData = tabDataMap[key];
  savedData.projectId = useProjectStore().getProjectId ?? undefined;
  savedData.teamId = useTeamStore().getTeamId ?? undefined;
  // 已有接口保留其原父目录，避免保存时被当前选中目录覆盖（如复制后保存会移动接口）；
  // 仅新建接口（parentId 为空/0）时落到当前选中目录。
  savedData.parentId =
      (savedData.parentId == null || savedData.parentId === 0)
          ? (apiFolderAndInterfaceRef.value.getCurrentFolder().id ?? 0)
          : savedData.parentId;
  const res = await saveApi(savedData);
  if (res.code == 200) {
    Message.success({
      content: '保存成功',
      duration: 2000,
    });
    // 获取接口的详细信息
    const {data} = await getApiById(res.data);

    // 将这个新获取的添加进去，且tabList中的activeKey也需要替换为当前的这个id
    const savedIdStr = String(data.id);
    const saveNewTab: TabItem = {
      key: savedIdStr,
      apiName: data.apiName,
      unsavedChanges: false,
    };

    // 删除tabList中item的key数据
    tabList.value = tabList.value.filter((item) => item.key !== key);
    tabList.value.push(saveNewTab);
    // 删除临时的数据
    delete tabDataMap[key];
    tabDataMap[savedIdStr] = JSON.parse(JSON.stringify(data));
    if (tabDebugResultMap[key]) {
      tabDebugResultMap[savedIdStr] = tabDebugResultMap[key];
      delete tabDebugResultMap[key];
    }
    activeKey.value = savedIdStr;
    await apiFolderAndInterfaceRef.value.reloadList();
    // 同步左侧树状列表的选中状态
    apiFolderAndInterfaceRef.value.setSelectedApi(Number(savedIdStr));
  }
};

/**
 * 保存并调试
 * @param tab
 * @param formData ApiDebugForm 传递的最新表单数据
 */
const saveAndDebugApi = async (tab: TabItem, formData?: AddApiInterfaceDTO) => {
  const key = tab.key;
  // 优先使用 ApiDebugForm 传递的最新数据，否则回退到 tabDataMap
  const savedData = formData || tabDataMap[key];
  savedData.projectId = useProjectStore().getProjectId ?? undefined;
  savedData.teamId = useTeamStore().getTeamId ?? undefined;
  savedData.parentId = (savedData.parentId == null || savedData.parentId === 0) ? (apiFolderAndInterfaceRef.value.getCurrentFolder().id ?? 0) : savedData.parentId;

  // 先调用保存接口
  const resSave = await saveApi(savedData);
  if (resSave.code != 200) {
    return;
  }
  // 获取接口的详细信息
  const {data} = await getApiById(resSave.data);

  // 将这个新获取的添加进去，且tabList中的activeKey也需要替换为当前的这个id
  const savedIdStr = String(data.id);
  const saveNewTab: TabItem = {
    key: savedIdStr,
    apiName: data.apiName,
    unsavedChanges: false,
  };

  // 删除tabList中item的key数据
  tabList.value = tabList.value.filter((item) => item.key !== key);
  tabList.value.push(saveNewTab);
  // 删除临时的数据
  delete tabDataMap[key];
  tabDataMap[savedIdStr] = JSON.parse(JSON.stringify(data));
  activeKey.value = savedIdStr;
  await apiFolderAndInterfaceRef.value.reloadList();

  // 重新获取接口列表树
  await apiFolderAndInterfaceRef.value.reloadList();
  // 同步左侧树状列表的选中状态
  apiFolderAndInterfaceRef.value.setSelectedApi(Number(savedIdStr));

  setLoading(true);
  try {
    const res = await debug(resSave.data);
    if (res.code == 200) {
      Message.success({
        content: '调试成功',
        duration: 2000,
      });
      tabDebugResultMap[activeKey.value] = res.data;
    }
    setLoading(false);
  }catch (e){
    setLoading(false);
  }
};

/**
 * 保存为用例
 * @param data 从 ApiDebugForm 传过来的用例数据（已包含用户填写的用例名称）
 */
const handleSaveAsCase = async (tab: TabItem, data: AddApiInterfaceDTO) => {
  // 确保有来源接口ID
  if (!data.sourceDratId) {
    Message.error({ content: '请先保存接口，再保存为用例', duration: 2000 });
    return;
  }
  // 深拷贝数据，避免修改原接口数据
  const caseData = JSON.parse(JSON.stringify(data));
  // 清空id，作为新记录插入
  caseData.id = undefined;
  // 设置项目ID和团队ID
  caseData.projectId = useProjectStore().getProjectId ?? undefined;
  caseData.teamId = useTeamStore().getTeamId ?? undefined;
  const res = await saveCase(caseData);
  if (res.code == 200) {
    Message.success({ content: '保存用例成功', duration: 2000 });
  } else {
    Message.error({ content: res.msg || '保存用例失败', duration: 2000 });
  }
};

// 关闭标签页时清理数据
const handleCloseTab = (key: string | number) => {
  const keyStr = String(key);
  delete tabDataMap[keyStr];
  delete tabDebugResultMap[keyStr];
  // 从tabList中删除
  tabList.value = tabList.value.filter((item) => item.key !== keyStr);
  // 判断activeKey 是不是 当前的这个key
  if (activeKey.value === keyStr) {
    if (tabList.value.length > 0) {
      // 切换到第一个 tab，同步高亮
      activeKey.value = tabList.value[0].key;
      apiFolderAndInterfaceRef.value.setSelectedApi(Number(tabList.value[0].key));
    } else {
      // 全部关闭，取消高亮
      activeKey.value = '';
      apiFolderAndInterfaceRef.value.setSelectedApi(null);
    }
  }
};


// 监听 activeKey 变化，同步左侧树高亮
watch(() => activeKey.value, (key) => {
  if (key && !key.startsWith('temporary_')) {
    apiFolderAndInterfaceRef.value?.setSelectedApi(Number(key));
  }
});

watch(() => tabDataMap, (newValue, oldValue) => {
  console.log("newValue", newValue)
}, {
  deep: true
})
</script>

<style scoped lang="less">
.api-manager-page {
  position: relative;
  padding: 0 16px 12px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.focus-btn {
  flex-shrink: 0;
}

.focus-exit-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.api-manager-page.focus-mode {
  padding: 0;
}

.api-manager-page.focus-mode .api-manager-card {
  border-radius: 0;
}

.api-manager-row {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.api-manager-inner-layout {
  flex: 1;
  overflow: hidden;
}

.api-manager-col {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.api-manager-side-col {
  flex-shrink: 0;
  transition: none; /* 拖拽时不需要动画 */
}

.api-manager-content-col {
  flex: 1;
}

/* 左侧伸缩分隔条 */
.api-manager-resizer {
  flex-shrink: 0;
  width: 6px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: col-resize;
  background: transparent;
  user-select: none;
}

.api-manager-resizer.is-collapsed {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  border-right: 1px solid var(--color-border-2);
  position: relative;
}

.sidebar-expand-btn {
  position: absolute;
  left: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-left: none;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
  z-index: 10;
}

.sidebar-expand-btn:hover {
  background: var(--color-fill-2);
}

.sidebar-expand-icon {
  color: rgb(var(--primary-6));
  font-size: 14px;
}

.api-manager-resizer:hover .resizer-line,
.api-manager-resizer:active .resizer-line {
  background: rgb(var(--primary-6));
}

.resizer-line {
  width: 2px;
  height: 24px;
  border-radius: 1px;
  background: var(--color-border-2);
}

.api-manager-spin {
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;

  :deep(.arco-spin-mask) {
    z-index: 100;
  }
}

.api-manager-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;
  border-radius: 0;
  border: none;

  :deep(.arco-card-body) {
    padding: 0;
    flex: 1;
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
  }
}

/* ===== 调试布局：表单 + 可拖拽/可折叠响应区 ===== */
.api-debug-layout {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.debug-form-region {
  flex: 1;
  min-height: 140px;
  overflow: hidden;
}

/* 拖拽分隔条 */
.response-resizer {
  position: relative;
  flex-shrink: 0;
  height: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: row-resize;
  background: var(--color-fill-2);
  border-top: 1px solid var(--color-border-2);
  border-bottom: 1px solid var(--color-border-2);
  user-select: none;
}

.response-resizer:hover {
  background: var(--color-fill-3);
}

.resizer-grip {
  width: 40px;
  height: 3px;
  border-radius: 2px;
  background: var(--color-text-4);
}

.resizer-collapse {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  cursor: pointer;
  color: var(--color-text-3);
  font-size: 14px;
}

.resizer-collapse:hover {
  color: rgb(var(--primary-6));
}

/* 响应面板区域（高度由 JS 动态控制） */
.debug-result-region {
  flex-shrink: 0;
  overflow: hidden;
  padding: 12px 16px;
  background: #fff;
}

/* 折叠态：底部长条 */
.response-collapsed-bar {
  flex-shrink: 0;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: var(--color-fill-2);
  border-top: 1px solid var(--color-border-2);
  cursor: row-resize;
  transition: background 0.15s;
}

.response-collapsed-bar:hover {
  background: var(--color-fill-3);
}

.response-collapsed-bar .bar-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 13px;
  color: var(--color-text-1);
}

.response-collapsed-bar .bar-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--color-text-3);
}

.response-collapsed-bar .meta-item {
  font-weight: 600;
  color: var(--color-text-2);
}

/* ===== 无接口时的空状态卡片 ===== */
.api-empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: var(--color-fill-1);
  user-select: none;
}

.empty-cards {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  justify-content: center;
}

.empty-tip {
  color: var(--color-text-3);
  font-size: 13px;
}
</style>
