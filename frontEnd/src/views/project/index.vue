<template>
  <div class="project-page-root">
  <a-scrollbar style="height: var(--page-container-height, calc(100vh - 60px)); overflow: auto;">
    <!-- 空状态 -->
    <div v-if="!currentProject?.id" class="empty-state">
      <!-- Hero 分割区域 -->
      <section class="hero-section">
        <div class="hero-content">
          <div class="hero-eyebrow">Mokatest 项目中心</div>
          <h1 class="hero-title">从这里开始你的<br />质量管理工作流</h1>
          <p class="hero-tagline">
            创建项目后，即可统一管理需求、用例、BUG、自动化测试与测试计划，
            让测试工作从分散走向体系化。
          </p>
          <div class="hero-actions">
            <a-button
              type="primary"
              shape="round"
              size="large"
              @click="handleCreateOrUpdateProject(null)"
            >
              <template #icon><IconPlus /></template>
              创建项目
            </a-button>
            <a-button
              type="text"
              size="large"
              @click="goBackToList"
            >
              <template #icon><IconArrowLeft /></template>
              {{ backLabel }}
            </a-button>
          </div>
        </div>

        <div class="hero-visual">
          <div class="preview-window">
            <div class="preview-header">
              <div class="preview-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <div class="preview-title">项目总览</div>
            </div>
            <div class="preview-body">
              <div class="preview-stats">
                <div class="preview-stat">
                  <div class="preview-stat-value">128</div>
                  <div class="preview-stat-label">需求</div>
                </div>
                <div class="preview-stat">
                  <div class="preview-stat-value">56</div>
                  <div class="preview-stat-label">用例</div>
                </div>
                <div class="preview-stat">
                  <div class="preview-stat-value">12</div>
                  <div class="preview-stat-label">BUG</div>
                </div>
              </div>
              <div class="preview-chart">
                <div class="preview-chart-bar" style="height: 40%;"></div>
                <div class="preview-chart-bar" style="height: 70%;"></div>
                <div class="preview-chart-bar" style="height: 55%;"></div>
                <div class="preview-chart-bar" style="height: 85%;"></div>
                <div class="preview-chart-bar" style="height: 60%;"></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Features 区域 -->
      <section class="features-section">
        <div class="features-grid">
          <a-card
            v-for="feature in featureCards"
            :key="feature.key"
            class="feature-card"
            :bordered="false"
          >
            <div class="feature-card-body">
              <div
                class="feature-icon"
                :style="{ background: feature.iconBg, color: feature.iconColor }"
              >
                <component :is="feature.icon" :size="24" />
              </div>
              <div class="feature-title">{{ feature.title }}</div>
              <ul class="feature-list">
                <li v-for="item in feature.items" :key="item">{{ item }}</li>
              </ul>
            </div>
          </a-card>
        </div>
      </section>
    </div>

  <!-- 有项目状态 -->
  <div v-else class="project-overview">
    <header class="project-header">
      <div class="header-inner">
        <div class="header-left">
          <div class="header-title-row">
            <h1 class="header-title">{{ currentProject.projectName }}</h1>
            <a-tag :color="statusColor(currentProject.status as string)" size="small">
              {{ statusText(currentProject.status as string) }}
            </a-tag>
          </div>
          <p class="header-subtitle">自动化测试平台 · 项目总览与管理中心</p>
        </div>
        <a-space>
          <a-button class="back-workbench-btn" shape="round" @click="goBackToList">
            <template #icon><IconArrowLeft /></template>
            {{ backLabel }}
          </a-button>
        </a-space>
      </div>
    </header>

    <main class="project-main">
      <a-tabs v-model:active-key="activeTab" type="rounded" class="project-tabs">
        <!-- Tab 1: 质量管理 -->
        <a-tab-pane key="qa" title="质量管理">
          <a-spin v-if="qaLoading" :size="40" style="display: block; padding: 60px 0;" />
          <LoadError v-else-if="qaLoadError" @retry="loadQaOverview" />
          <div v-else>
            <!-- QA 核心指标卡片 -->
            <a-grid :cols="{ xs: 2, sm: 2, md: 4 }" :colGap="12" :rowGap="12">
              <a-grid-item>
                <a-card class="qa-stat-card" hoverable role="button" tabindex="0" @click="goToQa('Requirement')" @keydown.enter="goToQa('Requirement')">
                  <div class="qa-stat-icon" style="background: #e8f3ff; color: #165dff;">
                    <icon-list />
                  </div>
                  <div class="qa-stat-num">{{ qaOverview.reqTotal || 0 }}</div>
                  <div class="qa-stat-label">需求总数</div>
                </a-card>
              </a-grid-item>
              <a-grid-item>
                <a-card class="qa-stat-card" hoverable role="button" tabindex="0" @click="goToQa('TestCase')" @keydown.enter="goToQa('TestCase')">
                  <div class="qa-stat-icon" style="background: #e8ffea; color: #00b42a;">
                    <icon-check-circle />
                  </div>
                  <div class="qa-stat-num">{{ qaOverview.caseTotal || 0 }}</div>
                  <div class="qa-stat-label">用例总数</div>
                </a-card>
              </a-grid-item>
              <a-grid-item>
                <a-card class="qa-stat-card" hoverable role="button" tabindex="0" @click="goToQa('Bug')" @keydown.enter="goToQa('Bug')">
                  <div class="qa-stat-icon" style="background: #ffece8; color: #f53f3f;">
                    <icon-bug />
                  </div>
                  <div class="qa-stat-num">
                    {{ qaOverview.bugTotal || 0 }}
                    <a-tag v-if="qaOverview.openBugTotal > 0" color="red" size="small" style="margin-left: 6px;">未关闭 {{ qaOverview.openBugTotal }}</a-tag>
                  </div>
                  <div class="qa-stat-label">BUG 总数</div>
                </a-card>
              </a-grid-item>
              <a-grid-item>
                <a-card class="qa-stat-card" hoverable role="button" tabindex="0" @click="goToQa('TestPlan')" @keydown.enter="goToQa('TestPlan')">
                  <div class="qa-stat-icon" style="background: #fff7e8; color: #ff7d00;">
                    <icon-calendar />
                  </div>
                  <div class="qa-stat-num">{{ qaOverview.planTotal || 0 }}</div>
                  <div class="qa-stat-label">测试计划</div>
                </a-card>
              </a-grid-item>
            </a-grid>

            <!-- BUG 图表 -->
            <a-grid :cols="{ xs: 1, lg: 2 }" :colGap="16" :rowGap="16" style="margin-top: 16px;">
              <a-grid-item>
                <a-card class="chart-card" title="BUG 严重程度分布">
                  <Chart :options="qaBugSeverityOption" height="260px" />
                </a-card>
              </a-grid-item>
              <a-grid-item>
                <a-card class="chart-card" title="BUG 状态分布">
                  <Chart :options="qaBugStatusOption" height="260px" />
                </a-card>
              </a-grid-item>
            </a-grid>

            <!-- 最近动态 -->
            <a-grid :cols="{ xs: 1, lg: 2 }" :colGap="16" :rowGap="16" style="margin-top: 16px;">
              <a-grid-item>
                <a-card class="qa-recent-card" title="最近 BUG">
                  <a-empty v-if="!qaOverview.recentBugs?.length" description="暂无 BUG" />
                  <div v-else class="qa-recent-list">
                    <div
                        v-for="bug in qaOverview.recentBugs"
                        :key="bug.id"
                        class="qa-recent-item"
                        role="button"
                        tabindex="0"
                        @click="router.push({ name: 'Bug', query: { highlight: bug.id } })"
                        @keydown.enter="router.push({ name: 'Bug', query: { highlight: bug.id } })"
                    >
                      <div class="qa-recent-line">
                        <a-tag :color="severityColor(bug.severity)" size="small">{{ severityLabel(bug.severity) }}</a-tag>
                        <a-tag :color="bugStatusColor(bug.status)" size="small" style="margin-left: 4px;">{{ bugStatusLabel(bug.status) }}</a-tag>
                        <span class="qa-recent-code">{{ bug.bugCode }}</span>
                      </div>
                      <div class="qa-recent-title">{{ bug.title }}</div>
                      <div class="qa-recent-meta">{{ bug.reporterName }} · {{ formatDate(bug.createTime) }}</div>
                    </div>
                  </div>
                </a-card>
              </a-grid-item>
              <a-grid-item>
                <a-card class="qa-recent-card" title="最近需求">
                  <a-empty v-if="!qaOverview.recentRequirements?.length" description="暂无需求" />
                  <div v-else class="qa-recent-list">
                    <div
                        v-for="req in qaOverview.recentRequirements"
                        :key="req.id"
                        class="qa-recent-item"
                        role="button"
                        tabindex="0"
                        @click="router.push({ name: 'Requirement', query: { highlight: req.id } })"
                        @keydown.enter="router.push({ name: 'Requirement', query: { highlight: req.id } })"
                    >
                      <div class="qa-recent-line">
                        <a-tag :color="reqPriorityColor(req.priority)" size="small">{{ req.priority }}</a-tag>
                        <a-tag :color="reqStatusColor(req.status)" size="small" style="margin-left: 4px;">{{ reqStatusLabel(req.status) }}</a-tag>
                        <span class="qa-recent-code">{{ req.reqCode }}</span>
                      </div>
                      <div class="qa-recent-title">{{ req.title }}</div>
                      <div class="qa-recent-meta">{{ formatDate(req.createTime) }}</div>
                    </div>
                  </div>
                </a-card>
              </a-grid-item>
            </a-grid>

            <!-- 最近测试计划 -->
            <a-card v-if="qaOverview.recentPlans?.length" class="qa-plan-card" title="最近测试计划" style="margin-top: 16px;">
              <div class="qa-plan-list">
                <div
                    v-for="plan in qaOverview.recentPlans"
                    :key="plan.id"
                    class="qa-plan-item"
                    role="button"
                    tabindex="0"
                    @click="router.push({ name: 'TestPlanDetail', params: { planId: plan.id } })"
                    @keydown.enter="router.push({ name: 'TestPlanDetail', params: { planId: plan.id } })"
                >
                  <div class="qa-plan-info">
                    <div class="qa-plan-name">{{ plan.planName }}</div>
                    <div class="qa-plan-meta">
                      <a-tag :color="planStatusColor(plan.status)" size="small">{{ planStatusLabel(plan.status) }}</a-tag>
                      <span style="margin-left: 8px; color: #86909c;">{{ plan.caseTotal }} 条用例 · 执行率 {{ plan.executeRate }}%</span>
                    </div>
                  </div>
                  <div class="qa-plan-progress">
                    <div class="qa-plan-bar-bg">
                      <div
                          class="qa-plan-bar-pass"
                          :style="{ width: (plan.caseTotal > 0 ? (plan.pass / plan.caseTotal * 100) : 0) + '%' }"
                      />
                      <div
                          class="qa-plan-bar-fail"
                          :style="{ width: (plan.caseTotal > 0 ? (plan.fail / plan.caseTotal * 100) : 0) + '%', left: (plan.caseTotal > 0 ? (plan.pass / plan.caseTotal * 100) : 0) + '%' }"
                      />
                    </div>
                    <div class="qa-plan-legend">
                      <span><span class="dot pass"></span>通过 {{ plan.pass || 0 }}</span>
                      <span><span class="dot fail"></span>失败 {{ plan.fail || 0 }}</span>
                      <span><span class="dot block"></span>阻塞 {{ plan.block || 0 }}</span>
                      <span><span class="dot unexec"></span>未执行 {{ plan.unexec || 0 }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </a-card>
          </div>
        </a-tab-pane>

        <!-- Tab 2: 自动化测试 -->
        <a-tab-pane key="auto" title="自动化测试">
          <a-spin v-if="autoLoading" :size="40" style="display: block; padding: 60px 0;" />
          <LoadError v-else-if="autoLoadError" @retry="loadAutoOverview" />
          <div v-else class="auto-tab-body">
            <!-- 核心自动化指标栏：6 列 -->
            <a-grid
              :cols="{ xs: 1, sm: 2, md: 3, lg: 4, xl: 5, xxl: 6 }"
              :colGap="16"
              :rowGap="16"
              class="auto-metrics-grid"
            >
              <a-grid-item
                v-for="stat in autoStatistics"
                :key="stat.key"
              >
                <a-card
                  class="auto-metric-card"
                  :class="[`metric-${stat.key}`]"
                  hoverable
                  role="button"
                  tabindex="0"
                  @click="handleMetricClick(stat)"
                  @keydown.enter="handleMetricClick(stat)"
                >
                  <div class="metric-card-inner">
                    <div class="metric-icon" :style="{ background: stat.iconBg, color: stat.iconColor }">
                      <component :is="stat.icon" :size="22" />
                    </div>
                    <div class="metric-content">
                      <div class="metric-title">{{ stat.title }}</div>
                      <div class="metric-value" :style="{ color: stat.color }">
                        <template v-if="stat.type === 'percent'">
                          {{ stat.value }}<span class="metric-unit">%</span>
                        </template>
                        <template v-else>
                          <a-statistic
                            :value="stat.value"
                            show-group-separator
                            :value-style="{ fontSize: '28px', fontWeight: 700, color: stat.color }"
                          />
                        </template>
                      </div>
                    </div>
                  </div>
                </a-card>
              </a-grid-item>
            </a-grid>

            <!-- 中间黄金双面板：7 : 3 -->
            <a-grid
              :cols="{ xs: 1, lg: 10 }"
              :colGap="16"
              :rowGap="16"
              class="auto-middle-grid"
            >
              <!-- 左侧：7 天执行趋势 -->
              <a-grid-item :span="{ xs: 24, lg: 7 }">
                <a-card class="chart-card trend-card" title="运行执行趋势（近 7 天）">
                  <Chart :options="sevenDayTrendOption" height="340px" />
                </a-card>
              </a-grid-item>

              <!-- 右侧：实时运行队列 -->
              <a-grid-item :span="{ xs: 24, lg: 3 }">
                <a-card class="queue-card" title="实时运行队列">
                  <div class="queue-body">
                    <a-empty
                      v-if="!runningQueue.length"
                      description="暂无运行中任务"
                      style="padding: 40px 0;"
                    />
                    <div v-else class="queue-list">
                      <div
                        v-for="item in runningQueue"
                        :key="item.id"
                        class="queue-item"
                      >
                        <div class="queue-item-header">
                          <div class="queue-item-title" :title="item.reportName">
                            {{ item.reportName }}
                          </div>
                          <a-tag
                            size="small"
                            :color="item.reportCategory === 'API' ? 'green' : 'blue'"
                          >
                            {{ item.reportCategory || 'UI' }}
                          </a-tag>
                        </div>
                        <div class="queue-item-plan" :title="item.planName">{{ item.planName }}</div>
                        <div class="queue-progress-wrap">
                          <a-progress
                            :percent="item.progress"
                            :color="'#165dff'"
                            :track-color="'#e8f3ff'"
                            size="small"
                            :show-text="false"
                          />
                          <span class="queue-progress-text breathing">{{ item.progress }}% 执行中</span>
                        </div>
                        <div class="queue-actions">
                          <a-popconfirm
                            :content="`确定中断「${item.reportName || item.planName}」的运行吗？`"
                            type="warning"
                            ok-text="中断"
                            cancel-text="取消"
                            @ok="killRunningTask(item)"
                          >
                            <a-button
                              type="primary"
                              status="danger"
                              size="mini"
                              shape="circle"
                              class="queue-kill-btn"
                              aria-label="中断运行"
                              @click.stop
                            >
                              <IconStop />
                            </a-button>
                          </a-popconfirm>
                        </div>
                      </div>
                    </div>
                  </div>
                </a-card>
              </a-grid-item>
            </a-grid>

            <!-- 底部面板：5 : 5 -->
            <a-grid
              :cols="{ xs: 1, lg: 2 }"
              :colGap="16"
              :rowGap="16"
              class="auto-bottom-grid"
            >
              <!-- 左侧：自动化资产及类型分布 -->
              <a-grid-item>
                <a-card class="chart-card asset-card" title="自动化资产及类型分布">
                  <div class="asset-card-body">
                    <div class="asset-donut">
                      <Chart :options="assetDonutOption" height="260px" />
                      <div class="asset-donut-center">
                        <div class="asset-donut-total">
                          {{ assetHoverIndex === 0 ? (autoOverview.uiSceneCount || 0) : assetHoverIndex === 1 ? (autoOverview.apiSceneCount || 0) : assetTotal }}
                        </div>
                        <div class="asset-donut-label">
                          {{ assetHoverIndex === 0 ? `占比 ${uiScenePercent}%` : assetHoverIndex === 1 ? `占比 ${apiScenePercent}%` : '资产总数' }}
                        </div>
                      </div>
                    </div>
                    <div class="asset-legend">
                      <div
                        class="asset-legend-item"
                        :class="{ active: assetHoverIndex === 0 }"
                        @mouseenter="assetHoverIndex = 0"
                        @mouseleave="assetHoverIndex = -1"
                      >
                        <div class="asset-legend-bar">
                          <div class="asset-legend-fill" :style="{ transform: 'scaleX(' + uiScenePercent / 100 + ')', background: '#165dff' }" />
                        </div>
                        <div class="asset-legend-meta">
                          <span class="asset-legend-dot" style="background: #165dff;" />
                          <span class="asset-legend-name">UI 场景</span>
                          <span class="asset-legend-count">{{ autoOverview.uiSceneCount || 0 }}</span>
                          <span class="asset-legend-percent">{{ uiScenePercent }}%</span>
                        </div>
                      </div>
                      <div
                        class="asset-legend-item"
                        :class="{ active: assetHoverIndex === 1 }"
                        @mouseenter="assetHoverIndex = 1"
                        @mouseleave="assetHoverIndex = -1"
                      >
                        <div class="asset-legend-bar">
                          <div class="asset-legend-fill" :style="{ transform: 'scaleX(' + apiScenePercent / 100 + ')', background: '#00b42a' }" />
                        </div>
                        <div class="asset-legend-meta">
                          <span class="asset-legend-dot" style="background: #00b42a;" />
                          <span class="asset-legend-name">API 场景</span>
                          <span class="asset-legend-count">{{ autoOverview.apiSceneCount || 0 }}</span>
                          <span class="asset-legend-percent">{{ apiScenePercent }}%</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </a-card>
              </a-grid-item>

              <!-- 右侧：快速配置通道 -->
              <a-grid-item>
                <a-card class="quick-access-card" title="快速配置通道">
                  <div class="quick-access-grid">
                    <div class="quick-access-item" role="button" tabindex="0" @click="goToAuto('UiSceneList')" @keydown.enter="goToAuto('UiSceneList')">
                      <div class="quick-access-icon" style="background: #e8f3ff; color: #165dff;">
                        <IconDesktop :size="24" />
                      </div>
                      <div class="quick-access-text">
                        <div class="quick-access-title">新建 UI 场景</div>
                        <div class="quick-access-desc">免代码编排自动化</div>
                      </div>
                    </div>
                    <div class="quick-access-item" role="button" tabindex="0" @click="goToAuto('ApiList')" @keydown.enter="goToAuto('ApiList')">
                      <div class="quick-access-icon" style="background: #e8ffea; color: #00b42a;">
                        <IconCode :size="24" />
                      </div>
                      <div class="quick-access-text">
                        <div class="quick-access-title">新建接口用例</div>
                        <div class="quick-access-desc">API 快速设计器</div>
                      </div>
                    </div>
                    <div class="quick-access-item" role="button" tabindex="0" @click="goToAuto('PlanList')" @keydown.enter="goToAuto('PlanList')">
                      <div class="quick-access-icon" style="background: #fff7e8; color: #ff7d00;">
                        <IconCalendar :size="24" />
                      </div>
                      <div class="quick-access-text">
                        <div class="quick-access-title">配置定时任务</div>
                        <div class="quick-access-desc">Cron / CI 触发绑定</div>
                      </div>
                    </div>
                    <div class="quick-access-item" role="button" tabindex="0" @click="goToAuto('ReportList')" @keydown.enter="goToAuto('ReportList')">
                      <div class="quick-access-icon" style="background: #f5e8ff; color: #722ed1;">
                        <IconFile :size="24" />
                      </div>
                      <div class="quick-access-text">
                        <div class="quick-access-title">测试执行报告</div>
                        <div class="quick-access-desc">全局报告中心</div>
                      </div>
                    </div>
                  </div>
                </a-card>
              </a-grid-item>
            </a-grid>
          </div>
        </a-tab-pane>
      </a-tabs>
    </main>
  </div>

  <!-- 新建/编辑项目弹窗 -->
  <ProjectForm
    v-model:visible="projectFormVisible"
    :project="editingProject"
    :can-manage-owner="canManageProjectOwner"
    @success="handleProjectSuccess"
  />
  </a-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { IconList, IconCheckCircle, IconBug, IconCalendar, IconDesktop, IconCode, IconFile, IconArrowLeft, IconPlus, IconStop } from '@arco-design/web-vue/es/icon';
import { Project } from "@/types/domain/Project";
import { getProjectById } from "@/api/MyApi/project";
import { getProjectOverview, getAutoOverview } from "@/api/MyApi/qa";
import { cancelTask } from "@/api/MyApi/task";
import useLoadState from '@/hooks/useLoadState';
import LoadError from '@/components/load-error/index.vue';
import ProjectForm from "@/components/project-form/index.vue";
import { useProjectStore, useUserStore, usePermissionStore } from "@/store";
import useDataStore from "@/store/modules/nav";
import { TEAM_MEMBER_MANAGE } from "@/constants/permissions";
import router from "@/router";
import { useRoute } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import dayjs from 'dayjs';

// ========== 弹窗表单 ==========
const editingProject = ref<Project | null>(null);
const projectFormVisible = ref(false);

// 空状态能力卡片
const featureCards = [
  {
    key: 'qa',
    title: '质量管理',
    icon: IconList,
    iconBg: '#e8f3ff',
    iconColor: '#165dff',
    items: ['需求管理', '用例管理', 'BUG 跟踪'],
  },
  {
    key: 'auto',
    title: '自动化测试',
    icon: IconCode,
    iconBg: '#e8ffea',
    iconColor: '#00b42a',
    items: ['UI 场景', 'API 接口', '定时任务'],
  },
  {
    key: 'plan',
    title: '测试计划',
    icon: IconCalendar,
    iconBg: '#fff7e8',
    iconColor: '#ff7d00',
    items: ['计划执行', '进度追踪', '用例分配'],
  },
  {
    key: 'report',
    title: '测试报告',
    icon: IconFile,
    iconBg: '#f5e8ff',
    iconColor: '#722ed1',
    items: ['执行统计', '趋势分析', '多维报表'],
  },
];

// ========== 当前项目数据 ==========
const currentProject = ref<Project>(new Project());
const projectStore = useProjectStore();
const userStore = useUserStore();
const permissionStore = usePermissionStore();

const canManageProjectOwner = computed(() => {
  const isSuperAdmin = userStore.role === 'super_admin';
  return isSuperAdmin || permissionStore.hasPermission(TEAM_MEMBER_MANAGE);
});

// 返回入口统一回到团队工作台
const backTarget = { name: 'TeamWorkspace' };
const backLabel = '返回工作台';
const goBackToList = () => {
  router.replace(backTarget);
};

// 监听 projectId 变化，自动加载对应项目详情
watch(
  () => projectStore.projectId,
  async (newProjectId) => {
    if (newProjectId) {
      try {
        const { data } = await getProjectById(String(newProjectId));
        currentProject.value = data;
      } catch {
        currentProject.value = new Project();
      }
    } else {
      currentProject.value = new Project();
    }
  },
  { immediate: true }
);

// ========== 处理从依赖项目页面被重定向回来的提示 ==========
const route = useRoute();
onMounted(() => {
  if (route.query.noProject === '1' && !projectStore.hasProjectSelected) {
    Message.warning({
      content: '请先创建项目，再使用自动化测试功能',
      duration: 2000,
    });
    // 清理 query，避免刷新重复提示
    router.replace({
      name: 'ProjectInfo',
      query: {},
    });
  }
});

// ========== 项目标签解析 ==========
const projectTags = computed(() => {
  const tags = currentProject.value.tagClassify;
  if (!tags) return [];
  if (Array.isArray(tags)) return tags;
  try {
    const parsed = JSON.parse(tags as string);
    return Array.isArray(parsed) ? parsed : [];
  } catch { return []; }
});

// ========== 状态映射 ==========
const statusMap: Record<string, { text: string; color: string }> = {
  ACTIVE: { text: '运行中', color: 'green' },
  COMPLETED: { text: '已完成', color: 'blue' },
  SUSPENDED: { text: '已搁置', color: 'orange' },
};
function statusText(status: string): string {
  return statusMap[status]?.text || status || '未知';
}
function statusColor(status: string): string {
  return statusMap[status]?.color || 'gray';
}

// ========== 日期格式化 ==========
function formatDate(date: any): string {
  if (!date) return '-';
  return dayjs(date).format('YYYY-MM-DD HH:mm');
}

// ========== 自动化指标卡片配置 ==========
const autoStatistics = computed(() => {
  const ui = Number(autoOverview.value.uiSceneCount) || 0;
  const apiScene = Number(autoOverview.value.apiSceneCount) || 0;
  const apiCase = Number(autoOverview.value.apiCaseCount) || 0;
  const plan = Number(autoOverview.value.planTotal) || 0;
  const today = Number(autoOverview.value.todayExecuteCount) || 0;
  const uiAvgPass = Number(autoOverview.value.uiAvgPassRate30d) || 0;

  return [
    {
      key: 'uiSceneCount',
      title: 'UI 测试场景',
      type: 'number',
      value: ui,
      icon: IconDesktop,
      iconBg: '#e8f3ff',
      iconColor: '#165dff',
      color: '#165dff',
      route: { name: 'UiSceneList' },
    },
    {
      key: 'apiSceneCount',
      title: 'API 测试场景',
      type: 'number',
      value: apiScene,
      icon: IconCode,
      iconBg: '#e8ffea',
      iconColor: '#00b42a',
      color: '#00b42a',
      route: { name: 'ApiSceneList' },
    },
    {
      key: 'apiCaseCount',
      title: 'API 测试用例',
      type: 'number',
      value: apiCase,
      icon: IconFile,
      iconBg: '#e5f9f9',
      iconColor: '#14c9c9',
      color: '#14c9c9',
      route: { name: 'ApiList' },
    },
    {
      key: 'planTotal',
      title: '自动化任务',
      type: 'number',
      value: plan,
      icon: IconCalendar,
      iconBg: '#fff7e8',
      iconColor: '#ff7d00',
      color: '#ff7d00',
      route: { name: 'PlanList' },
    },
    {
      key: 'todayExecuteCount',
      title: '今日执行次数',
      type: 'number',
      value: today,
      icon: IconCheckCircle,
      iconBg: '#f5e8ff',
      iconColor: '#722ed1',
      color: '#722ed1',
      route: { name: 'ReportList' },
    },
    {
      key: 'uiAvgPassRate30d',
      title: 'UI 平均通过率',
      type: 'percent',
      value: uiAvgPass,
      icon: IconCheckCircle,
      iconBg: '#e8ffea',
      iconColor: '#00b42a',
      color: '#00b42a',
      route: { name: 'ReportList' },
    },
  ];
});

const handleMetricClick = (stat: any) => {
  if (stat.route) {
    router.push(stat.route);
  }
};

// 实时运行队列
const runningQueue = computed(() => autoOverview.value.runningQueue || []);

// 中断运行任务：对接 /api/task/stop/{planId}
const killRunningTask = async (item: any) => {
  if (!item.planId) {
    Message.warning('未获取到任务信息，无法中断');
    return;
  }
  try {
    const res: any = await cancelTask(item.planId);
    if (res?.data || res?.code === 200) {
      Message.success(`已中断「${item.reportName || item.planName}」`);
      loadAutoOverview();
    } else {
      Message.warning('任务可能已结束，未执行中断');
    }
  } catch (e) {
    console.error(e);
    Message.error('中断失败，请稍后重试');
  }
};

// 资产分布悬浮索引
const assetHoverIndex = ref(-1);

// ========== Tab 切换 ==========
const activeTab = ref('qa');

// ========== QA 概览数据 ==========
const qaOverview = ref<any>({});
const { loading: qaLoading, loadError: qaLoadError, track: trackQa } = useLoadState();

const loadQaOverview = async () => {
  if (!projectStore.projectId) return;
  const res: any = await trackQa(getProjectOverview(projectStore.projectId));
  if (res && res.code !== 200) qaLoadError.value = true;
  if (res?.code === 200 && res.data) {
    qaOverview.value = res.data;
  }
};

// ========== 自动化概览数据 ==========
const autoOverview = ref<any>({});
const { loading: autoLoading, loadError: autoLoadError, track: trackAuto } = useLoadState();

const loadAutoOverview = async () => {
  if (!projectStore.projectId) return;
  const res: any = await trackAuto(getAutoOverview(projectStore.projectId));
  if (res && res.code !== 200) autoLoadError.value = true;
  if (res?.code === 200 && res.data) {
    autoOverview.value = res.data;
  }
};

// 监听项目变化时加载 QA 概览 + 自动化概览
watch(
  () => projectStore.projectId,
  async (newProjectId) => {
    if (newProjectId) {
      await loadQaOverview();
      await loadAutoOverview();
    } else {
      qaOverview.value = {};
      autoOverview.value = {};
    }
  },
  { immediate: true }
);

// ========== 自动化测试图表配置 ==========

// 7 天执行趋势 - 堆叠柱状图
const sevenDayTrendOption = computed(() => {
  const trend = autoOverview.value.sevenDayTrend || [];
  const dates = trend.map((d: any) => d.displayDate);
  const successData = trend.map((d: any) => d.success || 0);
  const failData = trend.map((d: any) => d.fail || 0);
  const hasData = successData.some((v: number) => v > 0) || failData.some((v: number) => v > 0);

  if (!hasData) {
    return {
      title: { text: '近 7 天暂无执行数据', left: 'center', top: 'center', textStyle: { color: '#86909c', fontSize: 14 } },
      xAxis: { type: 'category', data: dates, show: true },
      yAxis: { type: 'value', show: true },
      series: [],
    };
  }

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(0,0,0,0.75)',
      borderColor: 'rgba(0,0,0,0.75)',
      textStyle: { color: '#fff' },
      formatter: (params: any[]) => {
        const dateItem = trend[params[0]?.dataIndex];
        const total = (dateItem?.success || 0) + (dateItem?.fail || 0);
        const success = dateItem?.success || 0;
        const fail = dateItem?.fail || 0;
        const successRate = total > 0 ? ((success / total) * 100).toFixed(1) : '0.0';
        const failRate = total > 0 ? ((fail / total) * 100).toFixed(1) : '0.0';
        return `<div style="font-weight:600;margin-bottom:4px;">${dateItem?.date || ''}</div>
                <div>总运行：${total} 次</div>
                <div>成功运行：${success} 次 (${successRate}%)</div>
                <div>失败运行：${fail} 次 (${failRate}%)</div>`;
      },
    },
    grid: { left: '2%', right: '4%', bottom: '4%', top: '12%', containLabel: true },
    legend: { top: 0, right: 0, data: ['成功', '失败'] },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#86909c' },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#86909c' },
    },
    series: [
      {
        name: '成功',
        type: 'bar',
        stack: 'total',
        barWidth: '45%',
        itemStyle: { color: '#00b42a', borderRadius: [0, 0, 0, 0] },
        emphasis: { focus: 'series' },
        data: successData,
      },
      {
        name: '失败',
        type: 'bar',
        stack: 'total',
        barWidth: '45%',
        itemStyle: { color: '#f53f3f', borderRadius: [4, 4, 0, 0] },
        emphasis: { focus: 'series' },
        data: failData,
      },
    ],
  };
});

