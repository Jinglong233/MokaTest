<!--元素选择器-->
<template>
  <div class="element-select">
    <!--  定位来源单选：选哪个以哪个为准，两边数据都保留  -->
    <div class="locator-source-switch" :class="`source-${(localElement.locatorSource || 'LIBRARY').toLowerCase()}`">
      <a-radio-group v-model="localElement.locatorSource" type="button" :disabled="disabled"
                     @change="updateParent">
        <a-radio value="LIBRARY">库选元素</a-radio>
        <a-radio value="CUSTOM">自定义元素</a-radio>
      </a-radio-group>
    </div>

    <!--  库选元素面板：左目录树 + 右元素列表  -->
    <div v-show="localElement.locatorSource !== 'CUSTOM'" class="element-picker">
          <!--    左侧目录树-->
          <div class="folder-pane">
            <a-card class="element-tree-card">
              <a-button type="text" style="width: 100%;padding: 0" @click="allElement()">
                <template #icon>
                  <icon-select-all/>
                </template>
                全部元素
              </a-button>
              <div class="element-tree-scroll">
                <a-tree :data="folderTree"
                        :selected-keys="selectedFolder"
                        block-node
                        show-line
                        @select="selectFolder"
                        :fieldNames="{
                  key: 'id',
                  title: 'elementName',
                  }"
                >
                  <template #switcher-icon="node, { isLeaf }">
                    <icon-folder style="font-size: 16px;"/>
                  </template>
                </a-tree>
              </div>
            </a-card>
          </div>
          <!--    右侧元素列表-->
          <div class="element-pane">
            <a-card class="element-list-card">
              <!--        搜索-->
              <a-input v-model="searchElementForm.elementName"
                       :disabled="disabled"
                       allow-clear
                       placeholder="搜索元素名称"
                       class="element-search-input"
                       @input="debounceSearch"
                       @press-enter="searchElement"
                       @clear="searchElement"/>
              <!--        元素列表：仅元素名，原生滚动条 -->
              <div class="element-list">
                <div v-if="elementList.length === 0" class="element-empty">
                  {{ searchElementForm.elementName ? '未搜索到匹配的元素' : '当前目录下暂无元素' }}
                </div>
                <div v-for="element in elementList"
                     :key="element.id"
                     class="element-item"
                     :class="{ 'is-selected': localElement.locator?.id === element.id, 'is-disabled': disabled }"
                     :title="localElement.locator?.id === element.id ? element.elementName + '（再次点击取消选择）' : element.elementName"
                     @click="handleElementItemClick(element)">
                  <span class="element-item-name">{{ element.elementName }}</span>
                </div>
              </div>
            </a-card>
          </div>
        </div>

    <!--  自定义元素面板  -->
    <div v-show="localElement.locatorSource === 'CUSTOM'">
        <a-form :model="localElement.customLocator">
          <a-form-item field="customLocator.locatorType" label="定位方式">
            <a-select v-model="localElement.customLocator.locatorType" @change="updateParent"
                      :disabled="disabled">
              <a-option v-for="opt in locatorTypeOptions" :key="opt.value" :value="opt.value">{{ $t(opt.labelKey) }}</a-option>
            </a-select>
          </a-form-item>
          <a-form-item field="customLocator.locatorValue" label="定位值">
            <a-input v-model="localElement.customLocator.locatorValue" @change="updateParent"
                     :disabled="disabled"
                     :placeholder="localElement.customLocator?.locatorType === 'ROLE' ? '格式：角色::名称，如 button::登录' : '请输入定位值'"/>
          </a-form-item>
        </a-form>
        <a-button style="width: 100%;" @click="userAddOrUpdateElement('新建', localElement.customLocator)"
                  :disabled="disabled">
          添加到元素管理
        </a-button>
    </div>

    <!--  当前生效定位链：仅库选元素时显示所选元素的只读定位链（有序）  -->
    <div v-if="localElement.locatorSource === 'LIBRARY' && effectiveLocatorChain.length > 0"
         class="effective-locator-chain">
      <div class="effective-chain-header">
        <span class="effective-label">{{ $t('scene.elementSelect.effectiveChain') }}</span>
        <span class="effective-chain-tip">{{ $t('scene.elementSelect.chainReadonlyTip') }}</span>
      </div>
      <div v-for="(item, idx) in effectiveLocatorChain" :key="idx" class="effective-chain-item">
        <span class="effective-chain-index">{{ idx + 1 }}</span>
        <a-tag size="small">{{ locatorTypeLabel(item.type) }}</a-tag>
        <span class="effective-value" :title="item.value">{{ item.value }}</span>
        <a-tooltip :content="$t('scene.elementSelect.copyValue')">
          <a-button type="text" size="mini" class="effective-copy" @click="copyChainValue(item.value)">
            <icon-copy/>
          </a-button>
        </a-tooltip>
      </div>
    </div>

    <!--添加元素对话框-->
    <a-modal v-model:visible="addOrUpdateElementVisible" title="新建元素" @cancel="handleElementCancel"
             @before-ok="handleElementBeforeOk">
      <a-form ref="addElementFormRef" :model="addOrUpdateElementForm" :rules="addElementFormRules">
        <a-form-item field="parentId" label="所属目录" required>
          <a-tree-select
              :data="folderTree"
              placeholder="元素所属目录"
              :fieldNames="{
                key: 'id',
                title: 'elementName',
            }"
              v-model="addOrUpdateElementForm.parentId"
          >
            <template #tree-slot-icon>
              <icon-folder/>
            </template>
          </a-tree-select>
        </a-form-item>
        <a-form-item field="elementName" label="元素名称" required>
          <a-input v-model="addOrUpdateElementForm.elementName"/>
        </a-form-item>
        <a-form-item field="locatorType" label="定位方式" required>
          <a-select v-model="addOrUpdateElementForm.locatorType">
            <a-option v-for="opt in locatorTypeOptions" :key="opt.value" :value="opt.value">{{ $t(opt.labelKey) }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="locatorValue" label="定位值" required>
          <a-input v-model="addOrUpdateElementForm.locatorValue"
                   :placeholder="addOrUpdateElementForm.locatorType === 'ROLE' ? '格式：角色::名称，如 button::登录' : '请输入定位值'"/>
        </a-form-item>
        <a-form-item field="description" label="元素描述">
          <a-textarea v-model="addOrUpdateElementForm.description" allow-clear :max-length="50" show-word-limit
                      :auto-size="true"/>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import {IconFolder} from '@arco-design/web-vue/es/icon';
import {computed, onMounted, ref, watch} from 'vue';
import {useI18n} from 'vue-i18n';
import {Message} from "@arco-design/web-vue";
import {ElementVO} from "@/types/vo/ElementVO";
import {Element, LocatorChainItem} from "@/types/domain/Element";
import {LOCATOR_TYPE_OPTIONS} from "@/types/enum/element/ElementLocatorType";
import {
  add,
  getAllElementByProjectId,
  getElementById,
  getElementList,
  getElementListByFolderId,
  getFolderList,
  update
} from "@/api/MyApi/element";
import {ElementDTO} from "@/types/dto/ElementDTO";
import {useProjectStore} from "@/store";

interface Props {
  // 当前选择元素
  modelValue: ElementDTO
  // 只读模式（无步骤编辑权限或调试锁定时不允许修改元素选择/自定义定位）
  disabled?: boolean
}

const projectStore = useProjectStore();

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => new ElementDTO(), // 使用创建新元素的函数
  disabled: false
});

