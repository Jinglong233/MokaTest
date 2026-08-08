<template>
  <a-space direction="vertical" size="large" fill>
    <!-- 频率选择 -->
    <a-form-item>
      <a-radio-group v-model="frequency" type="button">
        <a-radio value="second">每秒</a-radio>
        <a-radio value="minute">每分钟</a-radio>
        <a-radio value="hourly">每小时</a-radio>
        <a-radio value="daily">每天</a-radio>
        <a-radio value="weekly">每周</a-radio>
        <a-radio value="monthly">每月</a-radio>
      </a-radio-group>
    </a-form-item>

    <!-- 每分钟配置 -->
    <a-form-item v-if="frequency === 'minute'" label="秒">
      <a-input-number
          v-model="config.second"
          :min="0"
          :max="59"
          placeholder="选择秒"
          style="width: 200px"
      >
        <template #suffix>秒</template>
      </a-input-number>
    </a-form-item>

    <!-- 每小时配置 -->
    <a-form-item v-if="frequency === 'hourly'" label="分钟">
      <a-input-number
          v-model="config.minute"
          :min="0"
          :max="59"
          placeholder="选择分钟"
          style="width: 200px"
      >
        <template #suffix>分</template>
      </a-input-number>
    </a-form-item>

    <!-- 每天配置 -->
    <a-form-item v-if="frequency === 'daily'" label="时间">
      <a-time-picker
          v-model="config.time"
          format="HH:mm"
          placeholder="选择时间"
          :allow-clear="false"
          style="width: 200px"
      />
    </a-form-item>

    <!-- 每周配置 -->
    <template v-if="frequency === 'weekly'">
      <a-form-item label="星期">
        <a-checkbox-group v-model="config.daysOfWeek">
          <a-checkbox value="1">周一</a-checkbox>
          <a-checkbox value="2">周二</a-checkbox>
          <a-checkbox value="3">周三</a-checkbox>
          <a-checkbox value="4">周四</a-checkbox>
          <a-checkbox value="5">周五</a-checkbox>
          <a-checkbox value="6">周六</a-checkbox>
          <a-checkbox value="0">周日</a-checkbox>
        </a-checkbox-group>
      </a-form-item>
      <a-form-item label="时间">
        <a-time-picker
            v-model="config.time"
            format="HH:mm"
            placeholder="选择时间"
            :allow-clear="false"
            style="width: 200px"
        />
      </a-form-item>
    </template>

    <!-- 每月配置 -->
    <template v-if="frequency === 'monthly'">
      <a-form-item label="日期">
        <a-select
            v-model="config.dayOfMonth"
            placeholder="选择日期"
            style="width: 200px"
        >
          <a-option
              v-for="day in 31"
              :key="day"
              :value="day"
          >
            {{ day }} 号
          </a-option>
        </a-select>
      </a-form-item>
      <a-form-item label="时间">
        <a-time-picker
            v-model="config.time"
            format="HH:mm"
            placeholder="选择时间"
            :allow-clear="false"
            style="width: 200px"
        />
      </a-form-item>
    </template>
    <div class="result-section">
      <a-form-item label="Cron 表达式">
        <a-input
            :model-value="cronExpression"
            readonly
            style="width: 100%"
        >
          <!--          <template #append>
                      <a-button type="primary" @click="copyCron">
                        <template #icon>
                          <icon-copy/>
                        </template>
                        复制
                      </a-button>
                    </template>-->
        </a-input>
      </a-form-item>

      <a-alert type="info" :show-icon="false">
        <template #icon>
          <icon-info-circle/>
        </template>
        {{ cronDescription }}
      </a-alert>
    </div>

    <!-- 格式说明 -->
    <a-collapse :bordered="false">
      <a-collapse-item header="Cron 表达式格式说明">
        <div class="format-info">
          <p><strong>标准格式：</strong>秒 分 时 日 月 星期</p>
          <ul>
            <li><code>*</code> - 表示任意值</li>
            <li><code>?</code> - 用于日和星期字段，表示不指定值</li>
            <li><code>-</code> - 表示范围，如 1-5</li>
            <li><code>,</code> - 表示列表，如 1,3,5</li>
            <li><code>/</code> - 表示增量，如 */5 表示每5个单位</li>
          </ul>
          <p><strong>示例：</strong></p>
          <ul>
            <li><code>* * * * * *</code> - 每秒执行一次</li>
            <li><code>0 0 0 * * *</code> - 每天 00:00:00 执行</li>
            <li><code>0 0 */2 * * *</code> - 每2小时执行一次</li>
            <li><code>0 0 9 * * 1,3,5</code> - 周一到周五 09:00:00 执行</li>
          </ul>
        </div>
      </a-collapse-item>
    </a-collapse>
  </a-space>
</template>

<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {Message} from '@arco-design/web-vue'
import {IconCopy, IconInfoCircle} from '@arco-design/web-vue/es/icon'

// 类型定义
type Frequency = 'second' | 'minute' | 'hourly' | 'daily' | 'weekly' | 'monthly'

interface CronConfig {
  second: number
  minute: number
  time: string
  daysOfWeek: string[]
  dayOfMonth: number
}