// 资产分布 - 环形图
const assetTotal = computed(() => {
  return (Number(autoOverview.value.uiSceneCount) || 0) + (Number(autoOverview.value.apiSceneCount) || 0);
});

const uiScenePercent = computed(() => {
  const total = assetTotal.value || 1;
  const ui = Number(autoOverview.value.uiSceneCount) || 0;
  return Math.round((ui / total) * 1000) / 10;
});

const apiScenePercent = computed(() => {
  const total = assetTotal.value || 1;
  const api = Number(autoOverview.value.apiSceneCount) || 0;
  return Math.round((api / total) * 1000) / 10;
});

const assetDonutOption = computed(() => {
  const ui = Number(autoOverview.value.uiSceneCount) || 0;
  const api = Number(autoOverview.value.apiSceneCount) || 0;
  const hasData = ui + api > 0;

  if (!hasData) {
    return {
      title: { text: '暂无自动化资产', left: 'center', top: 'center', textStyle: { color: '#86909c', fontSize: 14 } },
      series: [],
    };
  }

  return {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    color: ['#165dff', '#00b42a'],
    series: [
      {
        name: '资产分布',
        type: 'pie',
        radius: ['58%', '78%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 3 },
        label: { show: false },
        emphasis: {
          scale: true,
          scaleSize: 6,
          label: { show: true, formatter: '{b}\n{c}', fontSize: 14, fontWeight: 'bold' },
        },
        data: [
          { value: ui, name: 'UI 场景' },
          { value: api, name: 'API 场景' },
        ],
      },
    ],
  };
});

// 快捷跳转
const goToAuto = (name: string) => {
  router.push({ name });
};

// ========== QA 概览图表 ==========
const qaBugSeverityOption = computed(() => {
  const data = qaOverview.value.bugBySeverity || {};
  const entries = Object.entries(data).filter(([, v]: any) => v > 0);
  if (!entries.length) {
    return { title: { text: '暂无 BUG 数据', left: 'center', top: 'center', textStyle: { color: '#86909c', fontSize: 14 } } };
  }
  const severityMap: Record<string, string> = { FATAL: '致命', SERIOUS: '严重', NORMAL: '一般', TIPS: '提示' };
  const colorMap: Record<string, string> = { FATAL: '#f53f3f', SERIOUS: '#ff7d00', NORMAL: '#165DFF', TIPS: '#86909c' };
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, left: 'center', itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12 } },
    color: entries.map(([k]: any) => colorMap[k] || '#86909c'),
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{c}', fontSize: 12 },
      data: entries.map(([k, v]: any) => ({ name: severityMap[k] || k, value: v })),
    }],
  };
});