interface Emits {
  (e: 'update:modelValue', value: ElementDTO): void

  (e: 'change', value: ElementDTO): void
}

const emit = defineEmits<Emits>()

const {t} = useI18n();

// 定位类型下拉选项（含 TEXT_CONTAINS）
const locatorTypeOptions = LOCATOR_TYPE_OPTIONS;

// 定位类型展示文案
const locatorTypeLabel = (type?: string) => {
  const option = locatorTypeOptions.find((o) => o.value === type);
  return option ? t(option.labelKey) : (type || '');
};


// 创建本地副本，使用深度克隆避免引用问题
const localElement = ref<ElementDTO>(new ElementDTO())

// 监听 props 变化，更新本地数据（仅做数据同步，不触发接口请求）
watch(() => props.modelValue, (newVal) => {
  localElement.value = JSON.parse(JSON.stringify(newVal || new ElementDTO()))
  if (!localElement.value.locator) {
    localElement.value.locator = new Element();
  }
  if (!localElement.value.customLocator) {
    localElement.value.customLocator = new Element();
  }
  // 历史数据无来源标记：按「库选优先」规则推导展示来源（保存时会写入显式标记，语义不变）
  if (!localElement.value.locatorSource) {
    const locator = localElement.value.locator;
    localElement.value.locatorSource = (locator?.locatorType && locator?.locatorValue) ? 'LIBRARY' : 'CUSTOM';
  }
}, {deep: true, immediate: true})

