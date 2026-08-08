<template>
  <div class="script-list-panel">
    <!-- 顶部操作栏 -->
    <div class="script-list-header">
      <div class="script-tip">
        {{ scriptType === 'pre' ? '前置脚本在请求发送前执行，可用于动态设置变量（如时间戳、签名等）' : '后置脚本在收到响应后执行，可用于提取数据、自定义断言' }}
      </div>
      <a-button type="primary" size="small" :disabled="disabled" @click="addScript">
        <template #icon><icon-plus /></template>
        添加脚本
      </a-button>
    </div>

    <!-- 脚本列表 -->
    <div class="script-list-scroll">
      <div class="script-list">
      <div
        v-for="(item, index) in scriptList"
        :key="item.id"
        class="script-item"
        :class="{ 'is-expanded': expandedId === item.id }"
      >
        <!-- 脚本头部（可点击展开） -->
        <div class="script-item-header" @click="toggleExpand(item.id)">
          <div class="script-item-left">
            <icon-down v-if="expandedId === item.id" class="expand-icon" />
            <icon-right v-else class="expand-icon" />
            <span class="script-index">{{ index + 1 }}</span>
            <a-input
              v-model="item.name"
              size="mini"
              class="script-name-input"
              placeholder="脚本名称"
              :disabled="disabled"
              @click.stop
              @input="onChange"
            />
          </div>
          <div class="script-item-right">
            <a-switch
              v-model="item.enabled"
              size="small"
              type="round"
              :disabled="disabled"
              @change="onChange"
              @click.stop
            />
            <a-button
              type="text"
              size="mini"
              status="danger"
              :disabled="disabled"
              @click.stop="removeScript(index)"
            >
              <template #icon><icon-delete /></template>
            </a-button>
          </div>
        </div>

        <!-- 脚本内容（展开时显示） -->
        <div v-show="expandedId === item.id" class="script-item-body">
          <ScriptEditor
            v-model="item.content"
            :script-type="scriptType"
            :disabled="disabled"
            @change="onChange"
          />
        </div>
      </div>

      <!-- 空状态 -->
      <a-empty v-if="scriptList.length === 0" description="暂无脚本，点击上方按钮添加" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { IconPlus, IconDelete, IconDown, IconRight } from '@arco-design/web-vue/es/icon'
import ScriptEditor from './ScriptEditor.vue'
import { ScriptItem } from '@/types/domain/api/requestModel/ScriptItem'

interface Props {
  modelValue?: ScriptItem[]
  scriptType: 'pre' | 'post'
  disabled?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: ScriptItem[]): void
  (e: 'change'): void
}>()

const expandedId = ref<string | null>(null)

const scriptList = computed({
  get: () => props.modelValue || [],
  set: (val) => {
    emit('update:modelValue', val)
    emit('change')
  }
})

// 生成唯一 ID
let idCounter = 0
const generateId = () => {
  idCounter++
  return `script_${Date.now()}_${idCounter}`
}

// 添加脚本
const addScript = () => {
  if (props.disabled) return
  const list = [...scriptList.value]
  const newItem: ScriptItem = {
    id: generateId(),
    name: `${props.scriptType === 'pre' ? '前置' : '后置'}脚本 ${list.length + 1}`,
    content: '',
    enabled: true,
    sort: list.length
  }
  list.push(newItem)
  scriptList.value = list
  // 自动展开新添加的脚本
  expandedId.value = newItem.id
}

// 删除脚本
const removeScript = (index: number) => {
  if (props.disabled) return
  const list = [...scriptList.value]
  const removed = list.splice(index, 1)
  if (removed.length > 0 && expandedId.value === removed[0].id) {
    expandedId.value = null
  }
  // 重新计算 sort
  list.forEach((item, i) => item.sort = i)
  scriptList.value = list
}

// 展开/折叠
const toggleExpand = (id: string) => {
  expandedId.value = expandedId.value === id ? null : id
}

const onChange = () => {
  emit('change')
}
</script>

<style scoped lang="less">
.script-list-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  gap: 0;
}

.script-list-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-shrink: 0;
  margin-bottom: 12px;

  .script-tip {
    font-size: 12px;
    color: #666;
    padding: 6px 10px;
    background: #f5f5f5;
    border-radius: 4px;
    flex: 1;
  }
}

.script-list-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.script-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.script-item {
  border: 1px solid #e5e5e5;
  border-radius: 4px;
  background: #fff;
  overflow: hidden;

  &.is-expanded {
    border-color: #c5c5c5;
  }
}

.script-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: #f8f9fa;
  }
}

.script-item-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;

  .expand-icon {
    font-size: 12px;
    color: #888;
    flex-shrink: 0;
  }

  .script-index {
    font-size: 12px;
    color: #888;
    width: 20px;
    text-align: center;
    flex-shrink: 0;
  }

  .script-name-input {
    flex: 1;
    min-width: 0;

    :deep(.arco-input) {
      border: none;
      background: transparent;
      padding: 0;
      font-size: 13px;
      font-weight: 500;

      &:hover,
      &:focus {
        background: #f0f0f0;
      }
    }
  }
}

.script-item-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.script-item-body {
  padding: 0 12px 12px;
}
</style>