const qaBugStatusOption = computed(() => {
  const data = qaOverview.value.bugByStatus || {};
  const entries = Object.entries(data).filter(([, v]: any) => v > 0);
  if (!entries.length) {
    return { title: { text: '暂无 BUG 数据', left: 'center', top: 'center', textStyle: { color: '#86909c', fontSize: 14 } } };
  }
  const statusMap: Record<string, string> = {
    NEW: '新建', CONFIRMED: '已确认', FIXING: '修复中', FIXED: '已修复',
    VERIFIED: '已验证', CLOSED: '已关闭', REJECTED: '已驳回'
  };
  const colorMap: Record<string, string> = {
    NEW: '#f53f3f', CONFIRMED: '#ff7d00', FIXING: '#f7ba1e', FIXED: '#165DFF',
    VERIFIED: '#14c9c9', CLOSED: '#86909c', REJECTED: '#c9cdd4'
  };
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
    xAxis: { type: 'category', data: entries.map(([k]: any) => statusMap[k] || k), axisLabel: { fontSize: 11, rotate: 30 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      barWidth: '50%',
      data: entries.map(([k, v]: any) => ({ value: v, itemStyle: { color: colorMap[k] || '#86909c', borderRadius: [4, 4, 0, 0] } })),
      label: { show: true, position: 'top', fontSize: 12 },
    }],
  };
});

