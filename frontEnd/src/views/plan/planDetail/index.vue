<template>
  <div class="container" style="height: 100%">
    <Breadcrumb
        :items="['menu.plan', 'menu.plan.planDetail']"
    />
    <a-space direction="vertical" :size="12" fill>
      <a-space direction="vertical" :size="16" fill>
        <a-card :title="$t('menu.plan.planDetail')">
          <template #extra>
            <a-space align="center">
              <a-button v-permission="'auto:plan:update'" v-if="plan.planCategory !== PlanCategory.API" type="primary" @click="editPlanConfig()">
                <template #icon>
                  <icon-settings/>
                </template>
                任务配置
              </a-button>
              <a-button type="primary" @click="refresh()">
                <template #icon>
                  <icon-refresh/>
                </template>
                刷新
              </a-button>
              <a-button v-permission="'auto:plan:execute'" type="primary" status="success" @click="execution()">
                执行
              </a-button>
            </a-space>
          </template>
          <template #title>
            <a-space align="center">
              <a-button type="text" @click="router.back()">
                <template #icon>
                  <icon-undo/>
                </template>
                返回
              </a-button>
              <icon-user style="color: #3cc071"/>
              创建者：
              <a-avatar :size="32">
                <img
                    alt="avatar"
                    src="https://p1-arco.byteimg.com/tos-cn-i-uwbnlip3yd/3ee5f13fb09879ecb5185e440cef6eb9.png~tplv-uwbnlip3yd-webp.webp"
                />
              </a-avatar>
              「{{ plan.createUserId }}」
              任务名称：[{{ plan.planName }}]
              <a-tag :color="plan.planCategory === PlanCategory.API ? 'arcoblue' : 'green'">{{ PlanCategoryDesc[plan.planCategory || PlanCategory.UI] }}</a-tag>
              <icon-calendar-clock style="color: #0960bd"/>
              创建时间：【{{ plan.createdAt }}】
              更新时间：【{{ plan.updatedAt }}】
            </a-space>
          </template>
        </a-card>
      </a-space>
    </a-space>
    <a-row style="margin-bottom: 16px;height: 100%">
      <a-col :flex="3" style="height: 100%">
        <a-card title="任务配置" style="height: 100%">
          <template #extra>
            <a-button v-permission="'auto:plan:update'" type="primary" @click="update()">保存</a-button>
          </template>
          <a-form ref="planFormRef" :model="planForm" layout="vertical" :rules="rules">
            <a-form-item field="planName" label="任务名称">
              <a-input v-model="planForm.planName"/>
            </a-form-item>
            <a-form-item field="description" label="任务描述">
              <a-textarea v-model="planForm.description"/>
            </a-form-item>
            <a-form-item field="taskType" label="任务类型">
              <a-radio-group v-model="planForm.taskType">
                <a-radio value="NORMAL">普通任务</a-radio>
                <a-radio value="TIMING">定时任务</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item v-if="planForm.taskType === TaskType.TIMING" field="cronExpression" label="参数">
              <CronConfig v-model="planForm.cronExpression"/>
            </a-form-item>
            <a-form-item field="executionType" label="执行类型">
              <a-radio-group v-model="planForm.executionType">
                <a-radio value="ORDER">顺序执行</a-radio>
                <a-radio value="PARALLEL">并发执行</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item v-if="plan.planCategory !== PlanCategory.API" field="sceneStatusExtract" label="场景状态提取">
              <a-tree-select
                  :data="sceneTreeData"
                  placeholder="请选择需要状态的场景"
                  style="width: 300px"
                  v-model="planForm.sceneStatusExtract"
                  :selectable="isDisabled"
                  :fieldNames="{
                    key: 'id',
                    title: 'name',
                  }"
                  :allow-clear="true"
              >
                <template #tree-slot-icon="{node}">
                  <icon-folder v-if="node.sceneType==='FOLDER'" style="font-size: 16px;"/>
                  <icon-drive-file v-if="node.sceneType==='SCENE'" style="font-size: 16px;"/>
                </template>
              </a-tree-select>
            </a-form-item>
            <a-form-item field="webhookEnabled" label="执行后通知">
              <a-switch
                  v-model="planForm.webhookEnabled"
                  :checked-value="1"
                  :unchecked-value="0"
                  checked-text="开启"
                  unchecked-text="关闭"
              />
            </a-form-item>
            <a-form-item
                v-if="planForm.webhookEnabled === 1"
                field="webhookIds"
                label="通知配置"
            >
              <a-select
                  v-model="webhookIdsArray"
                  placeholder="请选择要通知的 Webhook 配置（多选）"
                  multiple
                  allow-clear
                  :max-tag-count="3"
              >
                <a-option
                    v-for="item in webhookOptions"
                    :key="item.value"
                    :value="item.value"
                    :disabled="!item.enabled"
                >
                  <span :style="{ color: item.enabled ? undefined : '#86909c' }"
                  >{{ item.label }}
                  </span>
                  <a-tag
                      v-if="!item.enabled"
                      size="small"
                      color="gray"
                      style="margin-left: 8px"
                  >已禁用</a-tag>
                </a-option>
              </a-select>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
      <a-col :flex="9" style="height: 100%">
        <a-card style="height: 100%" title="场景步骤">
          <template #title>
            <a-space>
              <a-button v-permission="'auto:plan:update'" type="primary" @click="openAddScene()">
                添加场景
                <template #icon>
                  <icon-plus/>
                </template>
              </a-button>
            </a-space>
          </template>
          <a-table row-key="id" :columns="sceneColumns" :data="selectedSceneList"
                   :draggable="{ type: 'handle', width: 40 }"
                   @change="handleChange"
                   :scrollbar="true"
                   :scroll="{x:'100%',y:'100%'}"
                   :hide-expand-button-on-empty="true" :pagination="false">
            <template #options="{ record }">
              <a-space align="center">
                <a-button v-permission="'auto:plan:update'" type="primary" @click="handleEditScene(record)">
                  编辑
                </a-button>
                <a-button v-permission="'auto:plan:update'" type="primary" status="danger" @click="handleRemoveScene(record)">
                  移除
                </a-button>
              </a-space>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
    <a-modal :visible="addSceneVisible" @cancel="addSceneVisible= false" :width="1000" title="添加场景"
             @ok="handleAddSceneOk">
      <a-table row-key="id" :columns="columns" :data="sceneList" :row-selection="rowSelection"
               :hide-expand-button-on-empty="true"
               v-model:selectedKeys="selectedKeys"/>
    </a-modal>


    <!--    计划运行配置-->
    <a-modal v-model:visible="planRunningConfigVisible" title="任务运行配置"
             width="auto"
             @before-ok="handlePlanConfigBeforeOk">
      <a-form
          style="width: 670px;"
          label-align="left"
          ref="updatePlanRunningConfigFormRef" :model="updatePlanRunningConfigForm"
          :rules="updatePlanRunningConfigFormRules">
        <a-tabs :default-active-key="1">
          <a-tab-pane :key="1" title=运行配置>
            <a-form-item field="sceneBrowserConfig.browserType" label="浏览器类型" required>
              <a-select v-model="updatePlanRunningConfigForm.sceneBrowserConfig.browserType">
                <a-option value="CHROME">谷歌</a-option>
                <a-option value="EDGE">微软Edge</a-option>
                <a-option value="FIREFOX">火狐</a-option>
                <a-option value="SAFARI">safari</a-option>
                <a-option value="IE">IE浏览器</a-option>
              </a-select>
            </a-form-item>
            <a-form-item field="sceneBrowserConfig.runningType" label="运行模式" required>
              <a-select v-model="updatePlanRunningConfigForm.sceneBrowserConfig.runningType">
                <a-option value="NORMAL">正常模式</a-option>
                <a-option value="HEADLESS">无头模式</a-option>
              </a-select>
            </a-form-item>
            <a-form-item field="sceneBrowserConfig.windowMode" label="窗口模式" required>
              <a-select v-model="updatePlanRunningConfigForm.sceneBrowserConfig.windowMode">
                <a-option value="MAXIMIZE">窗口最大化</a-option>
                <a-option value="CUSTOMSIZE">自定义尺寸</a-option>
              </a-select>
            </a-form-item>
            <a-form-item field="sceneBrowserConfig.deviceType" label="设备类型" required>
              <a-select v-model="updatePlanRunningConfigForm.sceneBrowserConfig.deviceType">
                <a-option value="MOBILE">移动端</a-option>
                <a-option value="PC">PC端</a-option>
              </a-select>
            </a-form-item>

            <a-form-item v-if="updatePlanRunningConfigForm.sceneBrowserConfig.windowMode === 'CUSTOMSIZE'"
                         field="sceneBrowserConfig.windowSize" label="窗口大小"
                         required>
              <a-input v-model="updatePlanRunningConfigForm.sceneBrowserConfig.windowSize"/>
            </a-form-item>
          </a-tab-pane>
          <a-tab-pane :key="2" title="步骤通用配置">
            <a-form-item field="setting.timeout" label="超时时间" required>
              <a-input-number :precision="0" hide-button="hide-button" :min="0"
                              v-model="updatePlanRunningConfigForm.setting.timeout">
                <template #suffix>
                  秒
                </template>
              </a-input-number>
            </a-form-item>
            <a-form-item field="setting.preExecuteWaitingTime" label="执行前等待时间" required>
              <a-input-number :precision="0" hide-button="hide-button" :min="0"
                              v-model="updatePlanRunningConfigForm.setting.preExecuteWaitingTime">
                <template #suffix>
                  秒
                </template>
              </a-input-number>
            </a-form-item>
            <a-form-item field="setting.waitingTimeAfterExecution" label="执行后等待时间" required>
              <a-input-number :precision="0" hide-button="hide-button" :min="0"
                              v-model="updatePlanRunningConfigForm.setting.waitingTimeAfterExecution">
                <template #suffix>
                  秒
                </template>
              </a-input-number>
            </a-form-item>
            <a-form-item field="setting.screenshotConfiguration" label="截图策略" required>
              <a-select v-model="updatePlanRunningConfigForm.setting.screenshotConfiguration">
                <a-option value="NOT_SCREENSHOT">不截图</a-option>
                <a-option value="SCREENSHOT">当前步骤截图</a-option>
                <a-option value="SCREENSHOT_EXCEPTION">出现异常截图</a-option>
              </a-select>
            </a-form-item>
            <a-form-item field="setting.errorHandlingStrategy" label="错误处理策略" required>
              <a-select v-model="updatePlanRunningConfigForm.setting.errorHandlingStrategy">
                <a-option value="IGNORE">忽略</a-option>
                <a-option value="STOP">终止</a-option>
                <a-option value="RETRY" disabled>重试（暂时搁置）</a-option>
              </a-select>
            </a-form-item>
          </a-tab-pane>
        </a-tabs>
      </a-form>
    </a-modal>

    <!-- 执行面板 -->
    <a-drawer
      :width="600"
      :visible="executionPanelVisible"
      @cancel="closeExecutionPanel"
      :footer="false"

      :title="executionReport.status === 1 ? (executionSummary.failure > 0 ? `执行完成（${executionSummary.failure} 个步骤失败）` : '执行完成') : '执行中'"
      :mask-closable="false"
    >
      <div v-if="executionReport.status === 0" class="execution-running">
        <a-spin dot />
        <span style="margin-left: 8px">任务执行中...</span>
      </div>
      <div v-else-if="executionReport.status === 1" class="execution-complete">
        <!-- 完成态按步骤结果分三支：全过/有失败/全部失败，绝不把失败渲染成成功 -->
        <a-result v-if="executionSummary.verdict === 'success'" status="success" title="执行完成，全部通过">
          <template #extra>
            <a-button type="primary" @click="viewReportDetail">查看完整报告</a-button>
          </template>
        </a-result>
        <a-result v-else-if="executionSummary.verdict === 'warning'" status="warning" :title="`执行完成，${executionSummary.failure} 个步骤失败`">
          <template #subtitle>
            通过 {{ executionSummary.success }} / 失败 {{ executionSummary.failure }} / 跳过 {{ executionSummary.skipped }}，共 {{ executionSummary.total }} 步
          </template>
          <template #extra>
            <a-button type="primary" @click="viewReportDetail">查看失败详情</a-button>
          </template>
        </a-result>
        <a-result v-else status="error" :title="`执行完成，全部 ${executionSummary.failure} 个步骤失败`">
          <template #extra>
            <a-button type="primary" @click="viewReportDetail">查看失败详情</a-button>
          </template>
        </a-result>
      </div>

      <!-- 场景列表 -->
      <div class="scene-list" style="margin-top: 16px">
        <div
          v-for="(scene, sceneIndex) in executionSceneList"
          :key="scene.sceneId"
          class="scene-item"
          style="margin-bottom: 16px; border: 1px solid #e5e6eb; border-radius: 4px; padding: 12px"
        >
          <div class="scene-header" style="font-weight: bold; margin-bottom: 8px">
            <icon-code-sandbox style="margin-right: 4px" />
            {{ scene.sceneName }}
          </div>
          <div v-if="scene.steps.length === 0" class="scene-empty" style="color: #86909c">
            等待执行...
          </div>
          <div v-else class="step-list">
            <div
              v-for="(step, stepIndex) in scene.steps"
              :key="step.id || stepIndex"
              class="step-item"
              style="display: flex; align-items: center; padding: 4px 0"
            >
              <div class="step-status" style="width: 24px; text-align: center">
                <icon-check-circle
                  v-if="getStepRunStatus(step, sceneIndex, stepIndex) === 'SUCCESS'"
                  style="color: #00b42a; font-size: 16px"
                />
                <icon-close-circle
                  v-else-if="getStepRunStatus(step, sceneIndex, stepIndex) === 'FAILURE'"
                  style="color: #f53f3f; font-size: 16px"
                />
                <icon-minus-circle
                  v-else-if="getStepRunStatus(step, sceneIndex, stepIndex) === 'SKIPPED'"
                  style="color: #86909c; font-size: 16px"
                />
                <icon-loading
                  v-else-if="getStepRunStatus(step, sceneIndex, stepIndex) === 'RUNNING'"
                  style="color: #165dff; font-size: 16px"
                />
                <icon-clock-circle
                  v-else
                  style="color: #c9cdd4; font-size: 16px"
                />
              </div>
              <div class="step-name" style="flex: 1; margin-left: 8px; font-size: 13px">
                {{ step.step?.stepName || step.stepName || '未知步骤' }}
              </div>
              <div class="step-time" style="color: #86909c; font-size: 12px">
                <span v-if="step.result?.timeConsuming">{{ step.result.timeConsuming }}ms</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-drawer>
  </div>

