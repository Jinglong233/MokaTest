<template>
  <div class="container" style="width: 100%;">
    <div class="report-detail-header">
      <Breadcrumb
          :items="['menu.list.reportList', 'menu.report.reportDetail']"
      />
      <a-space direction="vertical" :size="12" fill>
        <a-space direction="vertical" :size="16" fill>
          <a-card :title="$t('menu.report.reportOverview')">
          <template #title>
            <a-space align="center">
              <a-button type="text" @click="router.back()">
                <template #icon>
                  <icon-undo/>
                </template>
                返回
              </a-button>
              <icon-user style="color: #3cc071"/>
              执行者：
              <a-avatar :size="32">
                <img
                    alt="avatar"
                    src="https://p1-arco.byteimg.com/tos-cn-i-uwbnlip3yd/3ee5f13fb09879ecb5185e440cef6eb9.png~tplv-uwbnlip3yd-webp.webp"
                />
              </a-avatar>
              「{{ reportInfo.executionUserName }}」
              任务名称：[{{ reportInfo.planName }}]
              <icon-calendar-clock style="color: #0960bd"/>
              开始时间：【{{ reportInfo.createTime }}】
              结束时间：【{{ reportInfo.endTime }}】
            </a-space>

          </template>
          <template #extra>
            <a-button v-if="sceneListOverview['failed'] && Object.values(sceneListOverview['failed']).length > 0 "
                      type="primary" @click="failSceneRetry()">失败场景执行
            </a-button>
          </template>
          <DataOverview :report-detail="reportInfo"/>
        </a-card>
      </a-space>
    </a-space>
    </div>
    <a-row class="report-detail-body">
      <a-col :span="4" style="height: 100%">
        <a-card class="detail-card" title="场景结果" style="height: 100%">
          <a-tabs default-active-key="1">
            <a-tab-pane key="1" title="全部">
              <a-scrollbar type="track" :outer-style="{ height: '100%' }" style="height: 100%;overflow: auto">
                <a-card v-for="(value,key) in sceneListOverview['all']" :key="key"
                        style="cursor: pointer;"
                        :title="`场景名称：${key.split('_')[1]}`" :class="{'active':selectKey== key}"
                        @click="selectScene(key)">
                  <template #extra>
                    <a-button type="text" @click="goSceneDetail(key.split('_')[0])">
                      详情
                    </a-button>
                  </template>
                  <a-space>
                    <a-progress
                        :percent="calcPercent(value.statusSummary.SUCCESS, value.statusSummary.SUCCESS + value.statusSummary.FAILURE + value.statusSummary.SKIPPED)"
                        color="#3cc071"
                        animation
                        type="circle"
                        style="margin-bottom: 20px;"
                    />
                    <a-progress
                        :percent="calcPercent(value.statusSummary.FAILURE, value.statusSummary.SUCCESS + value.statusSummary.FAILURE + value.statusSummary.SKIPPED)"
                        color="red"
                        animation
                        type="circle"
                        style="margin-bottom: 20px;"
                    />
                    <a-progress
                        :percent="calcPercent(value.statusSummary.SKIPPED, value.statusSummary.SUCCESS + value.statusSummary.FAILURE + value.statusSummary.SKIPPED)"
                        color="gray"
                        animation
                        type="circle"
                        style="margin-bottom: 20px;"
                    />
                  </a-space>
                </a-card>
              </a-scrollbar>
            </a-tab-pane>
            <a-tab-pane key="2" title="成功">
              <a-scrollbar type="track" :outer-style="{ height: '100%' }" style="height: 100%;overflow: auto">
                <a-card v-for="(value,key) in sceneListOverview['success']" :key="key"
                        style="cursor: pointer;"
                        :title="`场景名称：${key.split('_')[1]}`" :class="{'active':selectKey== key}"
                        @click="selectScene(key)">
                  <template #extra>
                    <a-button type="text" @click="goSceneDetail(key.split('_')[0])">
                      详情
                    </a-button>
                  </template>
                  <a-space>
                    <a-progress
                        :percent="calcPercent(value.statusSummary.SUCCESS, value.statusSummary.SUCCESS + value.statusSummary.FAILURE + value.statusSummary.SKIPPED)"
                        color="#3cc071"
                        animation
                        type="circle"
                        style="margin-bottom: 20px;"
                    />
                    <a-progress
                        :percent="calcPercent(value.statusSummary.FAILURE, value.statusSummary.SUCCESS + value.statusSummary.FAILURE + value.statusSummary.SKIPPED)"
                        color="red"
                        animation
                        type="circle"
                        style="margin-bottom: 20px;"
                    />
                    <a-progress
                        :percent="calcPercent(value.statusSummary.SKIPPED, value.statusSummary.SUCCESS + value.statusSummary.FAILURE + value.statusSummary.SKIPPED)"
                        color="gray"
                        animation
                        type="circle"
                        style="margin-bottom: 20px;"
                    />
                  </a-space>
                </a-card>
              </a-scrollbar>
            </a-tab-pane>
            <a-tab-pane key="3" title="失败">
              <a-scrollbar type="track" :outer-style="{ height: '100%' }" style="height: 100%;overflow: auto">
                <a-card v-for="(value,key) in sceneListOverview['failed']" :key="key"
                        style="cursor: pointer;"
                        :title="`场景名称：${key.split('_')[1]}`" :class="{'active':selectKey== key}"
                        @click="selectScene(key)">
                  <template #extra>
                    <a-button type="text" @click="goSceneDetail(key.split('_')[0])">
                      详情
                    </a-button>
                  </template>
                  <a-space>
                    <a-progress
                        :percent="calcPercent(value.statusSummary.SUCCESS, value.statusSummary.SUCCESS + value.statusSummary.FAILURE + value.statusSummary.SKIPPED)"
                        color="#3cc071"
                        animation
                        type="circle"
                        style="margin-bottom: 20px;"
                    />
                    <a-progress
                        :percent="calcPercent(value.statusSummary.FAILURE, value.statusSummary.SUCCESS + value.statusSummary.FAILURE + value.statusSummary.SKIPPED)"
                        color="red"
                        animation
                        type="circle"
                        style="margin-bottom: 20px;"
                    />
                    <a-progress
                        :percent="calcPercent(value.statusSummary.SKIPPED, value.statusSummary.SUCCESS + value.statusSummary.FAILURE + value.statusSummary.SKIPPED)"
                        color="gray"
                        animation
                        type="circle"
                        style="margin-bottom: 20px;"
                    />
                  </a-space>
                </a-card>
              </a-scrollbar>
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </a-col>
      <a-col :span="20" style="height: 100%;">
        <a-card class="detail-card" style="height: 100%;width: 100%;" title="场景步骤" id="parentNode">
          <a-tabs default-active-key="1">
            <a-tab-pane key="1" title="全部">
              <a-scrollbar type="track" :outer-style="{ height: '100%' }" style="height: 100%;overflow: auto">
                <a-tree
                    style="width: 100%"
                    v-if="currentSelectSceneReport['all'] && currentSelectSceneReport['all'].length > 0"
                    :data="currentSelectSceneReport['all']"
                    :fieldNames="{
                    key: 'id',
                    title: 'title',
                    children: 'children',
                  }"
                    @select="showStepResult"
                    :blockNode="true"
                >
                  <template #title="el">
                    <a-card
                        :style="{
                          width: '100%',
                          boxSizing: 'border-box',
                          margin: '0',
                          backgroundColor: el.step.isDisable === 1 ? 'rgba(0, 0, 0, 0.1)' : ''
                        }"
                        :class="{
                        'active': currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id,
                        'success': el.status=== StepExecutionType.SUCCESS && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id),
                        'skipped': el.status=== StepExecutionType.SKIPPED && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id),
                        'failure': el.status === StepExecutionType.FAILURE && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id)
                      }"
                    >
                      <div style="display: flex;align-items: center;gap: 8px;width: 100%;">
                        <!-- 序号 -->
                        <div style="flex-shrink: 0; width: 32px;">
                          <a-badge :count="el.step.orderIndex"/>
                        </div>
                        <!-- 中间内容区域 -->
                        <div style="display: flex;align-items: center;gap: 8px;flex: 1;min-width: 0;">
                          <a-avatar
                              shape="square"
                              :style="{ backgroundColor: '#165DFF', flexShrink: 0 }"
                              :size="28">
                            <icon-code-sandbox/>
                          </a-avatar>

                          <a-tag color="cyan" size="large" style="flex-shrink: 0;">
                            {{ getStepTypeChinese(el.stepType) }}
                          </a-tag>

                          <!-- 文本区域 -->
                          <a-typography-text
                              style="flex: 1;min-width: 0;overflow: hidden;text-overflow: ellipsis;white-space: nowrap;">
                            {{ el.step.stepName }}
                          </a-typography-text>
                        </div>
                        <!-- 状态图标 -->
                        <div style="flex-shrink: 0; width: 24px; display: flex; justify-content: center;">
                          <icon-check-circle
                              style="color: #3cc071; font-size: 20px;"
                              v-if="getStepStatus(el) === StepExecutionType.SUCCESS"
                          />
                          <icon-minus-circle
                              style="color: #c2c8d5; font-size: 20px;"
                              v-if="getStepStatus(el) === StepExecutionType.SKIPPED"
                          />
                          <icon-close-circle
                              style="color: red; font-size: 20px;"
                              v-else-if="getStepStatus(el) === StepExecutionType.FAILURE"
                          />
                        </div>
                      </div>
                    </a-card>
                  </template>
                </a-tree>
              </a-scrollbar>
            </a-tab-pane>
            <a-tab-pane key="2" title="成功">
              <a-scrollbar type="track" :outer-style="{ height: '100%' }" style="height: 100%;overflow: auto">
                <a-tree
                    v-if="currentSelectSceneReport['success'] && currentSelectSceneReport['success'].length > 0"
                    :data="currentSelectSceneReport['success']"
                    :fieldNames="{
                    key: 'id',
                    title: 'title',
                    children: 'children',
                  }"
                    @select="showStepResult"
                    :blockNode="true"
                >
                  <template #title="el">
                    <a-card
                        :style="{
                          width: '100%',
                          boxSizing: 'border-box',
                          margin: '0',
                          backgroundColor: el.step.isDisable === 1 ? 'rgba(0, 0, 0, 0.1)' : ''
                        }"
                        :class="{
                        'active': currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id,
                       'success': el.status=== StepExecutionType.SUCCESS && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id),
                       'skipped': el.status=== StepExecutionType.SKIPPED && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id),
                        'failure': el.status === StepExecutionType.FAILURE && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id)
                      }"
                    >
                      <div style="display: flex;align-items: center;gap: 8px;width: 100%;">
                        <!-- 序号 -->
                        <div style="flex-shrink: 0; width: 32px;">
                          <a-badge :count="el.step.orderIndex"/>
                        </div>
                        <!-- 中间内容区域 -->
                        <div style="display: flex;align-items: center;gap: 8px;flex: 1;min-width: 0;">
                          <a-avatar shape="square" :style="{ marginRight: '8px', backgroundColor: '#165DFF' }"
                                    :size="28">
                            <icon-code-sandbox/>
                          </a-avatar>
                          <a-tag color="cyan" size="large">
                            {{ getStepTypeChinese(el.step.stepType) }}
                          </a-tag>
                          <!-- 文本区域 -->
                          <a-typography-text> {{ el.step.stepName }}</a-typography-text>
                        </div>
                        <!-- 状态图标 -->
                        <div style="flex-shrink: 0; width: 24px; display: flex; justify-content: center;">
                          <icon-check-circle style="color: #3cc071; font-size: 20px;"
                                             v-if="getStepStatus(el) === StepExecutionType.SUCCESS"/>
                          <icon-minus-circle style="color: #c2c8d5; font-size: 20px;"
                                             v-if="getStepStatus(el) === StepExecutionType.SKIPPED"/>
                          <icon-close-circle style="color: red; font-size: 20px;"
                                             v-else-if="getStepStatus(el) === StepExecutionType.FAILURE"/>
                        </div>
                      </div>
                    </a-card>
                  </template>

                </a-tree>
              </a-scrollbar>
            </a-tab-pane>
            <a-tab-pane key="3" title="失败">
              <a-scrollbar type="track" :outer-style="{ height: '100%' }" style="height: 100%;overflow: auto">
                <a-tree
                    v-if="currentSelectSceneReport['failure'] && currentSelectSceneReport['failure'].length > 0"
                    :data="currentSelectSceneReport['failure']"
                    :fieldNames="{
                    key: 'id',
                    title: 'title',
                    children: 'children',
                  }"
                    @select="showStepResult"
                    :blockNode="true"
                >
                  <template #title="el">
                    <a-card
                        :style="{
                          width: '100%',
                          boxSizing: 'border-box',
                          margin: '0',
                          backgroundColor: el.step.isDisable === 1 ? 'rgba(0, 0, 0, 0.1)' : ''
                        }"
                        :class="{
                        'active': currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id,
                        'success': el.status=== StepExecutionType.SUCCESS && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id),
                        'skipped': el.status=== StepExecutionType.SKIPPED && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id),
                        'failure': el.status === StepExecutionType.FAILURE && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id)
                      }"
                    >
                      <div style="display: flex;align-items: center;gap: 8px;width: 100%;">
                        <!-- 序号 -->
                        <div style="flex-shrink: 0; width: 32px;">
                          <a-badge :count="el.step.orderIndex"/>
                        </div>
                        <!-- 中间内容区域 -->
                        <div
                            style="display: flex;align-items: center;gap: 8px;flex: 1;min-width: 0;">
                          <a-avatar
                              shape="square"
                              :style="{ backgroundColor: '#165DFF', flexShrink: 0 }"
                              :size="28"
                          >
                            <icon-code-sandbox/>
                          </a-avatar>

                          <a-tag color="cyan" size="large" style="flex-shrink: 0;">
                            {{ getStepTypeChinese(el.stepType) }}
                          </a-tag>

                          <!-- 文本区域 -->
                          <a-typography-text
                              style="flex: 1;min-width: 0;overflow: hidden;text-overflow: ellipsis;white-space: nowrap;">
                            {{ el.step.stepName }}
                          </a-typography-text>
                        </div>
                        <!-- 状态图标 -->
                        <div style="flex-shrink: 0; width: 24px; display: flex; justify-content: center;">
                          <icon-check-circle
                              style="color: #3cc071; font-size: 20px;"
                              v-if="getStepStatus(el) === StepExecutionType.SUCCESS"
                          />
                          <icon-minus-circle
                              style="color: #c2c8d5; font-size: 20px;"
                              v-if="getStepStatus(el) === StepExecutionType.SKIPPED"
                          />
                          <icon-close-circle
                              style="color: red; font-size: 20px;"
                              v-else-if="getStepStatus(el) === StepExecutionType.FAILURE"
                          />
                        </div>
                      </div>
                    </a-card>
                  </template>

                </a-tree>
              </a-scrollbar>
            </a-tab-pane>
            <a-tab-pane key="4" title="跳过">
              <a-scrollbar type="track" :outer-style="{ height: '100%' }" style="height: 100%;overflow: auto">

                <a-tree
                    v-if="currentSelectSceneReport['skipped'] && currentSelectSceneReport['skipped'].length > 0"
                    :data="currentSelectSceneReport['skipped']"
                    :fieldNames="{
                                key: 'id',
                                title: 'title',
                                children: 'children',
                              }"
                    @select="showStepResult"
                    :block-node="true"
                >
                  <template #title="el">
                    <a-card
                        :style="{
                          width: '100%',
                          boxSizing: 'border-box',
                          margin: '0',
                          backgroundColor: el.step.isDisable === 1 ? 'rgba(0, 0, 0, 0.1)' : ''
                        }"
                        :class="{
                        'active': currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id,
                        'success': el.status=== StepExecutionType.SUCCESS && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id),
                        'skipped': el.status=== StepExecutionType.SKIPPED && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id),
                        'failure': el.status === StepExecutionType.FAILURE && !(currentSelectStep && currentSelectStep.step && currentSelectStep.step.id === el.step.id)
                      }"
                    >
                      <div style="display: flex;align-items: center;gap: 8px;width: 100%;">
                        <!-- 序号 -->
                        <div style="flex-shrink: 0; width: 32px;">
                          <a-badge :count="el.step.orderIndex"/>
                        </div>
                        <!-- 中间内容区域 -->
                        <div
                            style="display: flex;align-items: center;gap: 8px;flex: 1;min-width: 0;">
                          <a-avatar
                              shape="square"
                              :style="{ backgroundColor: '#165DFF', flexShrink: 0 }"
                              :size="28"
                          >
                            <icon-code-sandbox/>
                          </a-avatar>

                          <a-tag color="cyan" size="large" style="flex-shrink: 0;">
                            {{ getStepTypeChinese(el.stepType) }}
                          </a-tag>

                          <!-- 文本区域 -->
                          <a-typography-text
                              style="flex: 1;min-width: 0;overflow: hidden;text-overflow: ellipsis;white-space: nowrap;">
                            {{ el.step.stepName }}
                          </a-typography-text>
                        </div>
                        <!-- 状态图标 -->
                        <div style="flex-shrink: 0; width: 24px; display: flex; justify-content: center;">
                          <icon-check-circle
                              style="color: #3cc071; font-size: 20px;"
                              v-if="getStepStatus(el) === StepExecutionType.SUCCESS"
                          />
                          <icon-minus-circle
                              style="color: #c2c8d5; font-size: 20px;"
                              v-if="getStepStatus(el) === StepExecutionType.SKIPPED"
                          />
                          <icon-close-circle
                              style="color: red; font-size: 20px;"
                              v-else-if="getStepStatus(el) === StepExecutionType.FAILURE"
                          />
                        </div>
                      </div>
                    </a-card>
                  </template>
                </a-tree>
              </a-scrollbar>
            </a-tab-pane>
            <StepDebugResultDraw :step-result="currentSelectStep" :visible="drawerVisible" @cancel="closeDraw"/>
            <ApiStepDebugResult
                :visible="apiDrawerVisible"
                :step-result="currentApiStepResult"
                @update:visible="apiDrawerVisible = $event"
            />
          </a-tabs>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'ReportDetail' };
