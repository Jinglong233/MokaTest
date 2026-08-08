<template>
  <div class="parameter-table">
    <a-table :columns="columns" :data="tableData" bordered :pagination="false">
      <template #status="{ record, rowIndex }">
        <a-switch
            size="small"
            v-model="record.disabled"
            :checked-value="false"
            :unchecked-value="true"
            :disabled="disabled"
            @change="() => handleUpdate(record, rowIndex)"
        />
      </template>
      <template #name="{ record, rowIndex }">
        <span class="param-name">{{ record.name }}</span>
      </template>
      <template #value="{ record, rowIndex }">
        <span class="param-value">{{ record.value }}</span>
      </template>
      <template #operation="{ record, rowIndex }">
        <a-space>
          <a-button :disabled="disabled" type="text" size="small" @click="editRow(rowIndex)">
            <icon-edit/>
          </a-button>
          <a-button :disabled="disabled" type="text" status="danger" size="small" @click="deleteRow(rowIndex)">
            <icon-delete/>
          </a-button>
        </a-space>
      </template>
    </a-table>
    <a-button :disabled="disabled" type="dashed" long @click="openAddModal" style="margin-top: 10px">
      <icon-plus/>
      添加参数
    </a-button>

    <!-- 添加/编辑参数弹窗 -->
    <a-modal
        v-model:visible="modalVisible"
        :title="modalTitle"
        width="500px"
        @ok="handleModalOk"
        @cancel="handleModalCancel"
    >
      <a-form :model="formData" layout="vertical">
        <a-form-item label="参数名" required>
          <a-input
              v-model="formData.name"
              placeholder="请输入参数名"
              allow-clear
              :disabled="disabled"
          />
        </a-form-item>
        <a-form-item label="参数值">
          <a-input
              v-model="formData.value"
              placeholder="请输入参数值"
              allow-clear
              :disabled="disabled"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model="formData.disabled" :checked-value="false" :unchecked-value="true" :disabled="disabled"/>
          <span style="margin-left: 8px">{{ formData.disabled ? '禁用' : '启用' }}</span>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import {ref, reactive, watch} from 'vue'
import {IconPlus, IconEdit, IconDelete} from '@arco-design/web-vue/es/icon'
import {Message, Modal} from '@arco-design/web-vue'
import {GlobalRequestVarType} from "@/types/domain/api/apiEnum/GlobalRequestVarType";

const props = defineProps<{
  type?: GlobalRequestVarType
  teamId?: number
  data?: any[]
  disabled?: boolean
}>()

const columns = [
  {title: '状态', slotName: 'status', width: '10%'},
  {title: '参数名', slotName: 'name', width: '35%'},
  {title: '参数值', slotName: 'value', width: '35%'},
  {title: '操作', slotName: 'operation', width: '20%'}
]

const tableData = ref<any[]>([])
const modalVisible = ref(false)
const modalTitle = ref('添加参数')
const editIndex = ref(-1)
const editId = ref<number | null>(null)

const formData = reactive({
  name: '',
  value: '',
  disabled: false
})

const emit = defineEmits(['add', 'update', 'delete'])

// 监听外部传入的数据
watch(() => props.data, (newData) => {
  if (newData) {
    tableData.value = [...newData]
  }
}, {immediate: true, deep: true})

const openAddModal = () => {
  modalTitle.value = '添加参数'
  editIndex.value = -1
  editId.value = null
  formData.name = ''
  formData.value = ''
  formData.disabled = false
  modalVisible.value = true
}

const editRow = (index: number) => {
  const row = tableData.value[index]
  if (row) {
    modalTitle.value = '编辑参数'
    editIndex.value = index
    editId.value = row.id
    formData.name = row.name
    formData.value = row.value
    formData.disabled = row.disabled || false
    modalVisible.value = true
  }
}

const deleteRow = (index: number) => {
  if (props.disabled) return
  const row = tableData.value[index]
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除参数"${row.name}"吗？`,
    onOk: () => {
      if (props.type && row.id) {
        // 全局参数：触发删除事件
        emit('delete', props.type, row.id)
      }
      tableData.value.splice(index, 1)
      Message.success('删除成功')
    }
  })
}

const handleUpdate = (record: any, index: number) => {
  if (props.disabled) return
  if (props.type && record.id) {
    // 全局参数：触发更新事件
    emit('update', props.type, record.id, {
      name: record.name,
      value: record.value,
      disabled: record.disabled
    })
  }
}

const handleModalOk = () => {
  if (props.disabled) return
  if (!formData.name.trim()) {
    Message.warning('请输入参数名')
    return
  }

  if (editIndex.value >= 0) {
    // 编辑模式
    const updatedData = {
      ...tableData.value[editIndex.value],
      name: formData.name,
      value: formData.value,
      disabled: formData.disabled
    }
    tableData.value[editIndex.value] = updatedData

    if (props.type && editId.value) {
      // 全局参数：触发更新事件
      emit('update', props.type, editId.value, {
        name: formData.name,
        value: formData.value,
        disabled: formData.disabled
      })
    }
    Message.success('修改成功')
  } else {

    if (props.type) {
      // 全局参数：触发新增事件
      emit('add', props.type, {
        name: formData.name,
        value: formData.value,
        disabled: formData.disabled
      })
    } else {
      // 环境参数：直接添加
      tableData.value.push({
        id: null,
        name: formData.name,
        value: formData.value,
        disabled: formData.disabled
      })
      Message.success('添加成功')
    }
  }

  modalVisible.value = false
}

const handleModalCancel = () => {
  modalVisible.value = false
}

const getData = () => tableData.value

const setData = (data: any[]) => {
  tableData.value = data && data.length ? [...data] : []
}

const addRow = (param?: { name: string; value: string; disabled?: boolean }) => {
  if (param) {
    if (props.type) {
      // 全局参数：触发新增事件
      emit('add', props.type, {
        name: param.name,
        value: param.value,
        disabled: param.disabled || false
      })
    } else {
      tableData.value.push({
        name: param.name,
        value: param.value,
        disabled: param.disabled !== undefined ? param.disabled : false
      })
    }
  } else {
    openAddModal()
  }
}

defineExpose({getData, setData, addRow, openAddModal})
</script>

<style scoped lang="scss">
.parameter-table {
  .param-name,
  .param-value {
    word-break: break-all;
    white-space: normal;
    line-height: 1.5;
  }
}
</style>