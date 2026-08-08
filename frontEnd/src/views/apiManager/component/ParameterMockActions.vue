<template>
  <a-space class="cell-actions">
    <a-popover
        trigger="click"
        position="left"
        :popup-visible="visible"
        @popup-visible-change="(val: boolean) => emit('update:visible', val)"
        unmount-on-close
    >
      <a-button type="text" size="mini" :disabled="disabled">Mock</a-button>
      <template #content>
        <MockDataPopover
            :parameter-type="record?.type"
            :mock-config="record?.mockConfig"
            @select="(config: MockConfig) => emit('select', config)"
            @cancel="emit('cancel')"
        />
      </template>
    </a-popover>
  </a-space>
</template>

<script setup lang="ts">
import {RequestParameter} from '@/types/domain/api/requestModel/RequestParameter';
import {MockConfig} from '@/types/domain/api/requestModel/MockConfig';
import MockDataPopover from './MockDataPopover.vue';

interface Props {
  record?: RequestParameter;
  visible?: boolean;
  disabled?: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
  (e: 'select', config: MockConfig): void;
  (e: 'cancel'): void;
}>();
</script>