</template>

<script lang="ts" setup>

import {computed, onMounted, ref} from "vue";
import {useRoute, useRouter} from "vue-router";
import {getPlanById, updatePlan, updatePlanRunningConfig} from "@/api/MyApi/plan";
import {listPlanWebhooks} from "@/api/MyApi/planWebhook";
import {Plan} from "@/types/domain/Plan";
import {SceneVO} from "@/types/vo/SceneVO";
import {getAllSceneList, getSceneListByIds, updateSceneSetting} from "@/api/MyApi/scene";
import {Message} from "@arco-design/web-vue";
import {executionTask} from "@/api/MyApi/task";
import {getReportDetail} from "@/api/MyApi/report";
import CronConfig from "@/views/plan/components/CronConfig.vue";
import {StepExecutionType} from "@/types/enum/StepExecutionType";
import {TaskType} from "@/types/enum/task/TaskType";
import {PlanCategory, PlanCategoryDesc} from "@/types/enum/plan/PlanCategory";
import {useProjectStore} from "@/store";
import {SceneSetting} from "@/types/domain/SceneSetting";


const planRunningConfigVisible = ref(false);
const updatePlanRunningConfigForm = ref<any>({setting: {}, sceneBrowserConfig: {}});
const updatePlanRunningConfigFormRef = ref()