</script>

<script setup lang="ts">
import DataOverview from "@/views/report/report-detail/components/data-overview.vue";
import {useI18n} from 'vue-i18n';
import {onMounted, onUnmounted, ref, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import {Report} from "@/types/domain/Report";
import {getReportDetail, reRunFailScene} from "@/api/MyApi/report";
import {getStepTypeChinese} from "@/types/enum/StepType";
import StepDebugResultDraw from "@/views/scene/component/StepDebugResultDraw.vue";
import ApiStepDebugResult from "@/views/scene/component/ApiStepDebugResult.vue";
import {StepExecutionType} from "@/types/enum/StepExecutionType";
import {Message, Modal} from "@arco-design/web-vue";
import {PlanCategory} from "@/types/enum/plan/PlanCategory";

const {t} = useI18n();


const reportInfo = ref<Report>({});

const currentSelectSceneReport = ref<any>({});

const currentSelectStep = ref({});

const sceneResultList = ref([]);

const selectKey = ref<string>('');

// 步骤结果抽屉显示框
const drawerVisible = ref(false);
// API 步骤结果抽屉显示框
const apiDrawerVisible = ref(false);
const currentApiStepResult = ref<any>({});
// 左侧场景概览数据
const sceneListOverview = ref<any>({});
const route = useRoute();
const router = useRouter();

// 轮询定时器
let pollingTimer: ReturnType<typeof setInterval> | null = null;

// 加载报告详情
const loadReportDetail = async () => {
  if (!route.params.reportId) {
    return;
  }
  const result = await getReportDetail(route.params.reportId);
  reportInfo.value = result.data;

  if (reportInfo.value.scenes) {
    sceneResultList.value = JSON.parse(reportInfo.value.scenes);
    // 如果没有已选中的场景，默认选中第一个
    if (!selectKey.value) {
      selectKey.value = Object.keys(JSON.parse(reportInfo.value.scenes))[0];
    }
    currentSelectSceneReport.value = classificationProcessReportData()
    sceneListOverview.value = sceneResultListEnhanced(reportInfo.value.scenes);
  }
};

// 启动轮询
const startPolling = () => {
  if (pollingTimer) return;
  pollingTimer = setInterval(() => {
    loadReportDetail();
  }, 3000); // 每3秒刷新一次
};

// 停止轮询
const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer);
    pollingTimer = null;
  }
};