// 快捷跳转
const goToQa = (name: string) => {
  router.push({ name });
};

const severityLabel = (s: string) => {
  const map: Record<string, string> = { FATAL: '致命', SERIOUS: '严重', NORMAL: '一般', TIPS: '提示' };
  return map[s] || s;
};
const severityColor = (s: string) => {
  const map: Record<string, string> = { FATAL: 'red', SERIOUS: 'orange', NORMAL: 'blue', TIPS: 'gray' };
  return map[s] || 'gray';
};
const bugStatusLabel = (s: string) => {
  const map: Record<string, string> = {
    NEW: '新建', CONFIRMED: '已确认', FIXING: '修复中', FIXED: '已修复',
    VERIFIED: '已验证', CLOSED: '已关闭', REJECTED: '已驳回'
  };
  return map[s] || s;
};
const bugStatusColor = (s: string) => {
  const map: Record<string, string> = {
    NEW: 'red', CONFIRMED: 'orange', FIXING: 'gold', FIXED: 'blue',
    VERIFIED: 'cyan', CLOSED: 'gray', REJECTED: 'gray'
  };
  return map[s] || 'gray';
};
const reqStatusLabel = (s: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿', REVIEWING: '评审中', CONFIRMED: '已确认', DEVELOPING: '开发中',
    TESTING: '测试中', RELEASED: '已上线', CLOSED: '已关闭'
  };
  return map[s] || s;
};
const reqStatusColor = (s: string) => {
  const map: Record<string, string> = {
    DRAFT: 'gray', REVIEWING: 'cyan', CONFIRMED: 'blue', DEVELOPING: 'gold',
    TESTING: 'purple', RELEASED: 'green', CLOSED: 'gray'
  };
  return map[s] || 'gray';
};
const reqPriorityColor = (p: string) => {
  const map: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue', P3: 'gray' };
  return map[p] || 'gray';
};
const planStatusLabel = (s: string) => {
  const map: Record<string, string> = { DRAFT: '草稿', READY: '就绪', RUNNING: '执行中', COMPLETED: '已完成' };
  return map[s] || s;
};
const planStatusColor = (s: string) => {
  const map: Record<string, string> = { DRAFT: 'gray', READY: 'blue', RUNNING: 'gold', COMPLETED: 'green' };
  return map[s] || 'gray';
};