const sceneTreeData = ref([]);

const projectStore = useProjectStore();
const selectedKeys = ref([]);
const planFormRef = ref('');
const rowSelection = ref({
  type: 'checkbox',
  onlyCurrent: false,
});

// ========== 执行面板相关 ==========
const executionPanelVisible = ref(false);
const executionReportId = ref<number | null>(null);
const executionReport = ref<any>({});
const executionTimer = ref<ReturnType<typeof setTimeout> | null>(null);
const executionSceneList = ref<any[]>([]);
// 缓存当前正在执行的步骤索引，避免 getStepRunStatus 双重循环 O(n²)
const currentRunningIndex = ref<{ sceneIdx: number; stepIdx: number }>({ sceneIdx: -1, stepIdx: -1 });

// 打开执行面板
const openExecutionPanel = (reportId: number) => {
  executionReportId.value = reportId;
  executionPanelVisible.value = true;

  // 用计划已选场景构建初始骨架，避免刷新后未执行的场景丢失
  executionSceneList.value = selectedSceneList.value.map(scene => ({
    sceneId: scene.id,
    sceneName: scene.name,
    steps: []
  }));

  loadExecutionReport();
  // 启动1秒轮询，使用 setTimeout 递归确保每次请求完成后再发下一次
  if (executionTimer.value) clearTimeout(executionTimer.value);
  const doPoll = async () => {
    if (!executionPanelVisible.value) return; // 抽屉关闭后停止
    try {
      await loadExecutionReport();
    } catch (e) {
      // 请求异常时继续轮询，避免网络抖动导致轮询中断
      console.error('轮询报告异常', e);
    }
    if (executionReport.value.status === 0) {
      executionTimer.value = setTimeout(doPoll, 1000);
    }
  };
  doPoll();
};