onMounted(async () => {
  // 获取路由参数
  if (!route.params.reportId) {
    router.back();
    return;
  }

  // 首次加载报告详情
  await loadReportDetail();

  // 如果状态是"执行中"，启动轮询
  if (reportInfo.value.status === 0) {
    startPolling();
  }
})

// 监听状态变化，执行完成后停止轮询
watch(() => reportInfo.value.status, (newVal) => {
  if (newVal === 1 && pollingTimer) {
    stopPolling();
  }
});

// 页面卸载时清除轮询
onUnmounted(() => {
  stopPolling();
})

const showStepResult = (stepResultId: any) => {
  const findStepResult = (steps: any[]): any => {
    for (const stepResult of steps) {
      if (stepResult.id == stepResultId) {
        return stepResult;
      }
      if (stepResult.children && stepResult.children.length > 0) {
        const found = findStepResult(stepResult.children);
        if (found) return found;
      }
    }
    return null;
  };

  const foundStep = findStepResult(currentSelectSceneReport.value['all']);

  if (foundStep) {
    // 判断步骤如果是跳过状态就禁止执行后续步骤
    if (getStepStatus(foundStep) === "SKIPPED") {
      Message.warning({
        content: '跳过步骤没执行结果',
        duration: 1000
      })
      return;
    }

    // API/SQL 步骤使用 ApiStepDebugResult（与 API 测试页/场景实时调试保持一致）
    if (foundStep.step?.stepType === 'API_REQUEST' || foundStep.step?.stepType === 'SQL') {
      currentApiStepResult.value = foundStep;
      apiDrawerVisible.value = true;
    } else {
      currentSelectStep.value = foundStep;
      drawerVisible.value = true;
    }
  } else {
    console.warn(`StepResult with id ${stepResultId} not found`);
  }
};