// 仅在库选元素 id 变化时请求元素详情：校验元素是否已被从元素库删除，并刷新定位链展示
watch(() => props.modelValue?.locator?.id, async (elementId) => {
  if (!elementId) {
    return;
  }
  const result = await getElementById(elementId)
  // 等待期间用户可能已改选/取消，回包与当前选择不一致时不处理
  if (localElement.value.locator?.id !== elementId) {
    return;
  }
  if (result.data === null) {
    // 库元素已删除：降级为自定义定位（保留定位链快照，并回写父组件避免已删除的 id 落库）
    localElement.value.customLocator.locatorValue = localElement.value.locator.locatorValue;
    localElement.value.customLocator.locatorType = localElement.value.locator.locatorType;
    localElement.value.customLocator.locatorChain = localElement.value.locator.locatorChain;
    // 将此定位变为自定义
    localElement.value.locator = new Element();
    localElement.value.locatorSource = 'CUSTOM';
    Message.warning({
      content: t('scene.elementSelect.deletedFallback'),
      duration: 2500
    });
    updateParent();
  } else if (result.data?.locatorChain?.length) {
    // 刷新定位链快照用于只读展示（不回写父组件，不改变保存语义）
    localElement.value.locator.locatorChain = result.data.locatorChain;
  }
}, {immediate: true})

// 更新父组件数据
const updateParent = () => {
  emit('update:modelValue', JSON.parse(JSON.stringify(localElement.value)))
  emit('change', JSON.parse(JSON.stringify(localElement.value)))
}

// ==================== 当前生效定位链 ====================

// 当前生效定位链：优先取库元素的 locatorChain，为空时按主定位合成单元素链
const effectiveLocatorChain = computed<LocatorChainItem[]>(() => {
  const locator = localElement.value?.locator;
  if (!locator) return [];
  if (locator.locatorChain && locator.locatorChain.length > 0) {
    return locator.locatorChain;
  }
  if (locator.locatorType && locator.locatorValue) {
    return [{type: locator.locatorType.toUpperCase(), value: locator.locatorValue}];
  }
  return [];
});

// 复制链上某项的定位值
const copyChainValue = async (value?: string) => {
  if (!value) return;
  try {
    await navigator.clipboard.writeText(value);
    Message.success({content: t('scene.elementSelect.copySuccess'), duration: 1000});
  } catch (e) {
    Message.warning({content: t('scene.elementSelect.copyFailed'), duration: 1500});
  }
};

// 选中库元素：两边数据都保留，只更新库选值（来源由顶部单选决定）
const handleElementItemClick = (element: Element) => {
  if (props.disabled) return;
  if (localElement.value.locator?.id === element.id) {
    // 点击已选中的元素，取消选择
    clearSelectElement();
    return;
  }
  localElement.value.locator = {...element};
  updateParent();
};

// 取消库元素选择：仅清空库选值，不影响自定义定位
const clearSelectElement = () => {
  if (props.disabled) return;
  localElement.value.locator = new Element();
  updateParent();
};

// 弹窗模式
const modalMode = ref('新建')

// 当前选择的目录（空数组 = 全部元素）
const selectedFolder = ref<string[]>([])

// 添加元素对话框开关
const addOrUpdateElementVisible = ref(false)

// 添加元素表单引用
const addElementFormRef = ref()

// 添加元素表单
const addOrUpdateElementForm = ref<Element>(new Element())

// 目录树列表
const folderTree = ref<ElementVO[]>([])

// 加载目录列表
const reloadFolderTree = async () => {
  const folderList = await getFolderList(String(projectStore.getProjectId ?? ''))
  folderTree.value = folderList.data
}

