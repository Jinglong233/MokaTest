<template>
  <div>
    <a-spin style="width: 100%">
      <a-card
          class="general-card"
      >
        <a-row justify="space-between">
          <a-col v-for="(item, idx) in renderData" :key="idx" :span="6">
            <a-statistic
                :title="item.title"
                :value="item.value"
                show-group-separator
                :value-from="0"
                animation
            >
              <template #prefix>
              <span
                  class="statistic-prefix"
                  :style="{ background: item.prefix.background }"
              >
                <component
                    :is="item.prefix.icon"
                    :style="{ color: item.prefix.iconColor }"
                />
              </span>
              </template>

              <template #suffix>{{ item.suffix }}</template>

              <template #extra v-if="item.hasItem">
                <div class="boxes-container">
                  <a-tooltip content="成功数量" background-color="#00b42a">
                    <div class="box box-1">{{ item.hasItem.success }}</div>
                  </a-tooltip>
                  <a-tooltip content="失败数量" background-color="#ff221b">
                    <div class="box box-2">{{ item.hasItem.error }}</div>
                  </a-tooltip>
                  <a-tooltip content="跳过数量" background-color="#152e53">
                    <div class="box box-3">{{ item.hasItem.skip }}</div>
                  </a-tooltip>
                </div>
              </template>
            </a-statistic>
          </a-col>
        </a-row>
      </a-card>
    </a-spin>
  </div>
</template>

<script lang="ts" setup>
import {computed} from 'vue';
import {useI18n} from 'vue-i18n';
import useThemes from '@/hooks/themes';


const props = defineProps({
  reportDetail: Object,
});

const {t} = useI18n();
const {isDark} = useThemes();
const renderData = computed(() => [
  {
    title: t('report.reportOverview.executionDuration'),
    value: props.reportDetail.executionDuration / 1000,
    prefix: {
      icon: 'icon-clock-circle',
      background: isDark.value ? '#593E2F' : '#FFE4BA',
      iconColor: isDark.value ? '#F29A43' : '#F77234',
    },
    suffix: '秒',
    hasItem: false
  },
  {
    title: t('report.reportOverview.sceneNumber'),
    value: props.reportDetail.sceneNumber,
    prefix: {
      icon: 'icon-mind-mapping',
      background: isDark.value ? '#3D5A62' : '#E8FFFB',
      iconColor: isDark.value ? '#6ED1CE' : '#33D1C9',
    },
    suffix: '个',
    hasItem: false
  },
  {
    title: t('report.reportOverview.stepNumber'),
    value: props.reportDetail.stepNumber,
    prefix: {
      icon: 'icon-ordered-list',
      background: isDark.value ? '#354276' : '#E8F3FF',
      iconColor: isDark.value ? '#4A7FF7' : '#165DFF',
    },
    suffix: '个',
    hasItem: {
      success: props.reportDetail.stepSuccessNumber,
      error: props.reportDetail.stepErrorNumber,
      skip: props.reportDetail.stepSkipNumber,
    }
  },
  {
    title: t('report.reportOverview.assertNumber'),
    value: props.reportDetail.assertNumber,
    prefix: {
      icon: 'icon-check-circle',
      background: isDark.value ? '#3F385E' : '#F5E8FF',
      iconColor: isDark.value ? '#8558D3' : '#722ED1',
    },
    suffix: '个',
    hasItem: {
      success: props.reportDetail.assertSuccessNumber,
      error: props.reportDetail.assertErrorNumber,
      skip: props.reportDetail.assertSkipNumber,
    }
  },
]);
</script>

<style scoped lang="less">
:deep(.arco-statistic) {
  .arco-statistic-title {
    color: rgb(var(--gray-10));
    font-weight: bold;
  }

  .arco-statistic-value {
    display: flex;
    align-items: center;
  }
}

.statistic-prefix {
  display: inline-block;
  width: 32px;
  height: 32px;
  margin-right: 8px;
  color: var(--color-white);
  font-size: 16px;
  line-height: 32px;
  text-align: center;
  vertical-align: middle;
  border-radius: 6px;
}


.boxes-container {
  display: flex;
  justify-content: space-around;
  gap: 1px;
  padding: 0 5px;
}

.box {
  width: 25px;
  height: 25px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 0.9rem;
  color: #1d2129;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
}

.box-1 {
  background-color: #a8e6cf; /* 浅绿色 */
}

.box-2 {
  background-color: #ffaaa5; /* 淡红色 */
}

.box-3 {
  background-color: #d9d9d9; /* 浅灰色 */
}
</style>