// ========== 新建/编辑项目 ==========
const handleCreateOrUpdateProject = (project: Project | null) => {
  editingProject.value = project;
  projectFormVisible.value = true;
};

const handleProjectSuccess = async (project?: Project) => {
  const dataStore = useDataStore();
  await dataStore.fetchData();

  if (project?.id) {
    // 编辑成功：刷新当前展示的项目详情
    if (project.id === projectStore.projectId) {
      try {
        const { data } = await getProjectById(String(project.id));
        currentProject.value = data;
      } catch {
        currentProject.value = new Project();
      }
    }
  } else {
    // 新建成功：自动切换到最新的项目（列表通常按创建时间倒序）
    if (dataStore.data && dataStore.data.length > 0) {
      const latestProject = dataStore.data[0] as Project;
      if (latestProject.id) {
        projectStore.setProject(latestProject.id, latestProject.projectName);
        try {
          const { data } = await getProjectById(String(latestProject.id));
          currentProject.value = data;
        } catch {
          currentProject.value = new Project();
        }
      }
    }
  }
};
</script>

<style scoped>
/* 空状态 */
.empty-state {
  min-height: var(--page-container-height, calc(100vh - 60px));
  padding: 0;
  background-color: #f5f6f7;
  overflow-y: auto;
}

/* Hero 分割布局 */
.hero-section {
  max-width: 1200px;
  margin: 0 auto;
  padding: 4rem 2rem 3rem;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4rem;
  align-items: center;
}
.hero-content {
  max-width: 520px;
}
.hero-eyebrow {
  display: inline-block;
  font-size: 0.85rem;
  font-weight: 600;
  color: #165dff;
  background: #e8f3ff;
  padding: 0.35rem 0.85rem;
  border-radius: 2rem;
  margin-bottom: 1.25rem;
}
.hero-title {
  font-size: 2.75rem;
  font-weight: 700;
  line-height: 1.2;
  color: var(--color-text-1);
  margin: 0 0 1.25rem;
  letter-spacing: -0.02em;
}
.hero-tagline {
  font-size: 1.05rem;
  line-height: 1.7;
  color: var(--color-text-3);
  margin: 0 0 2rem;
}
.hero-actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

