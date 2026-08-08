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
          @change="() => handleUpdate(rowIndex, record)"
        />
      </template>
      <template #name="{ record, rowIndex }">
        <a-input
          v-model="record.name"
          placeholder="参数名"
          :disabled="disabled"
          @blur="() => handleUpdate(rowIndex, record)"
        />
      </template>
      <template #value="{ record, rowIndex }">
        <a-input
          v-model="record.value"
          placeholder="参数值"
          :disabled="disabled"
          @blur="() => handleUpdate(rowIndex, record)"
        />
      </template>
      <template #operation="{ record, rowIndex }">
        <a-space>
          <a-button
            :disabled="disabled"
            type="text"
            status="danger"
            size="small"
            @click="deleteRow(rowIndex)"
          >
            <icon-delete />
          </a-button>
        </a-space>
      </template>
    </a-table>
    <a-button :disabled="disabled" type="dashed" long @click="openAddModal" style="margin-top: 10px">
      <icon-plus />
      添加参数
    </a-button>

    <!-- 添加参数弹窗 -->
    <a-modal
      v-model:visible="modalVisible"
      title="添加参数"
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
          <a-switch
            v-model="formData.disabled"
            :checked-value="false"
            :unchecked-value="true"
            :disabled="disabled"
          />
          <span style="margin-left: 8px">{{
            formData.disabled ? '禁用' : '启用'
          }}</span>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { ref, reactive } from 'vue';
  import { IconPlus, IconDelete } from '@arco-design/web-vue/es/icon';
  import { Message, Modal } from '@arco-design/web-vue';
  import { RequestParameter } from '@/types/domain/api/requestModel/RequestParameter';

  const props = defineProps<{
    fieldName: string;
    disabled?: boolean;
  }>();

  const columns = [
    { title: '状态', slotName: 'status', width: '10%' },
    { title: '参数名', slotName: 'name', width: '40%' },
    { title: '参数值', slotName: 'value', width: '40%' },
    { title: '操作', slotName: 'operation', width: '10%' },
  ];

  const tableData = ref<any[]>([]);
  const modalVisible = ref(false);

  const formData = ref(new RequestParameter());

  const emit = defineEmits(['add', 'update', 'delete']);

  const openAddModal = () => {
    if (props.disabled) return;
    formData.value = new RequestParameter();
    modalVisible.value = true;
  };

  const deleteRow = (index: number) => {
    if (props.disabled) return;
    const row = tableData.value[index];
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除参数"${row.name}"吗？`,
      onOk: () => {
        emit('delete', props.fieldName, index);
      },
    });
  };

  const handleUpdate = (index: number, record: any) => {
    if (props.disabled) return;
    emit('update', props.fieldName, index, {
      name: record.name,
      value: record.value,
      disabled: record.disabled,
    });
  };

  const handleModalOk = () => {
    if (props.disabled) return;
    if (!formData.value.name.trim()) {
      Message.warning('请输入参数名');
      return;
    }

    emit('add', props.fieldName, {
      name: formData.value.name,
      value: formData.value.value,
      disabled: formData.value.disabled,
    });
    modalVisible.value = false;
  };

  const handleModalCancel = () => {
    modalVisible.value = false;
  };

  const getData = () => tableData.value;

  const setData = (data: any[]) => {
    tableData.value = data && data.length ? [...data] : [];
  };

  defineExpose({ getData, setData });
</script>

<style scoped lang="scss"></style>