const closeDraw = () => {
  drawerVisible.value = false;
  apiDrawerVisible.value = false;
  currentSelectStep.value = {};
  currentApiStepResult.value = {};
}

// 计算进度百分比，返回 Number 并处理除零
const calcPercent = (count: number, total: number): number => {
  if (!total) return 0;
  return Number((count / total).toFixed(2));
};

/**
 * 将报告结果做进一步处理
 * 1. 将数据处理成arco design 树组件可以解析的数据结构。原数据也都保存在dataRef字段中
 * @param stepResults
 */
const transformToTreeData = (stepResults: any) => {
  if (!stepResults || !Array.isArray(stepResults)) {
    return [];
  }

  const processStepResult = (stepResult: any) => {
    const step = stepResult.step;

    // 构建树节点，保留所有原始数据
    const treeNode = {
      // Arco Tree 需要的字段
      id: step.id.toString(),
      title: step.stepName,
      children: [],

      // 保留原始数据
      ...stepResult,
      dataRef: stepResult, // 将原始数据保存在 dataRef 中方便访问

      // 添加额外字段便于使用
      orderIndex: step.orderIndex,
      stepType: step.stepType,
      iterations: stepResult.iterations,
    };

    // 处理子步骤
    if (stepResult.children && stepResult.children.length > 0) {
      treeNode.children = stepResult.children.map(processStepResult);
    } else if (step.childrenSteps && step.childrenSteps.length > 0) {
      // 如果 childrenSteps 有数据但 children 没有，也进行处理
      treeNode.children = step.childrenSteps.map((childStep: any) => {
        return {
          id: childStep.id.toString(),
          title: childStep.stepName,
          orderIndex: childStep.orderIndex,
          stepType: childStep.stepType,
          status: 'SKIPPED', // 默认状态，根据实际情况调整
          step: childStep,
          dataRef: {step: childStep, status: 'SKIPPED'}
        };
      });
    }

    return treeNode;
  };

  return stepResults.map(processStepResult);
};