/* 右侧视觉预览 */
.hero-visual {
  display: flex;
  justify-content: center;
  align-items: center;
}
.preview-window {
  width: 100%;
  max-width: 480px;
  background: white;
  border-radius: 1rem;
  box-shadow: 0 20px 50px -12px rgba(0, 0, 0, 0.1);
  border: 1px solid #e5e7eb;
  overflow: hidden;
}
.preview-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}
.preview-dots {
  display: flex;
  gap: 0.4rem;
}
.preview-dots span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #e5e7eb;
}
.preview-dots span:nth-child(1) { background: #f87171; }
.preview-dots span:nth-child(2) { background: #fbbf24; }
.preview-dots span:nth-child(3) { background: #34d399; }
.preview-title {
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--color-text-2);
}
.preview-body {
  padding: 1.5rem;
}
.preview-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin-bottom: 1.5rem;
}
.preview-stat {
  text-align: center;
  padding: 1rem;
  background: #f9fafb;
  border-radius: 0.75rem;
}
.preview-stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--color-text-1);
}
.preview-stat-label {
  font-size: 0.75rem;
  color: var(--color-text-3);
  margin-top: 0.25rem;
}
.preview-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 0.75rem;
  height: 120px;
  padding: 1rem;
  background: #f9fafb;
  border-radius: 0.75rem;
}
.preview-chart-bar {
  flex: 1;
  background: linear-gradient(180deg, #165dff 0%, #4da6ff 100%);
  border-radius: 0.25rem 0.25rem 0 0;
  opacity: 0.8;
}

/* Features 区域 */
.features-section {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem 2rem 4rem;
  border-top: 1px solid #e5e7eb;
}
.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
}
.feature-card {
  border-radius: 1rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  border: 1px solid #e5e7eb;
  background: white;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}