interface Props {
  modelValue?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '0 0 9 * * *'
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

// 响应式数据
const frequency = ref<Frequency>('daily')
const config = ref<CronConfig>({
  second: 0,
  minute: 0,
  time: '09:00',
  daysOfWeek: ['1'],
  dayOfMonth: 1
})

const isUpdatingFromProp = ref(false)

// 生成 Cron 表达式
const cronExpression = computed<string>(() => {
  switch (frequency.value) {
    case 'second':
      return '* * * * * *'

    case 'minute':
      return `${config.value.second} * * * * *`

    case 'hourly':
      return `0 ${config.value.minute} * * * *`

    case 'daily': {
      const [hour, minute] = config.value.time.split(':')
      return `0 ${minute} ${hour} * * *`
    }

    case 'weekly': {
      if (config.value.daysOfWeek.length === 0) return '0 0 0 * * *'
      const [hour, minute] = config.value.time.split(':')
      const days = config.value.daysOfWeek.sort().join(',')
      return `0 ${minute} ${hour} * * ${days}`
    }

    case 'monthly': {
      const [hour, minute] = config.value.time.split(':')
      return `0 ${minute} ${hour} ${config.value.dayOfMonth} * *`
    }

    default:
      return '* * * * * *'
  }
})

// 生成人类可读描述
const cronDescription = computed<string>(() => {
  switch (frequency.value) {
    case 'second':
      return '每秒执行一次'

    case 'minute':
      return `每分钟的第 ${config.value.second} 秒执行`

    case 'hourly':
      return `每小时的第 ${config.value.minute} 分钟执行`

    case 'daily':
      return `每天 ${config.value.time} 执行`

    case 'weekly': {
      if (config.value.daysOfWeek.length === 0) return '请选择至少一天'
      const dayNames: Record<string, string> = {
        '0': '周日',
        '1': '周一',
        '2': '周二',
        '3': '周三',
        '4': '周四',
        '5': '周五',
        '6': '周六'
      }
      const days = config.value.daysOfWeek
          .sort()
          .map(d => dayNames[d])
          .join('、')
      return `每周 ${days} 的 ${config.value.time} 执行`
    }

    case 'monthly':
      return `每月 ${config.value.dayOfMonth} 号 ${config.value.time} 执行`

    default:
      return ''
  }
})

const parseCronExpression = (cron: string): void => {
  const parts = cron.trim().split(/\s+/)
  if (parts.length !== 6) return

  const [second, minute, hour, dayOfMonth, month, dayOfWeek] = parts

  // 每秒：* * * * * *
  if (second === '*' && minute === '*' && hour === '*' && dayOfMonth === '*' && dayOfWeek === '*') {
    frequency.value = 'second'
    return
  }

  // 每分钟：30 * * * * * (第30秒执行)
  if (second !== '*' && minute === '*' && hour === '*' && dayOfMonth === '*' && dayOfWeek === '*') {
    frequency.value = 'minute'
    config.value.second = parseInt(second) || 0
    return
  }

  // 每小时：0 30 * * * * (第30分钟执行)
  if (second !== '*' && minute !== '*' && hour === '*' && dayOfMonth === '*' && dayOfWeek === '*') {
    frequency.value = 'hourly'
    config.value.minute = parseInt(minute) || 0
    return
  }

  // 每周：0 0 9 * * 1,3,5 (周一、三、五 9:00)
  if (dayOfWeek !== '*' && dayOfMonth === '*') {
    frequency.value = 'weekly'
    const h = parseInt(hour) || 0
    const m = parseInt(minute) || 0
    config.value.time = `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`
    config.value.daysOfWeek = dayOfWeek.split(',')
    return
  }

  // 每月：0 0 9 15 * * (每月15号 9:00)
  if (dayOfMonth !== '*' && dayOfWeek === '*') {
    frequency.value = 'monthly'
    const h = parseInt(hour) || 0
    const m = parseInt(minute) || 0
    config.value.time = `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`
    config.value.dayOfMonth = parseInt(dayOfMonth) || 1
    return
  }

  // 每天：0 0 9 * * * (每天 9:00)
  if (dayOfMonth === '*' && dayOfWeek === '*' && hour !== '*') {
    frequency.value = 'daily'
    const h = parseInt(hour) || 0
    const m = parseInt(minute) || 0
    config.value.time = `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`
    return
  }
}

watch(
    () => props.modelValue,
    (newValue) => {
      if (newValue && newValue !== cronExpression.value) {
        isUpdatingFromProp.value = true
        parseCronExpression(newValue)
        isUpdatingFromProp.value = false
      }
    },
    {immediate: true}
)

watch(cronExpression, (newValue) => {
  if (!isUpdatingFromProp.value) {
    emit('update:modelValue', newValue)
  }
})

// 复制到剪贴板
const copyCron = async (): Promise<void> => {
  try {
    await navigator.clipboard.writeText(cronExpression.value)
    Message.success('Cron 表达式已复制到剪贴板')
  } catch (err) {
    Message.error('复制失败，请手动复制')
  }
}
</script>

<style scoped>
.cron-generator {
  max-width: 800px;
  margin: 40px auto;
  padding: 20px;
}

.result-section {
  background: linear-gradient(135deg, #f5f7fa 0%, #f0f2f5 100%);
  padding: 24px;
  border-radius: 8px;
  margin-top: 8px;
}

.result-section :deep(.arco-form-item) {
  margin-bottom: 16px;
}

.result-section :deep(.arco-alert) {
  background: white;
  border: 1px solid #e5e7eb;
}

.format-info {
  line-height: 1.8;
}

.format-info p {
  margin-bottom: 12px;
}

.format-info ul {
  margin: 8px 0;
  padding-left: 24px;
}

.format-info li {
  margin: 4px 0;
}

.format-info code {
  background: #f1f3f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 13px;
  color: #e03e2d;
}

:deep(.arco-form-item-label-col) {
  font-weight: 500;
}

:deep(.arco-checkbox-group) {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

:deep(.arco-radio-button) {
  min-width: 80px;
  text-align: center;
}
</style>
