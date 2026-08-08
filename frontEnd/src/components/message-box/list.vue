<template>
  <a-list :bordered="false">
    <a-list-item
      v-for="item in renderList"
      :key="item.id"
      action-layout="vertical"
      :style="{
        opacity: item.isRead ? 0.6 : 1,
      }"
    >
      <template #extra>
        <a-tag v-if="!item.isRead" color="red">未读</a-tag>
        <a-tag v-else color="gray">已读</a-tag>
      </template>
      <div class="item-wrap" @click="onItemClick(item)">
        <a-list-item-meta>
          <template #avatar>
            <a-avatar shape="circle">
              <icon-notification />
            </a-avatar>
          </template>
          <template #title>
            <a-space :size="4">
              <span>{{ item.title }}</span>
            </a-space>
          </template>
          <template #description>
            <div>
              <a-typography-paragraph
                :ellipsis="{
                  rows: 1,
                }"
              >{{ item.content }}</a-typography-paragraph>
              <a-space size="mini" style="margin-top: 4px;">
                <a-tag v-if="item.snapshot?.teamName" size="small" color="arcoblue">{{ item.snapshot.teamName }}</a-tag>
                <a-tag v-if="item.snapshot?.projectName" size="small" color="green">{{ item.snapshot.projectName }}</a-tag>
              </a-space>
              <a-typography-text class="time-text">
                {{ item.createTime }}
              </a-typography-text>
            </div>
          </template>
        </a-list-item-meta>
      </div>
    </a-list-item>
  </a-list>
</template>

<script lang="ts" setup>
  import { PropType } from 'vue';
  import { MessageRecord, MessageListType } from '@/api/message';

  const props = defineProps({
    renderList: {
      type: Array as PropType<MessageListType>,
      required: true,
    },
  });

  const emit = defineEmits(['itemClick']);

  const onItemClick = (item: MessageRecord) => {
    emit('itemClick', [item]);
  };
</script>

<style scoped lang="less">
  :deep(.arco-list) {
    .arco-list-item {
      min-height: 86px;
      border-bottom: 1px solid rgb(var(--gray-3));
    }
    .arco-list-item-extra {
      position: absolute;
      right: 20px;
    }
    .arco-list-item-meta-content {
      flex: 1;
    }
    .item-wrap {
      cursor: pointer;
    }
    .time-text {
      font-size: 12px;
      color: rgb(var(--gray-6));
    }
    .arco-empty {
      display: none;
    }
    .arco-typography {
      margin-bottom: 0;
    }
  }
</style>