// 关闭执行面板
const closeExecutionPanel = () => {
  executionPanelVisible.value = false;
  if (executionTimer.value) {
    clearTimeout(executionTimer.value);
    executionTimer.value = null;
  }
};

// 加载执行中的报告
const loadExecutionReport = async () => {
  if (!executionReportId.value) return;
  try {
    const result = await getReportDetail(executionReportId.value);
    executionReport.value = result.data;
    if (executionReport.value.scenes) {
      const scenes = JSON.parse(executionReport.value.scenes);
      const reportSceneMap = new Map<string, any[]>();
      Object.entries(scenes).forEach(([key, steps]: [string, any]) => {
        const sceneId = key.split('_')[0];
        reportSceneMap.set(sceneId, Array.isArray(steps) ? steps : []);
      });

      // 用报告结果更新已有场景的步骤，保留骨架中未开始的场景
      executionSceneList.value = executionSceneList.value.map(scene => {
        const reportSteps = reportSceneMap.get(String(scene.sceneId));
        if (reportSteps) {
          return { ...scene, steps: reportSteps };
        }
        return scene;
      });
    }

    // 计算当前正在执行的步骤索引（缓存，避免 getStepRunStatus 双重循环）
    let found = false;
    for (let s = 0; s < executionSceneList.value.length; s++) {
      const scene = executionSceneList.value[s];
      for (let i = 0; i < scene.steps.length; i++) {
        if (!scene.steps[i].result) {
          currentRunningIndex.value = { sceneIdx: s, stepIdx: i };
          found = true;
          break;
        }
      }
      if (found) break;
    }
    if (!found) {
      currentRunningIndex.value = { sceneIdx: -1, stepIdx: -1 };
    }

    // 状态变为已完成，停止轮询并清理 sessionStorage
    if (executionReport.value.status === 1 && executionTimer.value) {
      clearTimeout(executionTimer.value);
      executionTimer.value = null;
      sessionStorage.removeItem('planExecution_' + plan.value.id);
    }
  } catch (e) {
    console.error('加载报告失败', e);
  }
};