/**
 * 分类处理报告数据
 */
const classificationProcessReportData = () => {

  const result = {
    'all': [],
    'success': [],
    'failure': [],
    'skipped': []
  }
  result.all = transformToTreeData(JSON.parse(reportInfo.value.scenes)[selectKey.value]) as any[];
  result.success = transformToTreeData(filterSuccessfulSteps(JSON.parse(reportInfo.value.scenes)[selectKey.value])) as any[];
  result.failure = transformToTreeData(filterFailedSteps(JSON.parse(reportInfo.value.scenes)[selectKey.value])) as any[];
  result.skipped = transformToTreeData(filterSkippedSteps(JSON.parse(reportInfo.value.scenes)[selectKey.value])) as any[];
  return result;
}


/**
 * 获取成功的步骤列表
 * @param steps
 */
const filterSuccessfulSteps = (steps: any) => {
  if (!Array.isArray(steps)) {
    return [];
  }

  /**
   * 递归检查步骤是否完全成功
   * @param {Object} stepData 步骤数据
   * @returns {boolean} 是否完全成功
   */
  const isStepCompletelySuccessful = (stepData: any) => {

    // 判断是否有 iterations 且不为空
    if (stepData.iterations != null && Object.keys(stepData.iterations).length > 0) {
      // 遍历 stepData.iterations
      for (const [key, value] of Object.entries(stepData.iterations)) {
        // 检查步骤本身状态
        if (value.status !== 'SUCCESS') {
          return false;
        }
      }

      // 如果有子步骤，递归检查每个子步骤
      if (stepData.children && Array.isArray(stepData.children)) {
        return stepData.children.every(child => isStepCompletelySuccessful(child));
      }

      // 如果没有子步骤，只需要检查自身状态
      return true;
    } else {
      if (!stepData.result) {
        return false;
      }
      // 检查步骤本身状态
      if (stepData.result.status !== 'SUCCESS') {
        return false;
      }

      // 如果有子步骤，递归检查每个子步骤
      if (stepData.children && Array.isArray(stepData.children)) {
        return stepData.children.every(child => isStepCompletelySuccessful(child));
      }

      // 如果没有子步骤，只需要检查自身状态
      return true;
    }


  }

  // 过滤步骤数组，只保留完全成功的步骤
  return steps.filter(step => isStepCompletelySuccessful(step));
}