.feature-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}
.feature-card :deep(.arco-card-body) {
  padding: 1.25rem;
}
.feature-card-body {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.feature-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0.75rem;
}
.feature-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-text-1);
}
.feature-list {
  margin: 0;
  padding-left: 1rem;
  font-size: 0.8rem;
  color: var(--color-text-3);
  line-height: 1.8;
}

/* 响应式 */
@media (max-width: 991px) {
  .hero-section {
    grid-template-columns: 1fr;
    gap: 2.5rem;
    padding: 2.5rem 1.5rem 2rem;
    text-align: center;
  }
  .hero-content {
    max-width: 600px;
    margin: 0 auto;
  }
  .hero-title {
    font-size: 2.25rem;
  }
  .hero-actions {
    justify-content: center;
  }
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 576px) {
  .empty-state {
    padding: 0;
  }
  .hero-section {
    padding: 2rem 1rem 1.5rem;
  }
  .hero-title {
    font-size: 1.75rem;
  }
  .features-section {
    padding: 1.5rem 1rem 2rem;
  }
  .features-grid {
    grid-template-columns: 1fr;
  }
  .preview-stats {
    grid-template-columns: 1fr;
  }
}

/* 项目概览 */
.project-overview {
  background-color: #f5f6f7;
}
.project-header {
  position: sticky;
  top: 0;
  z-index: 10;
  border-bottom: 1px solid #e5e7eb;
  background-color: white;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1);
}
.header-inner {
  margin: 0 auto;
  padding: 1rem 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.header-left { min-width: 0; }
.header-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.header-title {
  font-size: 1.5rem;
  font-weight: 700;
  line-height: 2rem;
  margin: 0;
}
.header-subtitle {
  font-size: 0.875rem;
  color: #6b7280;
  margin-top: 0.25rem;
  margin-bottom: 0;
}

.project-main {
  padding: 1.5rem;
}

/* Tabs */
.project-tabs :deep(.arco-tabs-content) {
  padding-top: 16px;
}
.project-tabs :deep(.arco-tabs-nav-tab-list) {
  gap: 8px;
}
.project-tabs :deep(.arco-tabs-tab) {
  font-size: 14px;
  font-weight: 500;
  padding: 8px 20px;
}
.project-tabs :deep(.arco-tabs-tab-active) {
  font-weight: 600;
}

/* 统计卡片 */
.stat-card {
  transition: all 0.3s ease;
  border-radius: 12px;
}
.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px -8px rgba(0, 0, 0, 0.12);
}
.stat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.stat-title {
  font-size: 14px;
  color: var(--color-text-2);
  font-weight: 500;
}
.stat-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60px;
}
.stat-circle {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-circle-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-1);
}

/* 图表卡片 */
.chart-card {
  border-radius: 12px;
}
.chart-card :deep(.arco-card-body) {
  padding: 8px 16px 16px;
}

/* 快捷入口 */
.auto-entry-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  padding: 4px 0;
}

/* 质量指标进度条 */
.quality-progress-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 8px 0;
}
.quality-progress-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.quality-progress-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-1);
}

/* 响应式 */
@media (max-width: 768px) {
  .header-inner {
    flex-direction: column;
    align-items: flex-start;
  }
  .header-title { font-size: 1.25rem; }
  .project-main { padding: 1rem; }
}

/* QA 统计卡片 */
.qa-stat-card {
  text-align: center;
  padding: 16px 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 10px;
}
.qa-stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px -4px rgba(0,0,0,0.1);
}

/* 键盘可达卡片的焦点态 */
.qa-stat-card:focus-visible,
.qa-recent-item:focus-visible,
.qa-plan-item:focus-visible,
.auto-metric-card:focus-visible,
.quick-access-item:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: -2px;
}
.qa-stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  margin-bottom: 8px;
}
.qa-stat-num {
  font-size: 22px;
  font-weight: 700;
  color: #1d2129;
  line-height: 1.2;
}
.qa-stat-label {
  font-size: 12px;
  color: #86909c;
  margin-top: 4px;
}