// 加载元素列表
const reloadElementList = async (folderId: string) => {
  let elList
  if (folderId == null) {
    elList = await getElementListByFolderId({
      folderId: '0',
      projectId: projectStore.getProjectId
    })
  } else {
    elList = await getElementListByFolderId({
      folderId: folderId,
      projectId: projectStore.getProjectId
    })
  }
  elementList.value = elList.data
}

onMounted(async () => {
  // 获取目录列表
  await reloadFolderTree()

  // 获取元素列表
  await reloadElementList('0')
})

// 搜索元素表单
const searchElementForm = ref<any>({
  elementName: '',
  elementType: 'ELEMENT',
  parentId: null,
});

// 元素列表
const elementList = ref<Element[]>([])


// 取消添加元素
const handleElementCancel = () => {
  addOrUpdateElementVisible.value = false
  // 清除校验状态
  addElementFormRef.value.clearValidate()
  addOrUpdateElementForm.value = new Element();
}

// 点击添加/编辑元素
const userAddOrUpdateElement = (mode: string, element: Element | undefined) => {
  modalMode.value = mode
  addOrUpdateElementVisible.value = true

  if (element != null) {
    addOrUpdateElementForm.value = {...new Element(), ...element}
  } else {
    addOrUpdateElementForm.value = {...new Element(), elementType: 'ELEMENT'}
  }
}

// 添加元素表单校验
const addElementFormRules = {
  parentId: [
    {
      required: true,
      message: '请选择所属目录',
    },
  ],
  elementName: [
    {
      required: true,
      message: '请输入元素名称',
    },
  ],
  locatorType: [
    {
      required: true,
      message: '请选择定位方式',
    },
  ],
  locatorValue: [
    {
      required: true,
      message: '请输入定位值',
    },
  ]
}

// 确认添加元素
const handleElementBeforeOk = async () => {
  const error = await addElementFormRef.value?.validate()
  if (error) {
    return false
  }

  let result: any
  if (addOrUpdateElementForm.value.id != null) {
    // id不为空代表是更新
    result = await update(addOrUpdateElementForm.value)
  } else { // 添加
    addOrUpdateElementForm.value.projectId = String(projectStore.getProjectId ?? '')
    addOrUpdateElementForm.value.createUserId = '1'
    addOrUpdateElementForm.value.updateUserId = "1"
    addOrUpdateElementForm.value.elementType = 'ELEMENT'
    result = await add(addOrUpdateElementForm.value)
  }
  if (result.data === true) {
    Message.success({
      content: `${modalMode.value}成功`,
      duration: 1000
    })
    // 新建成功后：刷新目标目录元素列表，并自动选中新元素（来源切到库选），流程闭环
    const folderId = addOrUpdateElementForm.value.parentId ?? '0';
    const listResult = await getElementListByFolderId({
      folderId: String(folderId),
      projectId: projectStore.getProjectId
    });
    elementList.value = listResult.data;
    selectedFolder.value = [String(folderId)];
    const newElement = (listResult.data || []).find(
        (el: Element) => el.elementName === addOrUpdateElementForm.value.elementName
            && el.locatorValue === addOrUpdateElementForm.value.locatorValue
    );
    if (newElement) {
      // 自动选中：来源切到库选
      localElement.value.locator = {...newElement};
      localElement.value.locatorSource = 'LIBRARY';
      updateParent();
      Message.success({
        content: '已自动选中新元素',
        duration: 1500
      });
    }
  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }
  return true
}

// 点击选择目录：保留搜索词，在新目录范围内重新搜索
const selectFolder = async (data: any) => {
  selectedFolder.value = data
  if (searchElementForm.value.elementName && searchElementForm.value.elementName.trim() !== '') {
    await searchElement();
    return;
  }
  const result = await getElementListByFolderId({
    folderId: data[0],
    projectId: projectStore.getProjectId
  })
  elementList.value = result.data
}

// 获取全部元素：保留搜索词，按全部范围重新搜索
const allElement = async () => {
  // 清空当前选择的目录
  selectedFolder.value = []
  if (searchElementForm.value.elementName && searchElementForm.value.elementName.trim() !== '') {
    await searchElement();
    return;
  }
  let axiosResponse = await getAllElementByProjectId(String(projectStore.getProjectId ?? ''))
  elementList.value = axiosResponse.data
}