/**
 * 获取失败的步骤列表
 */
const filterFailedSteps = (steps: any) => {
  if (!Array.isArray(steps)) {
    return [];
  }

  /**
   * 递归检查步骤是否失败
   * @param {Object} stepData 步骤数据
   * @returns {boolean} 是否失败
   */
  const isStepFailed = (stepData: any) => {

    // 判断是否有 iterations 且不为空
    if (stepData.iterations != null && Object.keys(stepData.iterations).length > 0) {
      // 遍历 stepData.iterations
      for (const [key, value] of Object.entries(stepData.iterations)) {
        // 检查步骤本身状态
        if (value.status === 'FAILURE') {
          return true;
        }
      }

      // 如果有子步骤，递归检查是否有任何子步骤失败
      if (stepData.children && Array.isArray(stepData.children)) {
        return stepData.children.some(child => isStepFailed(child));
      }
      return false;
    } else {
      // 检查步骤本身状态
      if (!stepData.result) {
        // 没有 result 的步骤不算作失败，应视为跳过
        return false;
      }

      if (stepData.result.status === 'FAILURE') {
        return true;
      }

      // 如果有子步骤，递归检查每个子步骤
      if (stepData.children && Array.isArray(stepData.children)) {
        return stepData.children.some(child => isStepFailed(child));
      }

      // 如果没有子步骤，只需要检查自身状态
      return stepData.result.status === 'FAILURE';
    }


  }

  // 过滤步骤数组，找出包含失败情况的步骤
  return steps.filter(step => isStepFailed(step));
}


/**
 * 获取跳过的步骤列表
 */