/* 最近动态 */
.qa-recent-card :deep(.arco-card-body) {
  padding: 8px 16px 16px;
}
.qa-recent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.qa-recent-item {
  padding: 10px 12px;
  background: #f7f8fa;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}
.qa-recent-item:hover {
  background: #f2f3f5;
}
.qa-recent-line {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
}
.qa-recent-code {
  font-size: 12px;
  color: rgb(var(--primary-6));
  font-weight: 600;
  margin-left: 4px;
}
.qa-recent-title {
  font-size: 13px;
  color: #1d2129;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.qa-recent-meta {
  font-size: 12px;
  color: #86909c;
  margin-top: 2px;
}

/* 最近测试计划 */
.qa-plan-card :deep(.arco-card-body) {
  padding: 8px 16px 16px;
}
.qa-plan-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.qa-plan-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f7f8fa;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  gap: 16px;
}
.qa-plan-item:hover {
  background: #f2f3f5;
}
.qa-plan-name {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
}
.qa-plan-meta {
  margin-top: 4px;
  font-size: 12px;
}
.qa-plan-progress {
  flex-shrink: 0;
  width: 200px;
}
.qa-plan-bar-bg {
  position: relative;
  height: 8px;
  background: #e5e6eb;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
}
.qa-plan-bar-pass {
  height: 100%;
  background: #00b42a;
  border-radius: 4px 0 0 4px;
}
.qa-plan-bar-fail {
  height: 100%;
  background: #f53f3f;
}
.qa-plan-legend {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  font-size: 11px;
  color: #86909c;
  justify-content: center;
}
.qa-plan-legend .dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 2px;
}
.qa-plan-legend .dot.pass { background: #00b42a; }
.qa-plan-legend .dot.fail { background: #f53f3f; }
.qa-plan-legend .dot.block { background: #ff7d00; }
.qa-plan-legend .dot.unexec { background: #c9cdd4; }

@media (max-width: 768px) {
  .qa-plan-item {
    flex-direction: column;
    align-items: flex-start;
  }
  .qa-plan-progress {
    width: 100%;
  }
}

/* ==================== 自动化测试 Tab 新布局 ==================== */

/* 返回工作台按钮 */
.back-workbench-btn {
  color: #165dff;
  background: transparent;
  border-color: #165dff;
  transition: all 0.2s ease;
}
.back-workbench-btn:hover {
  background: rgba(22, 93, 255, 0.05);
  border-color: #165dff;
  color: #165dff;
}

/* Tabs Active Bar 蓝色过渡 */
.project-tabs :deep(.arco-tabs-nav-ink) {
  background-color: #165dff;
  transition: all 0.2s ease;
}
.project-tabs :deep(.arco-tabs-tab-active) {
  color: #165dff;
}

.auto-tab-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 6 列指标卡片 */
.auto-metrics-grid {
  width: 100%;
}
.auto-metric-card {
  cursor: pointer;
  border-radius: 12px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.auto-metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px -6px rgba(0, 0, 0, 0.12);
}
.auto-metric-card :deep(.arco-card-body) {
  padding: 16px;
}
.metric-card-inner {
  display: flex;
  align-items: center;
  gap: 14px;
}
.metric-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.metric-content {
  flex: 1;
  min-width: 0;
}
.metric-title {
  font-size: 13px;
  color: var(--color-text-2);
  margin-bottom: 4px;
}
.metric-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
}
.metric-unit {
  font-size: 16px;
  font-weight: 500;
  margin-left: 2px;
}

/* 中间黄金双面板 */
.auto-middle-grid {
  width: 100%;
}
.trend-card :deep(.arco-card-body) {
  padding: 8px 16px 16px;
}

/* 实时运行队列 */
.queue-card :deep(.arco-card-body) {
  padding: 8px 16px 16px;
}
.queue-body {
  max-height: 340px;
  overflow-y: auto;
}
.queue-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.queue-item {
  position: relative;
  padding: 14px;
  background: #f7f8fa;
  border-radius: 10px;
  transition: background 0.2s ease;
  overflow: hidden;
}
.queue-item:hover {
  background: #f2f3f5;
}
.queue-item:hover .queue-kill-btn,
.queue-item:focus-within .queue-kill-btn,
.queue-kill-btn:focus-visible {
  opacity: 1;
  transform: translateX(0);
}
.queue-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.queue-item-title {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.queue-item-plan {
  font-size: 12px;
  color: #86909c;
  margin-bottom: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.queue-progress-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}
.queue-progress-wrap :deep(.arco-progress) {
  flex: 1;
}
.queue-progress-text {
  font-size: 12px;
  color: #165dff;
  font-weight: 600;
  white-space: nowrap;
}
.queue-actions {
  position: absolute;
  top: 14px;
  right: 14px;
}
.queue-kill-btn {
  opacity: 0;
  transform: translateX(10px);
  transition: all 0.2s ease;
}

/* 呼吸灯 */
.breathing {
  animation: breathing 1.6s ease-in-out infinite;
}
@keyframes breathing {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}

/* 底部面板 */
.auto-bottom-grid {
  width: 100%;
}
.asset-card :deep(.arco-card-body) {
  padding: 8px 16px 16px;
}
.asset-card-body {
  display: flex;
  align-items: center;
  gap: 24px;
}
.asset-donut {
  position: relative;
  width: 200px;
  height: 200px;
  flex-shrink: 0;
}
.asset-donut-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
}
.asset-donut-total {
  font-size: 32px;
  font-weight: 700;
  color: #1d2129;
  line-height: 1.1;
}
.asset-donut-label {
  font-size: 12px;
  color: #86909c;
  margin-top: 4px;
}
.asset-legend {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}
.asset-legend-item {
  cursor: pointer;
  transition: opacity 0.2s ease;
}
.asset-legend-item:hover,
.asset-legend-item.active {
  opacity: 1;
}
.asset-legend-item:not(:hover):not(.active) {
  opacity: 0.75;
}
.asset-legend-bar {
  height: 8px;
  background: #e5e6eb;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 8px;
}
.asset-legend-fill {
  width: 100%;
  height: 100%;
  border-radius: 4px;
  transform-origin: left center;
  transition: transform 0.4s ease;
}
.asset-legend-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.asset-legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.asset-legend-name {
  flex: 1;
  color: #1d2129;
  font-weight: 500;
}
.asset-legend-count {
  color: #1d2129;
  font-weight: 600;
}
.asset-legend-percent {
  color: #86909c;
  font-size: 12px;
}

/* 快速配置通道 */
.quick-access-card :deep(.arco-card-body) {
  padding: 16px;
}
.quick-access-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.quick-access-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 16px;
  border: 1px solid #e5e6eb;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}
.quick-access-item:hover {
  transform: scale(1.01);
  border-color: #165dff;
  box-shadow: 0 4px 14px -4px rgba(22, 93, 255, 0.15);
}
.quick-access-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.quick-access-text {
  flex: 1;
  min-width: 0;
}
.quick-access-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 4px;
}
.quick-access-desc {
  font-size: 12px;
  color: #86909c;
}

/* 响应式适配 */
@media (max-width: 1200px) {
  .auto-tab-body {
    gap: 14px;
  }
}

@media (max-width: 962px) {
  .asset-card-body {
    flex-direction: column;
    align-items: center;
  }
  .asset-legend {
    width: 100%;
  }
}

@media (max-width: 576px) {
  .metric-card-inner {
    gap: 10px;
  }
  .metric-icon {
    width: 38px;
    height: 38px;
  }
  .metric-value :deep(.arco-statistic-value) {
    font-size: 24px !important;
  }
  .quick-access-grid {
    grid-template-columns: 1fr;
  }
  .asset-donut {
    width: 160px;
    height: 160px;
  }
}
</style>