// 获取步骤状态（用于渲染）
const getStepRunStatus = (step: any, sceneIndex: number, stepIndex: number) => {
  if (step.result && step.result.status) {
    return step.result.status;  // SUCCESS / FAILURE / SKIPPED
  }
  // 判断是否是当前正在执行的步骤
  const reportStatus = executionReport.value.status;
  if (reportStatus !== 0) return 'PENDING';  // 非执行中状态，未执行的显示等待
  // 找到第一个没有 result 的步骤，标记为执行中
  for (let s = 0; s < executionSceneList.value.length; s++) {
    const scene = executionSceneList.value[s];
    for (let i = 0; i < scene.steps.length; i++) {
      const st = scene.steps[i];
      if (!st.result) {
        // 当前步骤就是正在执行的
        if (s === sceneIndex && i === stepIndex) return 'RUNNING';
        // 之后的步骤显示等待
        if (s > sceneIndex || (s === sceneIndex && i > stepIndex)) return 'PENDING';
      }
    }
  }
  return 'PENDING';
};

// 执行结果汇总：决定完成态渲染（成功/有失败/全部失败），不再只看 report.status
const executionSummary = computed(() => {
  let success = 0;
  let failure = 0;
  let skipped = 0;
  executionSceneList.value.forEach((scene, si) => {
    (scene.steps || []).forEach((step: any, i: number) => {
      const st = getStepRunStatus(step, si, i);
      if (st === 'FAILURE') failure += 1;
      else if (st === 'SUCCESS') success += 1;
      else if (st === 'SKIPPED') skipped += 1;
    });
  });
  const total = success + failure + skipped;
  const verdict = failure === 0 ? 'success' : success === 0 && skipped === 0 ? 'error' : 'warning';
  return { success, failure, skipped, total, verdict };
});

// 查看报告详情
const viewReportDetail = () => {
  if (executionReportId.value) {
    window.open(`/report/reportDetail/${executionReportId.value}`, '_blank');
  }
};

const columns = [
  {
    title: '场景名称',
    dataIndex: 'name',
  },
  {
    title: '场景描述',
    dataIndex: 'description',
  },
  {
    title: '目录',
    dataIndex: 'parentFolder',
  }
]