const filterSkippedSteps = (steps: any) => {
  if (!Array.isArray(steps)) {
    return [];
  }

  /**
   * 递归检查步骤是否被跳过（且没有失败）
   * @param {Object} stepData 步骤数据
   * @returns {boolean} 是否被跳过且无失败
   */
  const isStepSkipped = (stepData: any) => {
    // 判断是否有 iterations 且不为空
    if (stepData.iterations != null && Object.keys(stepData.iterations).length > 0) {
      let hasSkipped = false;
      // 遍历 stepData.iterations
      for (const [key, value] of Object.entries(stepData.iterations)) {
        // 如果步骤本身失败，不算跳过
        if (value.status === 'FAILURE') {
          return false;
        }

        // 如果步骤本身被跳过
        if (value.status === 'SKIPPED') {
          return true;
        }
        // 如果没有跳过的 iteration，直接返回 false
        if (!hasSkipped) {
          return false;
        }

        // 如果有子步骤，检查是否有子步骤被跳过（且没有失败）
        if (stepData.children && Array.isArray(stepData.children)) {
          const hasSkippedChild = stepData.children.some(child => isStepSkipped(child));
          const hasFailedChild = stepData.children.some(child => hasFailedDescendant(child));
          return hasSkippedChild && !hasFailedChild;
        }

        return hasSkipped;
      }
    } else {
      // 新增逻辑：如果没有 result 且没有 iterations 或 iterations 为空，则视为跳过
      if (!stepData.result) {
        // 如果有子步骤，检查是否有子步骤被跳过（且没有失败）
        if (stepData.children && Array.isArray(stepData.children)) {
          const hasSkippedChild = stepData.children.some(child => isStepSkipped(child));
          const hasFailedChild = stepData.children.some(child => hasFailedDescendant(child));
          return hasSkippedChild && !hasFailedChild;
        }
        // 没有 result 且没有 children，算作跳过
        return true;
      }

      // 如果步骤本身失败，不算跳过
      if (stepData.result.status === 'FAILURE') {
        return false;
      }

      // 如果步骤本身被跳过
      if (stepData.result.status === 'SKIPPED') {
        // 如果有子步骤，检查是否有子步骤被跳过（且没有失败）
        if (stepData.children && Array.isArray(stepData.children)) {
          const hasSkippedChild = stepData.children.some(child => isStepSkipped(child));
          const hasFailedChild = stepData.children.some(child => hasFailedDescendant(child));
          return hasSkippedChild && !hasFailedChild;
        }
        return true;
      }

      // 如果有子步骤，检查是否有子步骤被跳过（且没有失败）
      if (stepData.children && Array.isArray(stepData.children)) {
        const hasSkippedChild = stepData.children.some(child => isStepSkipped(child));
        const hasFailedChild = stepData.children.some(child => hasFailedDescendant(child));
        return hasSkippedChild && !hasFailedChild;
      }
      return false;
    }


  }

  /**
   * 检查是否有失败的后代步骤
   */
  const hasFailedDescendant = (stepData: any) => {
    if (stepData.status === 'FAILURE') return true;
    if (stepData.result && stepData.result.status === 'FAILURE') return true;
    if (stepData.children && Array.isArray(stepData.children)) {
      return stepData.children.some(child => hasFailedDescendant(child));
    }
    return false;
  }

  return steps.filter(step => isStepSkipped(step));
}


/**
 * 获取汇总数据
 * @param stepResults
 */
const processStepDataEnhanced = (stepResults: any) => {
  const result = {};
  const temp = JSON.parse(stepResults)
  for (const [key, steps] of Object.entries(temp)) {
    let totalSteps = 0;
    let outerSteps = 0;
    let innerSteps = 0;
    const statusSummary = {
      SUCCESS: 0,
      FAILURE: 0,
      SKIPPED: 0
    };

    const countStatus = (status: any) => {
      if (statusSummary.hasOwnProperty(status)) {
        statusSummary[status]++;
      } else {
        statusSummary[status] = 1;
      }
    };

    // 递归处理步骤数据
    const processStepRecursively = (stepData: any, isOuter: boolean = false) => {
      totalSteps++;

      if (isOuter) {
        outerSteps++;
      } else {
        innerSteps++;
      }

      // 统计步骤状态
      countStatus(getStepStatus(stepData));

      // 递归处理子步骤
      if (stepData.children && Array.isArray(stepData.children)) {
        stepData.children.forEach((childStep: any) => {
          processStepRecursively(childStep, false);
        });
      }
    };

    // 处理外层步骤
    steps.forEach((stepData: any) => {
      processStepRecursively(stepData, true);
    });

    result[key] = {
      totalSteps,
      outerSteps,
      innerSteps,
      statusSummary
    };
  }

  return result;
}

/**
 * 分类处理汇总数据
 */