const searchElement = async () => {
  searchElementForm.value.elementType = "ELEMENT";

  // 判断是否是全部元素
  if (selectedFolder.value.length === 0) {
    searchElementForm.value.parentId = null;
  } else {
    searchElementForm.value.parentId = selectedFolder.value[0];
  }
  // 进行搜索
  const result = await getElementList(searchElementForm.value);
  elementList.value = result.data;
}

// 输入即时搜索（300ms 防抖）
let searchTimer: any = null;
const debounceSearch = () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    searchElement();
  }, 300);
};


</script>

<style scoped>
.element-select {
  width: 100%;
}

/* 定位来源单选：库选蓝色选中态（默认），自定义绿色选中态，与底部生效条颜色呼应 */
.locator-source-switch {
  margin-bottom: 12px;
}

.locator-source-switch.source-custom :deep(.arco-radio-button.arco-radio-checked) {
  color: rgb(var(--green-6));
  border-color: rgb(var(--green-6));
  background-color: rgb(var(--green-1));
}

/* 左目录树 + 右元素列表两栏布局 */
.element-picker {
  display: flex;
  gap: 12px;
  height: 340px;
  width: 100%;
  margin-bottom: 12px;
}

.folder-pane {
  width: 180px;
  flex-shrink: 0;
  height: 100%;
}

.element-pane {
  flex: 1;
  min-width: 0;
  height: 100%;
}

.element-tree-card {
  height: 100%;
  margin: 0;
  padding: 0;
}

.element-tree-card :deep(.arco-card-body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 8px;
}

/* 目录树滚动区：原生滚动条 */
.element-tree-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.element-list-card {
  height: 100%;
  position: relative;
}

.element-list-card :deep(.arco-card-body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 8px;
}

.element-search-input {
  flex-shrink: 0;
  margin-bottom: 8px;
}

/* 元素列表：剩余高度 + 原生滚动条 */
.element-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border: 1px solid var(--color-neutral-3);
  border-radius: 4px;
  padding: 4px;
  box-sizing: border-box;
}

.element-empty {
  padding: 32px 0;
  text-align: center;
  color: var(--color-text-3);
  font-size: 13px;
}

/* 元素列表项：仅元素名 */
.element-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid transparent;
}

.element-item:hover {
  background-color: var(--color-fill-2);
}

.element-item.is-selected {
  border-color: rgb(var(--arcoblue-6));
  background-color: rgb(var(--arcoblue-1));
}

.element-item.is-disabled {
  cursor: not-allowed;
}

.element-item-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--color-text-1);
}

/* 表单校验错误态不应传导给组件内部输入控件（搜索框、自定义定位输入等），
   错误文案由外层 form-item 统一展示 */
.element-select :deep(.arco-input-wrapper.arco-input-error),
.element-select :deep(.arco-input-wrapper.arco-input-error:hover),
.element-select :deep(.arco-input-wrapper.arco-input-error:focus-within),
.element-select :deep(.arco-select-view-single.arco-select-view-error),
.element-select :deep(.arco-select-view-single.arco-select-view-error:hover),
.element-select :deep(.arco-select-view-single.arco-select-view-error:focus-within),
.element-select :deep(.arco-textarea-wrapper.arco-textarea-error),
.element-select :deep(.arco-textarea-wrapper.arco-textarea-error:hover),
.element-select :deep(.arco-textarea-wrapper.arco-textarea-error:focus-within) {
  border-color: var(--color-neutral-3);
  background-color: var(--color-bg-2);
  box-shadow: none;
}

/* 当前生效定位链（只读） */
.effective-locator-chain {
  padding: 6px 8px;
  border: 1px dashed var(--color-neutral-3);
  border-radius: 4px;
  background-color: var(--color-fill-1);
  min-width: 0;
}

.effective-chain-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.effective-chain-tip {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.effective-chain-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
}

.effective-chain-index {
  flex-shrink: 0;
  width: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.effective-label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-2);
}

.effective-value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--color-text-1);
}

.effective-copy {
  flex-shrink: 0;
  padding: 0 2px;
}
</style>