const sceneColumns = [
  {
    title: '场景名称',
    dataIndex: 'name',
  },
  {
    title: '场景描述',
    dataIndex: 'description',
  },
  {
    title: '创建时间',
    dataIndex: 'createAt'
  },
  {
    title: '操作',
    slotName: 'options',
  }
]


const reloadSceneTree = async () => {
  const projectId = projectStore.getProjectId;
  const category = plan.value.planCategory || PlanCategory.UI;
  const result = await getAllSceneList(projectId, category);
  sceneTreeData.value = result.data;
}
const reloadPlan = async () => {
  // 获取计划id
  if (!route.params.planId) {
    router.back();
  }
  // 获取计划详情
  const result = await getPlanById(route.params.planId);
  plan.value = result.data;
  // 解析 webhookIds
  parseWebhookIds(plan.value.webhookIds);
}
const reloadSelectScenes = async () => {
  if (plan.value.params) {
    selectedSceneList.value = JSON.parse(plan.value.params);
    const result = await getSceneListByIds(selectedSceneList.value);
    if (result.data) {
      selectedSceneList.value = result.data;
    }
  }
}

const router = useRouter();
const route = useRoute();

// 计划数据
const plan = ref<Plan>(new Plan());

// 计划表单
const planForm = ref<Plan>(new Plan());

// Webhook 配置选项（多选下拉框用，包含禁用项）
const webhookOptions = ref<Array<{ label: string; value: number; enabled: boolean }>>([]);
// Webhook 多选值（数组，提交时转为逗号分隔字符串）
const webhookIdsArray = ref<number[]>([]);

/**
 * 加载项目下的 Webhook 配置选项（包含禁用的，用于展示）
 */
const loadWebhookOptions = async () => {
  try {
    const res = await listPlanWebhooks(projectStore.getProjectId);
    const list = res.data || [];
    webhookOptions.value = list.map((item: any) => ({
      label: `${item.name} (${item.type})`,
      value: item.id,
      enabled: item.enabled,
    }));
  } catch (e) {
    console.error('加载 Webhook 配置失败', e);
  }
};

/**
 * 解析计划的 webhookIds 字符串为数组
 */
const parseWebhookIds = (webhookIds?: string) => {
  if (!webhookIds || webhookIds.trim() === '') {
    webhookIdsArray.value = [];
    return;
  }
  webhookIdsArray.value = webhookIds
    .split(',')
    .map((s: string) => parseInt(s.trim(), 10))
    .filter((n: number) => !isNaN(n));
};

/**
 * 将 webhookIdsArray 转为逗号分隔字符串
 */
const buildWebhookIds = () => {
  if (!webhookIdsArray.value || webhookIdsArray.value.length === 0) {
    return undefined;
  }
  return webhookIdsArray.value.join(',');
};
onMounted(async () => {
  await reloadPlan();
  await reloadSceneTree();
  await loadWebhookOptions();
  // 拷贝一份计划数据
  planForm.value = {...plan.value};

  // 判断计划是否有已选择的场景
  if (plan.value.params) {
    selectedSceneList.value = JSON.parse(plan.value.params);
    const result = await getSceneListByIds(selectedSceneList.value);
    if (result.data) {
      selectedSceneList.value = result.data;
      console.log("selectedSceneList.value", selectedSceneList.value)
    }
  }

  // 刷新恢复：检查是否有执行中的报告
  const savedReportId = sessionStorage.getItem('planExecution_' + plan.value.id);
  if (savedReportId) {
    try {
      const reportRes = await getReportDetail(Number(savedReportId));
      if (reportRes.data && reportRes.data.status === 0) {
        openExecutionPanel(Number(savedReportId));
      } else {
        // 报告已完成或不存在，清理标记
        sessionStorage.removeItem('planExecution_' + plan.value.id);
      }
    } catch (e) {
      sessionStorage.removeItem('planExecution_' + plan.value.id);
    }
  }
})


// 添加场景弹窗
const addSceneVisible = ref(false);

// 场景列表
const sceneList = ref<SceneVO[]>([])

// 当前已选择的场景列表
const selectedSceneList = ref<SceneVO[]>([])

// 打开添加场景弹窗
const openAddScene = async () => {
  addSceneVisible.value = true;
  const category = plan.value.planCategory || PlanCategory.UI;
  const result = await getAllSceneList(projectStore.getProjectId, category);
  const temp = result.data;
  sceneList.value = extractScenesWithParentFolder(temp)
  // 清空所有的selectedKeys,重新根据plan的parms赋值
  selectedKeys.value = JSON.parse(plan.value.params);
}