const sceneResultListEnhanced = (stepResults: any) => {
  const temp = processStepDataEnhanced(stepResults);


  const result = {
    all: {},
    failed: {},
    success: {}
  }
  result.all = temp;
  for (const [key, value] of Object.entries(temp)) {
    if (value.statusSummary.SUCCESS === value.totalSteps) {
      result.success[key] = value;
    } else {
      result.failed[key] = value;
    }
  }
  return result;
}


/**
 * 切换场景结果
 * @param sceneKey
 */
const selectScene = (sceneKey: any) => {
  selectKey.value = sceneKey;
  // currentSelectSceneReport.value = transformToTreeData(JSON.parse(reportInfo.value.scenes)[selectKey.value]);
  currentSelectSceneReport.value = classificationProcessReportData()
}

// 跳转场景编辑详情
const goSceneDetail = (sceneId: any) => {
  const isApi = reportInfo.value.reportCategory === PlanCategory.API;
  router.push({
    name: isApi ? 'ApiSceneList' : 'UiSceneList',
    query: {
      selectSceneId: sceneId
    }
  })
}

/**
 * 获取步骤状态，仅判断当前步骤，不考虑子步骤
 * 作用：用于处理步骤列表 右侧 的步骤执行状态图标显示
 * @param stepData 步骤数据
 * @returns 步骤状态
 */
const getStepStatus = (stepData: any) => {
  // 判断是否有 iterations 且不为空
  if (stepData.iterations != null && Object.keys(stepData.iterations).length > 0) {
    // 遍历 stepData.iterations
    for (const [key, value] of Object.entries(stepData.iterations)) {
      // 如果步骤本身失败，返回失败
      if (value.status === 'FAILURE') {
        return StepExecutionType.FAILURE;
      }

      // 如果步骤本身被跳过
      if (value.status === 'SKIPPED') {
        return StepExecutionType.SKIPPED;
      }
    }

    // 如果所有 iteration 都是成功
    return StepExecutionType.SUCCESS;
  } else {
    // 处理普通步骤（无 iterations）
    if (!stepData.result) {
      // 没有 result，算作跳过
      return StepExecutionType.SKIPPED;
    }

    // 直接返回步骤的结果状态
    return stepData.result.status;
  }
}


// 失败用例重执行
const failSceneRetry = async () => {
  let reTrySceneIds = [];
  // 获取失败的场景
  if (sceneListOverview.value['failed'] && Object.keys(sceneListOverview.value['failed']).length > 0) {
    // 循环 使用_截取 场景id
    for (let i = 0; i < Object.keys(sceneListOverview.value['failed']).length; i++) {
      reTrySceneIds.push(Number(Object.keys(sceneListOverview.value['failed'])[i].split('_')[0]))
    }
  }

  // 出现弹窗
  Modal.confirm({
    title: '重执行失败场景',
    content: '该操作会重新执行失败的场景，重新执行的场景结果会被汇总到当前报告中，是否继续？',
    onOk: async () => {
      const result = await reRunFailScene({
        reTrySceneIds: reTrySceneIds,
        sourceReportId: reportInfo.value.id
      });

      if (result.data === true) {
        Message.success({
          content: '重执行成功',
          duration: 1000
        });
        // 回退到报告列表
        router.push({
          name: 'ReportList'
        })
      } else {
        Message.error({
          content: '重执行失败，请重试',
          duration: 1000
        });
      }
    },
    onCancel: () => {
      // 取消
    }
  });

}

</script>

<style scoped>
.container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  margin-bottom: 0;
}

.report-detail-header {
  flex-shrink: 0;
}

.report-detail-body {
  flex: 1;
  min-height: 0;
}

.detail-card {
  display: flex;
  flex-direction: column;
}

.detail-card :deep(.arco-card-body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.detail-card :deep(.arco-tabs) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.detail-card :deep(.arco-tabs-nav) {
  flex-shrink: 0;
}

.detail-card :deep(.arco-tabs-content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.detail-card :deep(.arco-tabs-content-list) {
  flex: 1;
  min-height: 0;
}

.detail-card :deep(.arco-tabs-content-item) {
  height: 100%;
}

.detail-card :deep(.arco-tabs-pane) {
  height: 100%;
  overflow: hidden;
}

:deep(.arco-tree-node) {
  width: 100% !important;
}

:deep(.arco-tree-node-title) {
  width: 100% !important;
  flex: 1 !important;
}

:deep(.arco-tree-node-title-text) {
  width: 100% !important;
  flex: 1 !important;
}

:deep(.arco-card) {
  width: 100% !important;
  margin: 0 !important;
}

:deep(.arco-card-body) {
  padding: 12px !important;
}

.active {
  border: 1px solid #409EFF;
  background-color: #ecf5ff;
}

.success {
  border: 1px solid #67c23a;
  background-color: #f0f9ff;
}

.failure {
  border: 1px solid #F56C6C;
  background-color: #fef0f0;
}

.skipped {
  border: 1px solid #E6A23C;
  background-color: #fdf6ec;
}


</style>