// 组合场景目录结构
const extractScenesWithParentFolder = (data: SceneVO[], parentPath = ''): any[] => {
  const result = [];

  for (const item of data) {
    // 构建当前项的完整路径
    const currentPath = parentPath ? `${parentPath}/${item.name}` : item.name;

    if (item.sceneType === 'SCENE') {
      // 如果是场景，添加到结果中，并设置parentFolder
      result.push({
        ...item,
        parentFolder: parentPath || '' // 如果没有父级路径则为空字符串
      });
    }

    // 递归处理子项
    if (item.children && item.children.length > 0) {
      const childResults = extractScenesWithParentFolder(item.children, currentPath);
      result.push(...childResults);
    }
  }

  return result;
}

// 确认添加场景
const handleAddSceneOk = async () => {
  // 前端预校验：所选场景类型必须与计划类型一致
  const planCategory = plan.value.planCategory || PlanCategory.UI;
  const invalid = selectedKeys.value.some((id: any) => {
    const scene = sceneList.value.find((s: any) => s.id === id);
    return scene && (scene.sceneCategory || PlanCategory.UI) !== planCategory;
  });
  if (invalid) {
    Message.warning({
      content: '所选场景类型与计划类型不一致',
      duration: 1000
    });
    return;
  }

  // 更新plan数据
  plan.value.params = JSON.stringify(selectedKeys.value);
  const result = await updatePlan(plan.value);
  if (result.data) {
    Message.success({
      content: '更新成功',
      duration: 1000
    })
    addSceneVisible.value = false;
    await reloadSelectScenes();
  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }

}

// 拖拽场景变化的时候
const handleChange = async (newData: any) => {
  // 判断如果newData长度大于1，则对其进行排序
  if (newData.length > 1) {
    // 抽取newData数组里边的id，组成一个新的id数组
    const ids = newData.map((item: any) => item.id);
    // 更新plan
    plan.value.params = JSON.stringify(ids);
    const result = await updatePlan(plan.value);
    if (result.data) {
      Message.success({
        content: '更新成功',
        duration: 1000
      })
      await reloadPlan();
      await reloadSelectScenes();
    } else {
      Message.error({
        content: result.msg,
        duration: 1000
      })
    }
  }
}

const handleEditScene = (record: SceneVO) => {
  router.push({
    name: record.sceneCategory === 'API' ? 'ApiSceneList' : 'UiSceneList',
    query: {
      selectSceneId: record.id
    }
  })
}

const handleRemoveScene = async (record: SceneVO) => {
  if (plan.value.params && JSON.parse(plan.value.params as string).includes(record.id)) {
    plan.value.params = JSON.stringify(JSON.parse(plan.value.params as string).filter((id: number) => id !== record.id));
  }
  const result = await updatePlan(plan.value);
  if (result.data) {
    Message.success({
      content: '更新成功',
      duration: 1000
    })
  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }
  await reloadSelectScenes();
}
// 提交校验
const rules = {
  planName: [
    {
      required: true,
      message: '任务名称不能为空',
    },
  ],
  description: [
    {
      max: 100,
      message: '任务描述不能超过100个字符',
    }
  ],
  cronExpression: [
    {
      required: true,
      message: '任务执行表达式不能为空',
    },
  ],
  taskType: [
    {
      required: true,
      message: '任务执行类型不能为空',
    },
  ],
  executionType: [
    {
      required: true,
      message: '任务执行类型不能为空',
    },
  ],
}


// 更新计划信息
const update = async () => {
  // 这里更新的是否，更新的是所有的信息包括 plan.params，所以得重新同步一遍已修改的plan.params
  const res = await planFormRef.value?.validate();
  if (res) {
    return false;
  }
  planForm.value.params = plan.value.params;
  planForm.value.webhookIds = buildWebhookIds();
  const result = await updatePlan(planForm.value);
  if (result.data) {
    Message.success({
      content: '更新成功',
      duration: 1000
    })
    await reloadPlan();
    // 更新一遍计划表单的数据
    planForm.value = {...plan.value};
    await reloadSelectScenes();
  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }
}

// 刷新
const refresh = async () => {
  await reloadPlan();
  planForm.value = {...plan.value};
  await reloadSelectScenes();
}

// 执行
const execution = async () => {
  // 判断场景是否为空，为空的时候，不允许执行
  if (!JSON.parse(plan.value.params) || JSON.parse(plan.value.params).length <= 0) {
    Message.warning({
      content: '请先添加场景',
      duration: 1000
    })
    return;
  }

  // 防重复执行：检查是否已有执行中的报告
  const savedReportId = sessionStorage.getItem('planExecution_' + plan.value.id);
  if (savedReportId) {
    try {
      const reportRes = await getReportDetail(Number(savedReportId));
      if (reportRes.data && reportRes.data.status === 0) {
        // 还在执行中，直接 reopen 抽屉，不创建新执行
        openExecutionPanel(Number(savedReportId));
        Message.info({
          content: '已有执行中的任务，继续查看进度',
          duration: 2000
        });
        return;
      }
      // 已完成，清理标记
      sessionStorage.removeItem('planExecution_' + plan.value.id);
    } catch (e) {
      sessionStorage.removeItem('planExecution_' + plan.value.id);
    }
  }

  const result = await executionTask(plan.value.id);
  if (result.data) {
    const reportId = result.data;
    // 写入 sessionStorage，刷新后可恢复
    sessionStorage.setItem('planExecution_' + plan.value.id, String(reportId));
    // 页面内打开执行面板，实时查看执行进度
    openExecutionPanel(reportId);
    Message.success({
      content: '已开始执行',
      duration: 2000
    })
  }
}

// 判断是否可选
const isDisabled = (node: any) => {
  if (node.sceneType != null && node.sceneType === 'SCENE') {
    return true;
  }
  return false;
}

// 计划配置
const editPlanConfig = () => {
  planRunningConfigVisible.value = true;
  updatePlanRunningConfigFormRef.value.clearValidate();
  updatePlanRunningConfigForm.value = JSON.parse(plan.value.planRunningSetting as string);
}

const handlePlanConfigBeforeOk = async () => {
  const error = await updatePlanRunningConfigFormRef.value?.validate();
  if (error) {
    Message.error({
      content: '检验失败',
      duration: 1000
    })
    return false;
  }
  const result = await updatePlanRunningConfig(plan.value.id, JSON.stringify(updatePlanRunningConfigForm.value));
  if (result.data === true) {
    Message.success({
      content: '保存成功',
      duration: 1000
    })
    // 重置当前计划
    await reloadPlan();
  } else {
    Message.error({
      content: '保存失败',
      duration: 1000
    })

  }
}

// 编辑计划运行配置表单 提交检验规则
const updatePlanRunningConfigFormRules = {
  'sceneBrowserConfig.browserType': [
    {
      required: true,
      message: '请选择浏览器类型',
    },
  ],
  'sceneBrowserConfig.runningType': [
    {
      required: true,
      message: '请选择运行类型',
    },
  ],
  'sceneBrowserConfig.windowMode': [
    {
      required: true,
      message: '请选择窗口模式',
    },
  ],
  'sceneBrowserConfig.windowSize': [
    {
      required: true,
      validator: (value: any, callback: any) => {
        // 如果设置了跳过验证，则直接通过
        if (updatePlanRunningConfigForm.value.sceneBrowserConfig?.windowMode === 'MAXIMIZE') {
          return callback();
        }
        // 否则检查是否为空
        if (!value || value.trim() === '') {
          return callback('请输入尺寸');
        }


        if (!/^(?!0)\d{1,4}x(?!0)\d{1,4}$/.test(value)) {
          return callback('请输入合法的屏幕尺寸');
        }
        callback();
        callback();
      }
    }
  ],
  'setting.timeout': [
    {
      required: true,
      message: '请输入超时时间',
    },
  ],
  'setting.preExecuteWaitingTime': [
    {
      required: true,
      message: '请输入执行前等待时间',
    },
  ],
  'setting.waitingTimeAfterExecution': [
    {
      required: true,
      message: '请输入执行后等待时间',
    },
  ],
  'setting.screenshotConfiguration': [
    {
      required: true,
      message: '请选择截图策略',
    },
  ],
  'setting.errorHandlingStrategy': [
    {
      required: true,
      message: '请选择异常处理策略',
    },
  ],
};


</script>

<style scoped>

.execution-running {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: #165dff;
  font-size: 14px;
}

.execution-complete {
  padding: 10px 0;
}

.scene-item {
  transition: all 0.3s;
}

.scene-item:hover {
  border-color: #165dff;
}

.step-item {
  transition: background-color 0.2s;
}

.step-item:hover {
  background-color: #f2f3f5;
}

</style>