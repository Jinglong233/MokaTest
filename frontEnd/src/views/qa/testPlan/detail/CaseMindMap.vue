<template>
  <div class="case-mindmap-wrapper">
    <div ref="mapRef" class="case-mindmap-canvas"></div>
    <!-- 画布右上角悬浮工具栏：加载提示 + 主题切换 -->
    <div class="case-mindmap-toolbar-top">
      <span v-if="loadingDetail" class="hint loading">{{ $t('qa.testPlan.mindmap.loadingDetail') }}</span>
      <a-select v-model="currentTheme" size="mini" style="width: 96px" @change="onThemeChange">
        <a-option v-for="t in THEME_OPTIONS" :key="t.value" :value="t.value">{{ t.label }}</a-option>
      </a-select>
    </div>
    <!-- 选中用例节点后出现的操作按钮组：评定 / 关联BUG / 新建BUG（节点下方，选中期间常驻） -->
    <div
      v-if="hoverBtn.visible"
      class="case-hover-group"
      :style="{ left: hoverBtn.x + 'px', top: hoverBtn.y + 'px' }"
    >
      <a-tooltip :content="$t('qa.testPlan.mindmap.assess')" :mini="true">
        <a-button v-permission="'qa:testplan:execute'" type="primary" shape="circle" size="mini" @click.stop="openExecModal(hoverBtn.node)">
          <icon-check-circle />
        </a-button>
      </a-tooltip>
      <a-tooltip :content="$t('qa.testPlan.mindmap.detail')" :mini="true">
        <a-button shape="circle" size="mini" @click.stop="openDetailDrawer">
          <icon-eye />
        </a-button>
      </a-tooltip>
      <!-- BUG 下拉：新建BUG / 关联BUG -->
      <a-dropdown position="bottom" @select="handleBugAction">
        <a-tooltip content="BUG" :mini="true">
          <a-button v-permission="'qa:bug:view'" shape="circle" size="mini" @click.stop>
            <icon-bug />
          </a-button>
        </a-tooltip>
        <template #content>
          <a-doption v-permission="'qa:bug:create'" value="new">{{ $t('qa.bug.add') }}</a-doption>
          <a-doption v-permission="'qa:bug:view'" value="bind">{{ $t('qa.testPlan.mindmap.bindBug') }}</a-doption>
        </template>
      </a-dropdown>
      <a-tooltip :content="$t('qa.testPlan.mindmap.changeExecutor')" :mini="true">
        <a-button v-permission="'qa:testplan:update'" shape="circle" size="mini" @click.stop="openExecutorModal">
          <icon-user />
        </a-button>
      </a-tooltip>
      <a-popconfirm :content="$t('qa.testPlan.mindmap.removeConfirm')" type="warning" @ok="handleRemoveCase">
        <a-tooltip :content="$t('qa.testPlan.mindmap.unrelateCase')" :mini="true">
          <a-button v-permission="'qa:testplan:update'" status="danger" shape="circle" size="mini" @click.stop>
            <icon-delete />
          </a-button>
        </a-tooltip>
      </a-popconfirm>
    </div>

    <!-- 右键用例节点：完整执行评定面板（对齐执行页详情 tab + 底部执行面板） -->
    <a-modal
      v-model:visible="execModal.visible"
      :title="$t('qa.testPlan.mindmap.execTitle', { name: execModal.caseName })"
      :width="900"
      :mask-closable="false"
      @cancel="execModal.visible = false"
    >
      <div>
        <div class="dd-section-title">{{ $t('qa.testCase.preCondition') }}</div>
        <div v-if="execModal.detail?.preCondition" class="pre-view" v-html="execModal.detail.preCondition"></div>
        <div v-else class="pre-empty">{{ $t('qa.testPlan.none') }}</div>

        <div class="dd-section-title">{{ $t('qa.testCase.steps') }}</div>
        <a-table
          v-if="execModal.stepEdits.length"
          :data="execModal.stepEdits"
          :columns="stepColumns"
          :pagination="false"
          size="small"
          row-key="index"
          :bordered="{ cell: true }"
        >
          <template #stepNo="{ rowIndex }">
            <span class="step-no">{{ rowIndex + 1 }}</span>
          </template>
          <template #stepDesc="{ record }">
            <div v-html="record.step || '-'"></div>
          </template>
          <template #stepExpected="{ record }">
            <div v-html="record.expected || '-'"></div>
          </template>
          <template #stepActual="{ record }">
            <a-textarea
              v-model="record.actualResult"
              :auto-size="{ minRows: 1, maxRows: 4 }"
              :placeholder="$t('qa.testPlan.actualResultPlaceholder')"
              allow-clear
            />
          </template>
          <template #stepResult="{ record }">
            <a-select v-model="record.stepResult" :placeholder="$t('qa.testPlan.result.unexecuted')" allow-clear size="small">
              <a-option value="PASS">{{ $t('qa.testPlan.result.success') }}</a-option>
              <a-option value="FAIL">{{ $t('qa.testPlan.result.fail') }}</a-option>
              <a-option value="BLOCK">{{ $t('qa.testPlan.result.block') }}</a-option>
            </a-select>
          </template>
        </a-table>
        <a-empty v-else :description="$t('qa.testPlan.noSteps')" />

        <div class="dd-section-title">{{ $t('qa.testPlan.execResult') }}</div>
        <div class="exec-form-row">
          <a-radio-group v-model="execModal.result">
            <a-radio value="PASS"><span class="result-text pass">{{ $t('qa.testPlan.result.success') }}</span></a-radio>
            <a-radio value="FAIL"><span class="result-text fail">{{ $t('qa.testPlan.result.fail') }}</span></a-radio>
            <a-radio value="BLOCK"><span class="result-text block">{{ $t('qa.testPlan.result.block') }}</span></a-radio>
          </a-radio-group>
          <a-textarea
            v-model="execModal.remark"
            :auto-size="{ minRows: 2, maxRows: 4 }"
            :placeholder="$t('qa.testPlan.exec.remarkPlaceholder')"
            style="flex: 1"
          />
        </div>
      </div>
      <template #footer>
        <a-space>
          <a-button @click="execModal.visible = false">{{ $t('qa.common.cancel') }}</a-button>
          <a-button v-permission="'qa:testplan:execute'" type="primary" :loading="execModal.submitting" @click="handleExecSubmit">{{ $t('qa.testPlan.exec.submit') }}</a-button>
        </a-space>
      </template>
    </a-modal>

    <!-- 关联已有BUG抽屉（共享组件，同执行页） -->
    <BugBindDrawer
      v-model:visible="bugBindVisible"
      :plan-case-id="bugCaseInfo?.planCaseId || null"
    />

    <!-- 新建BUG抽屉（同执行页：BugForm，planCaseId/testCaseId 通过 initialData 带入） -->
    <a-drawer
      v-model:visible="newBugVisible"
      :title="$t('qa.bug.add')"
      :width="920"
      :footer="true"
      :body-style="{ padding: '0', height: '100%', overflow: 'hidden' }"
      @cancel="newBugVisible = false"
    >
      <div class="new-bug-body">
        <BugForm
          v-if="newBugVisible"
          ref="newBugFormRef"
          :initial-data="{
            projectId: projectStore.getProjectId,
            testCaseId: bugCaseInfo?.testCaseId,
            planCaseId: bugCaseInfo?.planCaseId,
            status: 'NEW',
            severity: 'NORMAL',
            priority: 'MEDIUM',
            environment: 'TEST',
            tags: [],
          }"
        />
      </div>
      <template #footer>
        <a-space>
          <a-button @click="newBugVisible = false">{{ $t('qa.common.cancel') }}</a-button>
          <a-button type="primary" :loading="newBugSaving" @click="handleNewBugSave">{{ $t('qa.common.save') }}</a-button>
        </a-space>
      </template>
    </a-drawer>

    <!-- 修改执行人弹窗 -->
    <a-modal
      v-model:visible="executorModal.visible"
      :title="$t('qa.testPlan.mindmap.changeExecutor')"
      :width="420"
      :mask-closable="false"
      @cancel="executorModal.visible = false"
    >
      <a-select
        v-model="executorModal.executorId"
        allow-search
        allow-clear
        :filter-option="false"
        :placeholder="$t('qa.testPlan.userSearchPlaceholder')"
        style="width: 100%"
        @search="loadExecutorOptions"
      >
        <a-option v-for="u in executorOptions" :key="u.id" :value="u.id">{{ u.nickname || u.username }}</a-option>
      </a-select>
      <template #footer>
        <a-space>
          <a-button @click="executorModal.visible = false">{{ $t('qa.common.cancel') }}</a-button>
          <a-button type="primary" :loading="executorModal.saving" @click="handleExecutorSave">{{ $t('qa.common.save') }}</a-button>
        </a-space>
      </template>
    </a-modal>
    <!-- 用例详情抽屉：基本信息 / 缺陷列表 / 执行历史 -->
    <a-drawer
      v-model:visible="detailDrawer.visible"
      :title="detailDrawer.caseName"
      :width="620"
      :footer="false"
    >
      <a-tabs v-model:active-key="detailDrawer.tab">
        <a-tab-pane key="info" :title="$t('qa.testPlan.exec.tab.basic')">
          <a-descriptions
            :data="[
              { label: $t('qa.testCase.code'), value: detailDrawer.detail?.caseCode || '-' },
              { label: $t('qa.testCase.type'), value: caseTypeText(detailDrawer.detail?.caseType) },
              { label: $t('qa.testCase.priority'), value: detailDrawer.detail?.priority || '-' },
              { label: $t('qa.testPlan.execResult'), value: resultText(detailDrawer.caseInfo?.executeResult) },
            ]"
            layout="inline-vertical" :column="2" :label-style="{ fontWeight: 'bold' }"
          />
          <a-divider/>
          <h4 style="margin-bottom: 8px;">{{ $t('qa.testCase.preCondition') }}</h4>
          <div class="rich-text-preview" v-html="renderHtml(detailDrawer.detail?.preCondition, $t('qa.testPlan.none'))"/>
          <a-divider/>
          <h4 style="margin-bottom: 8px;">{{ $t('qa.testCase.steps') }}</h4>
          <div v-if="detailSteps.length">
            <div v-for="(step, index) in detailSteps" :key="index" style="margin-bottom: 12px;">
              <div style="font-weight: bold; color: #86909c; margin-bottom: 4px;">{{ $t('qa.testCase.step') }} {{ index + 1 }}</div>
              <div class="rich-text-preview" v-html="renderHtml(step.step, '-')"/>
              <div style="color: #86909c; margin-top: 4px;">{{ $t('qa.testCase.expected') }}：</div>
              <div class="rich-text-preview" v-html="renderHtml(step.expected, '-')"/>
            </div>
          </div>
          <div v-else style="color: #86909c;">{{ $t('qa.testPlan.noSteps') }}</div>
        </a-tab-pane>
        <a-tab-pane key="bugs">
          <template #title>
            {{ $t('qa.testPlan.tab.bugs') }}
            <span class="tab-count-badge">{{ detailDrawer.bugs.length }}</span>
          </template>
          <a-table :data="detailDrawer.bugs" :columns="bugColumns" row-key="id" size="small" :pagination="false">
            <template #bugStatus="{ record }">
              <a-tag :color="bugStatusColor(record.status)" size="small">{{ bugStatusText(record.status) }}</a-tag>
            </template>
            <template #bugSeverity="{ record }">
              <a-tag :color="severityColor(record.severity)" size="small">{{ severityText(record.severity) }}</a-tag>
            </template>
          </a-table>
          <a-empty v-if="!detailDrawer.bugs.length" :description="$t('qa.testPlan.noLinkedBugs')" style="padding: 40px 0;" />
        </a-tab-pane>
        <a-tab-pane key="history">
          <template #title>
            {{ $t('qa.testPlan.tab.history') }}
            <span class="tab-count-badge">{{ detailDrawer.history.length }}</span>
          </template>
          <a-table :data="detailDrawer.history" :columns="historyColumns" row-key="id" size="small" :pagination="false">
            <template #historyResult="{ record }">
              <a-tag :color="resultColor(record.executeResult)" size="small">{{ resultText(record.executeResult) }}</a-tag>
            </template>
          </a-table>
          <a-empty v-if="!detailDrawer.history.length" :description="$t('qa.testPlan.noHistory')" style="padding: 40px 0;" />
        </a-tab-pane>
      </a-tabs>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, onMounted, onBeforeUnmount, nextTick, watch} from 'vue';
import {useI18n} from 'vue-i18n';
import {Message} from '@arco-design/web-vue';
import MindMap from 'simple-mind-map/dist/simpleMindMap.esm.min.js';
import 'simple-mind-map/dist/simpleMindMap.esm.min.css';
import {getTestPoints, getTestCaseDetail, executePlanCaseWithSteps, assignPlanCaseExecutor, removeCaseFromPlan, getTestCaseExecutionHistory, getBugListByPlanCase} from '@/api/MyApi/qa';
import {getUserListByPage} from '@/api/MyApi/user';
import {useProjectStore} from '@/store';
import BugForm from '@/views/qa/bug/components/BugForm.vue';
import BugBindDrawer from '@/views/qa/components/BugBindDrawer.vue';

const projectStore = useProjectStore();
const {t} = useI18n();

const props = defineProps<{
  planId: number;
  // 左栏树/筛选联动：命中的用例 id 集合（testCaseId）；不传/null = 全量
  filterCaseIds?: Set<number> | null;
  // 根节点文案（模块模式选中模块时为该模块名）
  rootLabel?: string;
  // 模块模式：平铺用例，不出现测试点节点
  flatCases?: boolean;
  // 模块模式 + 全部模块：按模块分组（功能用例 → 模块节点 → 用例）
  groupByModule?: boolean;
  // 全量模块列表（groupByModule 时用，空模块也要显示节点）
  moduleList?: { id: number; moduleName: string }[];
}>();

// 数据变更（执行人/移出计划等）后通知父页面刷新左栏树计数
const emit = defineEmits<{ refresh: [] }>();

const mapRef = ref<HTMLElement>();
let mind: any = null;
let resizeObserver: ResizeObserver | null = null;
const testPoints = ref<any[]>([]);
const unassignedCases = ref<any[]>([]);
const loadingDetail = ref(false);
// 已加载详情的用例节点 uid → 详情子节点（用于点击折叠/展开切换）
const detailCache = new Map<string, any[]>();
// 用例节点 uid → 计划用例已保存的步骤执行明细（提交执行结果时落库）
const caseStepResultsMap = new Map<string, any[]>();
// 用例节点 uid → 计划用例记录（planCaseId/executeResult/executeRemark，右键评定时用）
const caseInfoMap = new Map<string, any>();

function stripHtml(text: string): string {
  return (text || '').replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim();
}

// 富文本节点：值做 HTML 转义，防止用例内容里的 < > 破坏节点渲染
function escapeHtml(text: string): string {
  return (text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// 生成带文字的圆形徽标 SVG（注册到内置 iconList，与文字同行内联渲染）
function badgeSvg(text: string, color: string): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44" viewBox="0 0 44 44"><circle cx="22" cy="22" r="20" fill="${color}"/><text x="22" y="29" text-anchor="middle" font-size="19" font-weight="bold" fill="#ffffff" font-family="Arial, sans-serif">${text}</text></svg>`;
}

// 自定义优先级徽标注册进内置图标库（节点 icon 字段引用，格式 type_name）
if (Array.isArray((MindMap as any).iconList) && !(MindMap as any).iconList.some((g: any) => g.type === 'casePriority')) {
  (MindMap as any).iconList.push({
    type: 'casePriority',
    list: [
      {name: 'P0', icon: badgeSvg('P0', '#f53f3f')},
      {name: 'P1', icon: badgeSvg('P1', '#ff7d00')},
      {name: 'P2', icon: badgeSvg('P2', '#165dff')},
    ],
  });
}

// 执行结果 → tag 文案/配色（用例节点与步骤节点共用）
const resultTagOf = (r: string): {text: string; color: string} | undefined => ({
  PASS: {text: t('qa.testPlan.result.pass'), color: '#00b42a'},
  FAIL: {text: t('qa.testPlan.result.fail'), color: '#f53f3f'},
  BLOCK: {text: t('qa.testPlan.result.block'), color: '#ff7d00'},
} as Record<string, {text: string; color: string}>)[r];

// 当前选中的用例节点 uid（点击/评定时高亮）
let selectedCaseUid: string | null = null;

// 用例节点文本（quill 会剥掉 div/class，只能用最简 span）
function buildCaseText(c: any): string {
  return `<span>${escapeHtml(c.caseName || t('qa.testPlan.mindmap.caseFallback', { id: c.testCaseId }))}</span>`;
}

function buildCaseNode(c: any) {
  const uid = `case-${c.testCaseId}`;
  caseInfoMap.set(uid, c);
  if (c.stepResults) {
    try {
      const parsed = JSON.parse(c.stepResults);
      if (Array.isArray(parsed)) caseStepResultsMap.set(uid, parsed);
    } catch { /* 忽略脏数据 */ }
  }
  // 用例节点：彩色圆形徽标（P0/P1/P2）、名称卡片、执行结果（未执行不显示）
  const PRIORITY_STYLE: Record<string, {color: string}> = {
    P0: {color: '#f53f3f'},
    P1: {color: '#ff7d00'},
    P2: {color: '#165dff'},
  };
  const r = resultTagOf(c.executeResult);
  const p = PRIORITY_STYLE[c.priority];
  return {
    data: {
      text: buildCaseText(c),
      uid,
      expand: true,
      richText: true,
      // 卡片/选中样式（官方样式通道：data 里的 fillColor/borderColor 渲染为节点形状矩形）
      ...(uid === selectedCaseUid
        ? {...(SELECT_STYLE[currentTheme.value] || SELECT_STYLE.default), borderWidth: 2.5, borderRadius: 4}
        : {...(CARD_STYLE[currentTheme.value] || CARD_STYLE.default), borderWidth: 1, borderRadius: 4}),
      // 执行状态彩色 tag（节点原生 tag：圆角矩形+白字，未执行不显示）
      ...(r ? {tag: [{text: r.text, style: {fill: r.color, radius: 3, fontSize: 12, height: 20, paddingX: 8}}]} : {}),
      // 自绘彩色圆形徽标（P0/P1/P2 文字在圆内，与名称同行）
      ...(p ? {icon: [`casePriority_${c.priority}`]} : {}),
    },
    children: [],
  };
}

function buildTpNode(uid: string, name: string, cases: any[]) {
  return {
    data: {text: `${name}（${cases.length}）`, uid, expand: true, fillColor: '#e8f3ff', color: '#165dff'},
    children: cases.map(buildCaseNode),
  };
}

function buildMindData(tpData: any) {
  const tps = tpData?.testPoints || [];
  const unassigned = tpData?.unassignedCases || [];
  testPoints.value = tps;
  unassignedCases.value = unassigned;
  detailCache.clear();
  caseStepResultsMap.clear();
  caseInfoMap.clear();

  // 左栏联动筛选：只显示命中用例；过滤后为空的测试点节点整支隐藏（模块模式同理，只出现该模块下的用例）
  const filter = props.filterCaseIds;
  const match = (c: any) => !filter || filter.has(c.testCaseId);

  const root: any = {
    data: {text: props.rootLabel || t('qa.testPlan.tab.cases'), uid: 'root', expand: true},
    children: [],
  };

  // 模块模式：根节点下直接挂用例节点，不出现测试点层级
  if (props.flatCases) {
    const all = [...tps.flatMap((tp: any) => tp.cases || []), ...unassigned];
    root.children = all.filter(match).map(buildCaseNode);
    return root;
  }

  // 模块模式 + 全部模块：按模块分组，模块无层级平铺；没有用例的模块节点也保留
  if (props.groupByModule) {
    const all = [...tps.flatMap((tp: any) => tp.cases || []), ...unassigned];
    const byMod = new Map<number, any[]>();
    all.forEach((c: any) => {
      const k = c.moduleId ?? 0;
      if (!byMod.has(k)) byMod.set(k, []);
      byMod.get(k)!.push(c);
    });
    const knownIds = new Set<number>();
    root.children = (props.moduleList || []).map((m) => {
      knownIds.add(m.id);
      return buildTpNode(`mod-${m.id}`, m.moduleName, byMod.get(m.id) || []);
    });
    // 未归属任何模块/模块已被删的用例，归到「未选择模块」
    const orphans = all.filter((c: any) => !knownIds.has(c.moduleId ?? 0));
    if (orphans.length) root.children.push(buildTpNode('mod-0', t('qa.testPlan.mindmap.noModule'), orphans));
    return root;
  }

  // 测试点节点：有用例命中时显示；原本就没有用例的测试点也保留（空节点也要出来）
  const tpNodes = tps
    .map((tp: any) => ({tp, shown: (tp.cases || []).filter(match)}))
    .filter(({tp, shown}: any) => shown.length > 0 || (tp.cases || []).length === 0)
    .map(({tp, shown}: any) => buildTpNode(`tp-${tp.id}`, tp.name || t('qa.testPlan.defaultTestPoint'), shown));

  root.children = tpNodes;
  const filteredUnassigned = unassigned.filter(match);
  if (filteredUnassigned.length > 0) {
    root.children.push(buildTpNode('tp-unassigned', t('qa.testPlan.unassigned'), filteredUnassigned));
  }
  return root;
}

// 详情节点视觉规范（与用例库脑图一致）：tag 标识类型 + 底色/文字色；文本为纯文本转义，不再用富文本前缀
const mkTag = (text: string, fill: string) => ({text, style: {fill, radius: 3, fontSize: 12, height: 20, paddingX: 8}});
const DETAIL_TAG = {
  pre: () => mkTag(t('qa.testPlan.mindmap.tag.pre'), '#86909c'),
  step: (index: number) => mkTag(`${t('qa.testCase.step')} ${index}`, '#722ed1'),
  expect: () => mkTag(t('qa.testPlan.mindmap.tag.expect'), '#00b42a'),
  actual: () => mkTag(t('qa.testPlan.mindmap.tag.actual'), '#ff7d00'),
};
const DETAIL_NODE_STYLE = {
  pre: {fillColor: '#f2f3f5', color: '#4e5969'},
  step: {fillColor: '#f5e8ff', color: '#722ed1'},
  expect: {fillColor: '#e8ffea', color: '#00b42a'},
  actual: {fillColor: '#fff7e8', color: '#ff7d00'},
};

// 根据详情生成 前置条件 + 步骤 子节点；savedSteps 为该计划用例已保存的步骤执行明细
function buildDetailNodes(detail: any, savedSteps: any[]): any[] {
  const nodes: any[] = [];
  const pre = stripHtml(detail.preCondition);
  if (pre) {
    nodes.push({
      data: {
        text: escapeHtml(pre),
        uid: `pre-${detail.id}`,
        tag: [DETAIL_TAG.pre()],
        ...DETAIL_NODE_STYLE.pre,
      },
      children: [],
    });
  }
  (detail.testSteps || []).forEach((s: any, i: number) => {
    const stepText = stripHtml(s.step);
    const expected = stripHtml(s.expected);
    // 步骤执行状态 tag（在「步骤 N」tag 之后；未填写不显示）
    const sr = resultTagOf(savedSteps?.[i]?.stepResult);
    const stepNode: any = {
      data: {
        text: escapeHtml(stepText),
        uid: `step-${detail.id}-${i}`,
        expand: true,
        tag: [DETAIL_TAG.step(i + 1), ...(sr ? [mkTag(sr.text, sr.color)] : [])],
        ...DETAIL_NODE_STYLE.step,
      },
      children: [],
    };
    const actual = stripHtml(savedSteps?.[i]?.actualResult || '');
    // 预期结果/实际结果节点始终显示，无内容显示空值（实际结果挂在预期结果下）
    const actualNode = {
      data: {
        text: escapeHtml(actual),
        uid: `actual-${detail.id}-${i}`,
        tag: [DETAIL_TAG.actual()],
        ...DETAIL_NODE_STYLE.actual,
      },
      children: [],
    };
    stepNode.children.push({
      data: {
        text: escapeHtml(expected),
        uid: `expect-${detail.id}-${i}`,
        expand: true,
        tag: [DETAIL_TAG.expect()],
        ...DETAIL_NODE_STYLE.expect,
      },
      children: [actualNode],
    });
    nodes.push(stepNode);
  });
  if (nodes.length === 0) {
    nodes.push({
      data: {text: t('qa.testPlan.mindmap.emptyDetail'), uid: `empty-${detail.id}`, fillColor: '#f7f8fa', color: '#86909c'},
      children: [],
    });
  }
  return nodes;
}

// 点击用例节点：未加载 → 拉详情展开；已展开/已收起 → 不折叠，折叠只能通过节点上的 +/- 按钮
async function onNodeClick(node: any) {
  const uid: string = node?.nodeData?.data?.uid || '';
  if (uid.startsWith('case-')) {
    selectedOtherUid = null;
    selectedCaseUid = uid;
    applyCaseSelection();
    await expandCaseDetail(node);
    return;
  }
  // 非用例节点：点击选中高亮（用例选中态清掉，操作按钮随之隐藏）
  selectedCaseUid = null;
  applyCaseSelection();
  selectedOtherUid = uid;
  applyOtherSelection();
}

// 拉取用例详情子节点（带缓存；缓存由 buildMindData 在重建时清空，保证保存后拉到最新）
async function loadDetailNodes(uid: string): Promise<any[]> {
  let detailNodes = detailCache.get(uid);
  if (detailNodes) return detailNodes;
  const testCaseId = uid.replace('case-', '');
  loadingDetail.value = true;
  try {
    const res: any = await getTestCaseDetail(Number(testCaseId));
    detailNodes = res.code === 200 && res.data ? buildDetailNodes(res.data, caseStepResultsMap.get(uid) || []) : [];
    detailCache.set(uid, detailNodes);
  } catch (e) {
    console.error('[CaseMindMap] 加载用例详情失败:', e);
    detailNodes = [];
  } finally {
    loadingDetail.value = false;
  }
  return detailNodes;
}

// 展开用例详情子节点（已加载过则不动，交给节点 +/- 按钮控制）
async function expandCaseDetail(node: any) {
  const uid: string = node?.nodeData?.data?.uid || '';
  if ((node.nodeData.children || []).length > 0) return;

  const detailNodes = await loadDetailNodes(uid);
  node.nodeData.children = detailNodes.map(n => ({...n}));
  node.nodeData.data.expand = true;
  mind?.render();
}

// 用例节点卡片样式（官方：节点 data 里的 fillColor/borderColor/borderWidth/borderRadius 会渲染为节点形状）
const CARD_STYLE: Record<string, {fillColor: string; borderColor: string}> = {
  default: {fillColor: '#ffffff', borderColor: '#e5e6eb'},
  eyeCare: {fillColor: '#ffffff', borderColor: '#c7e0d2'},
  dark: {fillColor: '#2a2e35', borderColor: '#3a3e45'},
};
// 选中态高亮（随主题）
const SELECT_STYLE: Record<string, {fillColor: string; borderColor: string}> = {
  default: {fillColor: '#e8f3ff', borderColor: '#165dff'},
  eyeCare: {fillColor: '#d8ecdf', borderColor: '#2f9e6e'},
  dark: {fillColor: '#2b3d5c', borderColor: '#4e8cff'},
};

// 选中态：官方 API setNodeStyles（走 SET_NODE_DATA 命令 + node.reRender，样式存 data，重渲染不丢）
// 注意必须遍历节点实例树 renderer.root（renderTree 是纯数据，setNodeStyles 只认节点实例）
function applyCaseSelection() {
  const walk = (n: any) => {
    if (!n) return;
    const uid: string = n.nodeData?.data?.uid || '';
    if (uid.startsWith('case-')) {
      const selected = uid === selectedCaseUid;
      const s = (selected ? SELECT_STYLE : CARD_STYLE)[currentTheme.value] || (selected ? SELECT_STYLE : CARD_STYLE).default;
      mind?.renderer?.setNodeStyles(n, {
        fillColor: s.fillColor,
        borderColor: s.borderColor,
        borderWidth: selected ? 2.5 : 1,
        borderRadius: 4,
      });
    }
    (n.children || []).forEach(walk);
  };
  walk(mind?.renderer?.root);
  // 选中后「评定」按钮常驻选中节点右侧；无选中则隐藏
  if (selectedCaseUid) {
    const selNode = findNodeByUid(selectedCaseUid);
    if (selNode) positionHoverBtn(selNode);
  } else {
    hoverBtn.value.visible = false;
  }
}

// ===== 非用例节点选中态（根/测试点/模块/详情节点都可点击选中） =====
let selectedOtherUid: string | null = null;
// 选中时捕获原始样式，取消选中时还原（setNodeStyles 写入 data，需手动恢复）
const otherBaseStyle = new Map<string, any>();

function pickBaseStyle(d: any) {
  return {
    fillColor: d.fillColor,
    borderColor: d.borderColor,
    borderWidth: d.borderWidth,
    borderRadius: d.borderRadius,
  };
}

function applyOtherSelection() {
  const walk = (n: any) => {
    if (!n) return;
    const uid: string = n.nodeData?.data?.uid || '';
    if (uid && !uid.startsWith('case-')) {
      if (uid === selectedOtherUid) {
        if (!otherBaseStyle.has(uid)) otherBaseStyle.set(uid, pickBaseStyle(n.nodeData.data));
        const s = SELECT_STYLE[currentTheme.value] || SELECT_STYLE.default;
        mind?.renderer?.setNodeStyles(n, {
          fillColor: s.fillColor,
          borderColor: s.borderColor,
          borderWidth: 2,
          borderRadius: 4,
        });
      } else if (otherBaseStyle.has(uid)) {
        mind?.renderer?.setNodeStyles(n, otherBaseStyle.get(uid));
        otherBaseStyle.delete(uid);
      }
    }
    (n.children || []).forEach(walk);
  };
  walk(mind?.renderer?.root);
}

// ===== 主题切换（打包版只内置 default，用 setThemeConfig 深度合入自定义配色） =====
const THEME_OPTIONS = computed(() => [
  {label: t('qa.testPlan.mindmap.theme.default'), value: 'default'},
  {label: t('qa.testPlan.mindmap.theme.eyeCare'), value: 'eyeCare'},
  {label: t('qa.testPlan.mindmap.theme.dark'), value: 'dark'},
]);
const THEME_CONFIGS: Record<string, any> = {
  default: {
    backgroundColor: '#ffffff',
    lineColor: '#549688',
    root: {fillColor: '#165dff', color: '#fff', borderColor: '#165dff'},
    second: {fillColor: '#ffffff', color: '#1d2129', borderColor: 'transparent'},
    // 同级节点最小间距（给节点下方外挂按钮留位置）
    node: {color: '#1d2129', marginY: 60},
  },
  eyeCare: {
    backgroundColor: '#eef5e9',
    lineColor: '#74b49b',
    root: {fillColor: '#2f9e6e', color: '#fff', borderColor: '#2f9e6e'},
    second: {fillColor: '#ffffff', color: '#1d2129', borderColor: '#c7e0d2'},
    node: {color: '#1d2129', marginY: 60},
  },
  dark: {
    backgroundColor: '#1f2329',
    lineColor: '#4e5969',
    root: {fillColor: '#165dff', color: '#fff', borderColor: '#165dff'},
    second: {fillColor: '#2a2e35', color: '#e5e6eb', borderColor: '#3a3e45'},
    node: {color: '#e5e6eb', marginY: 60},
  },
};
const currentTheme = ref('default');

function onThemeChange(val: any) {
  const cfg = THEME_CONFIGS[val];
  if (!cfg || !mind) return;
  // setThemeConfig 为深度合入：先重置回默认主题再叠加目标配置，避免主题间串色
  mind.setTheme('default');
  mind.setThemeConfig(cfg);
  // 主题切换会重渲染，节点边框/底色被主题覆盖，需重刷选中态
  applyCaseSelection();
  applyOtherSelection();
}

// ===== 操作按钮组（点击选中用例节点后出现，节点下方常驻，直到点击画布取消选中） =====
const hoverBtn = ref<{ visible: boolean; x: number; y: number; node: any }>({visible: false, x: 0, y: 0, node: null});

function positionHoverBtn(node: any) {
  if (!mind || !mapRef.value) return false;
  // 官方坐标换算：节点逻辑坐标(left/top/width/height) × 缩放 + 平移 = 画布像素坐标
  // （不能用 group 的 getBoundingClientRect，其包围盒含隐形 hover 区/展开按钮触发区，会偏大）
  const {state} = mind.view.getTransformData();
  const left = node.left * state.scale + state.x;
  const top = node.top * state.scale + state.y;
  const width = node.width * state.scale;
  const height = node.height * state.scale;
  // 节点下方水平居中（3 个图标按钮组宽约 90px）
  hoverBtn.value = {
    visible: true,
    x: left + width / 2 - 45,
    y: top + height + 6,
    node,
  };
  return true;
}

// ===== 执行评定弹窗 =====
const execModal = ref<{
  visible: boolean;
  loading: boolean;
  submitting: boolean;
  caseInfo: any;
  caseName: string;
  detail: any;
  result: string;
  remark: string;
  stepEdits: { index: number; step: string; expected: string; actualResult: string; stepResult: string }[];
}>({visible: false, loading: false, submitting: false, caseInfo: null, caseName: '', detail: null, result: '', remark: '', stepEdits: []});

async function openExecModal(node: any) {
  const uid: string = node?.nodeData?.data?.uid || '';
  if (!uid.startsWith('case-')) return;
  const info = caseInfoMap.get(uid);
  if (!info) return;
  selectedCaseUid = uid;
  applyCaseSelection();

  execModal.value = {
    visible: true,
    loading: true,
    submitting: false,
    caseInfo: info,
    caseName: info.caseName || t('qa.testPlan.mindmap.caseFallback', { id: info.testCaseId }),
    detail: null,
    // 已执行的回填结果与备注；未执行保持空
    result: ['PASS', 'FAIL', 'BLOCK'].includes(info.executeResult) ? info.executeResult : '',
    remark: info.executeRemark || '',
    stepEdits: [],
  };
  try {
    const res: any = await getTestCaseDetail(Number(info.testCaseId));
    // 注意不能 execModal.value.caseInfo !== info 比较对象引用：
    // reactive 代理访问 .caseInfo 会返回包装后的 proxy，与原始对象永不相等
    if (execModal.value.caseInfo?.planCaseId !== info.planCaseId) return; // 已切换/关闭
    const detail = res.code === 200 ? res.data : null;
    execModal.value.detail = detail;
    // testSteps 后端可能返回 JSON 字符串，做兼容解析（同执行页）
    let steps: any[] = [];
    if (Array.isArray(detail?.testSteps)) {
      steps = detail.testSteps;
    } else if (typeof detail?.testSteps === 'string' && detail.testSteps) {
      try {
        const parsed = JSON.parse(detail.testSteps);
        if (Array.isArray(parsed)) steps = parsed;
      } catch { /* 忽略脏数据 */ }
    }
    const saved = caseStepResultsMap.get(uid) || [];
    execModal.value.stepEdits = steps.map((s: any, i: number) => ({
      index: i,
      step: s.step || '',
      expected: s.expected || '',
      actualResult: saved[i]?.actualResult || '',
      stepResult: saved[i]?.stepResult || '',
    }));
  } catch (err) {
    console.error('[CaseMindMap] 加载用例详情失败:', err);
    Message.error(t('qa.testPlan.mindmap.loadDetailFail'));
  } finally {
    execModal.value.loading = false;
  }
}

// 按 uid 在渲染树中查找节点（遍历节点实例树 renderer.root）
function findNodeByUid(uid: string): any {
  let found: any = null;
  const walk = (n: any) => {
    if (!n || found) return;
    if (n.nodeData?.data?.uid === uid) { found = n; return; }
    (n.children || []).forEach(walk);
  };
  walk(mind?.renderer?.root);
  return found;
}

async function handleExecSubmit() {
  const m = execModal.value;
  if (!m.caseInfo) return;
  if (!m.result) {
    Message.warning(t('qa.testPlan.exec.selectResult'));
    return;
  }
  m.submitting = true;
  try {
    const res: any = await executePlanCaseWithSteps(
      m.caseInfo.planCaseId,
      m.result,
      m.remark || undefined,
      undefined,
      m.stepEdits.map(s => ({actualResult: s.actualResult, stepResult: s.stepResult}))
    );
    if (res.code === 200) {
      Message.success(t('qa.testPlan.exec.recorded'));
      m.visible = false;
      // refreshMindData 内部会保持所有节点的展开/折叠状态（含本用例已展开的详情）
      await refreshMindData();
    }
  } catch (err) {
    console.error(err);
  } finally {
    m.submitting = false;
  }
}

// ===== BUG 关联/新建（复用执行页抽屉） =====
const bugCaseInfo = ref<any>(null); // 当前操作的计划用例（planCaseId/testCaseId/caseName）
const bugBindVisible = ref(false);
const newBugVisible = ref(false);
const newBugSaving = ref(false);
const newBugFormRef = ref();

// BUG 下拉动作：新建 / 关联
function handleBugAction(val: any) {
  if (val === 'new') openNewBugDrawer();
  else if (val === 'bind') openBugBindDrawer();
}

function resolveBugCaseInfo(): any {
  const uid = hoverBtn.value.node?.nodeData?.data?.uid || selectedCaseUid || '';
  return uid.startsWith('case-') ? caseInfoMap.get(uid) : null;
}

function openBugBindDrawer() {
  const info = resolveBugCaseInfo();
  if (!info) return;
  bugCaseInfo.value = info;
  bugBindVisible.value = true;
}

function openNewBugDrawer() {
  const info = resolveBugCaseInfo();
  if (!info) return;
  bugCaseInfo.value = info;
  newBugVisible.value = true;
}

async function handleNewBugSave() {
  if (!newBugFormRef.value) return;
  newBugSaving.value = true;
  try {
    const ok = await newBugFormRef.value.save();
    if (ok) {
      Message.success(t('qa.testPlan.exec.bugCreated'));
      newBugVisible.value = false;
    }
  } catch (e) {
    console.error(e);
  } finally {
    newBugSaving.value = false;
  }
}

// ===== 用例详情抽屉（基本信息 / 缺陷列表 / 执行历史） =====
const detailDrawer = ref<{ visible: boolean; tab: string; caseInfo: any; caseName: string; detail: any; bugs: any[]; history: any[] }>(
  {visible: false, tab: 'info', caseInfo: null, caseName: '', detail: null, bugs: [], history: []}
);

// testSteps 后端可能返回 JSON 字符串，做兼容解析（同执行页）
const detailSteps = computed(() => {
  let steps = detailDrawer.value.detail?.testSteps;
  if (typeof steps === 'string' && steps) {
    try {
      const parsed = JSON.parse(steps);
      steps = Array.isArray(parsed) ? parsed : [];
    } catch {
      steps = [];
    }
  }
  return Array.isArray(steps) ? steps : [];
});

async function openDetailDrawer() {
  const info = resolveBugCaseInfo();
  if (!info) return;
  detailDrawer.value = {
    visible: true, tab: 'info', caseInfo: info,
    caseName: info.caseName || t('qa.testPlan.mindmap.caseFallback', { id: info.testCaseId }),
    detail: null, bugs: [], history: [],
  };
  try {
    const [dRes, bRes, hRes]: any[] = await Promise.all([
      getTestCaseDetail(Number(info.testCaseId)),
      getBugListByPlanCase(Number(info.testCaseId)),
      getTestCaseExecutionHistory(Number(info.testCaseId)),
    ]);
    if (dRes.code === 200) detailDrawer.value.detail = dRes.data;
    if (bRes.code === 200) detailDrawer.value.bugs = bRes.data || [];
    if (hRes.code === 200) detailDrawer.value.history = hRes.data || [];
  } catch (e) {
    console.error('[CaseMindMap] 加载用例详情抽屉失败:', e);
  }
}

function renderHtml(content: string | undefined, defaultText?: string) {
  const fallback = defaultText ?? t('qa.testPlan.empty');
  if (!content || content.trim() === '') return fallback;
  if (/<[a-z][\s\S]*?>/i.test(content)) return content;
  return content.replace(/\n/g, '<br>');
}

const caseTypeText = (type?: string) => {
  const map: Record<string, string> = { FUNCTION: t('qa.testPlan.caseType.function'), API: t('qa.testPlan.caseType.api'), PERFORMANCE: t('qa.testPlan.caseType.performance'), COMPATIBILITY: t('qa.testPlan.caseType.compatibility'), SMOKE: t('qa.testPlan.caseType.smoke') };
  return map[type || ''] || type || '-';
};
const resultText = (r?: string) => {
  const map: Record<string, string> = { PASS: t('qa.testPlan.result.success'), FAIL: t('qa.testPlan.result.fail'), BLOCK: t('qa.testPlan.result.block'), NA: t('qa.testPlan.result.na'), UNEXECUTED: t('qa.testPlan.result.unexecuted') };
  return map[r || ''] || t('qa.testPlan.result.unexecuted');
};
const resultColor = (r?: string) => {
  const map: Record<string, string> = { PASS: 'green', FAIL: 'red', BLOCK: 'orange' };
  return map[r || ''] || 'gray';
};
const bugStatusText = (s?: string) => {
  const map: Record<string, string> = { NEW: t('qa.testPlan.bugStatus.new'), CONFIRMED: t('qa.testPlan.bugStatus.confirmed'), FIXING: t('qa.testPlan.bugStatus.fixing'), FIXED: t('qa.testPlan.bugStatus.fixed'), VERIFIED: t('qa.testPlan.bugStatus.verified'), CLOSED: t('qa.testPlan.bugStatus.closed'), REJECTED: t('qa.testPlan.bugStatus.rejected') };
  return map[s || ''] || s || '-';
};
const bugStatusColor = (s?: string) => {
  const map: Record<string, string> = { NEW: 'red', CONFIRMED: 'orange', FIXING: 'gold', FIXED: 'blue', VERIFIED: 'cyan', CLOSED: 'gray', REJECTED: 'gray' };
  return map[s || ''] || 'gray';
};
const severityText = (s?: string) => {
  const map: Record<string, string> = { FATAL: t('qa.testPlan.severity.fatal'), SERIOUS: t('qa.testPlan.severity.serious'), NORMAL: t('qa.testPlan.severity.normal'), TIPS: t('qa.testPlan.severity.tips') };
  return map[s || ''] || s || '-';
};
const severityColor = (s?: string) => {
  const map: Record<string, string> = { FATAL: 'red', SERIOUS: 'orange', NORMAL: 'blue', TIPS: 'gray' };
  return map[s || ''] || 'gray';
};

// ===== 表格列定义（:columns 数组 + slotName 自定义渲染） =====
// 执行评定弹窗 - 测试步骤表
const stepColumns = computed(() => [
  { title: '#', width: 50, align: 'center', slotName: 'stepNo' },
  { title: t('qa.testCase.step'), ellipsis: true, tooltip: true, slotName: 'stepDesc' },
  { title: t('qa.testCase.expected'), ellipsis: true, tooltip: true, slotName: 'stepExpected' },
  { title: t('qa.testPlan.actualResult'), width: 220, slotName: 'stepActual' },
  { title: t('qa.testPlan.stepResult'), width: 130, slotName: 'stepResult' },
]);
// 用例详情抽屉 - 缺陷列表表
const bugColumns = computed(() => [
  { title: t('qa.bug.code'), dataIndex: 'bugCode', width: 120, ellipsis: true, tooltip: true },
  { title: t('qa.testPlan.exec.bugName'), dataIndex: 'title', width: 200, ellipsis: true, tooltip: true },
  { title: t('qa.bug.status'), dataIndex: 'status', width: 90, align: 'center', slotName: 'bugStatus' },
  { title: t('qa.bug.severity'), dataIndex: 'severity', width: 90, align: 'center', slotName: 'bugSeverity' },
]);
// 用例详情抽屉 - 执行历史表
const historyColumns = computed(() => [
  { title: t('qa.testPlan.execResult'), dataIndex: 'executeResult', width: 90, slotName: 'historyResult' },
  { title: t('qa.testPlan.executor'), dataIndex: 'executeUserName', width: 100, ellipsis: true, tooltip: true },
  { title: t('qa.testPlan.execTime'), dataIndex: 'executeTime', width: 180, ellipsis: true, tooltip: true },
  { title: t('qa.testPlan.remark'), dataIndex: 'executeRemark', ellipsis: true, tooltip: true },
]);

// ===== 修改执行人 =====
const executorModal = ref<{ visible: boolean; saving: boolean; caseInfo: any; executorId: number | undefined }>(
  {visible: false, saving: false, caseInfo: null, executorId: undefined}
);
const executorOptions = ref<any[]>([]);

async function loadExecutorOptions(keyword?: string) {
  try {
    const res: any = await getUserListByPage({
      projectId: projectStore.getProjectId ?? undefined,
      username: keyword || undefined,
      pageNum: 1,
      pageSize: 20,
    });
    executorOptions.value = res.data?.records || res.data || [];
  } catch (e) {
    console.error(e);
  }
}

function openExecutorModal() {
  const info = resolveBugCaseInfo();
  if (!info) return;
  executorModal.value = {visible: true, saving: false, caseInfo: info, executorId: info.executorId ?? undefined};
  loadExecutorOptions();
}

async function handleExecutorSave() {
  const m = executorModal.value;
  if (!m.caseInfo) return;
  if (!m.executorId) {
    Message.warning(t('qa.testPlan.mindmap.selectExecutor'));
    return;
  }
  m.saving = true;
  try {
    const res: any = await assignPlanCaseExecutor(m.caseInfo.planCaseId, m.executorId);
    if (res.code === 200) {
      Message.success(t('qa.testPlan.mindmap.executorUpdated'));
      m.visible = false;
      await refreshMindData();
      emit('refresh');
    } else {
      Message.error(res.msg || t('qa.testPlan.saveFail'));
    }
  } catch (e: any) {
    console.error(e);
    Message.error(e?.response?.data?.msg || t('qa.testPlan.saveFail'));
  } finally {
    m.saving = false;
  }
}

// ===== 取消关联用例（移出计划） =====
async function handleRemoveCase() {
  const info = resolveBugCaseInfo();
  if (!info) return;
  try {
    const res: any = await removeCaseFromPlan(info.planCaseId);
    if (res.code === 200) {
      Message.success(t('qa.testPlan.mindmap.removed'));
      hoverBtn.value.visible = false;
      selectedCaseUid = null;
      await refreshMindData();
      emit('refresh');
    } else {
      Message.error(res.msg || t('qa.testPlan.mindmap.removeFail'));
    }
  } catch (e: any) {
    console.error(e);
    Message.error(e?.response?.data?.msg || t('qa.testPlan.mindmap.removeFail'));
  }
}

// ===== 重建时保持展开/折叠状态（与用例库脑图同一方案：状态还原到新数据树，单次 setData） =====
// 节点形态兼容：渲染树是实例（nodeData.data.uid），数据树是纯数据（data.uid）
const uidOf = (n: any): string => n?.nodeData?.data?.uid ?? n?.data?.uid ?? '';
const childrenOf = (n: any): any[] => n?.children || [];

async function rebuildMind(tpData: any) {
  if (!mind) return;
  // 重建前记录：已展开详情的用例 + 被用户折叠的节点
  const expandedCaseUids: string[] = [];
  const collapsedUids = new Set<string>();
  const collect = (n: any) => {
    if (!n) return;
    const uid = uidOf(n);
    if (uid.startsWith('case-') && childrenOf(n).length > 0) expandedCaseUids.push(uid);
    const expand = n?.nodeData?.data?.expand ?? n?.data?.expand;
    if (uid && expand === false) collapsedUids.add(uid);
    childrenOf(n).forEach(collect);
  };
  collect(mind.renderer?.root || mind.renderer?.renderTree);

  const data = buildMindData(tpData);
  // 恢复折叠状态（默认都是展开的，只把之前折叠的节点折回去）
  const applyCollapse = (n: any) => {
    if (!n) return;
    if (collapsedUids.has(n.data?.uid)) n.data.expand = false;
    (n.children || []).forEach(applyCollapse);
  };
  applyCollapse(data);
  // 恢复用例详情展开：详情子节点直接挂到新数据树（重新拉详情，保证是保存后的最新数据），
  // 不依赖渲染后的节点实例，避免实例/数据形态差异导致恢复失败
  if (expandedCaseUids.length) {
    const findDataNode = (n: any, uid: string): any => {
      if (!n) return null;
      if (n.data?.uid === uid) return n;
      for (const c of (n.children || [])) {
        const hit = findDataNode(c, uid);
        if (hit) return hit;
      }
      return null;
    };
    for (const uid of expandedCaseUids) {
      const target = findDataNode(data, uid);
      if (!target) continue;
      const detailNodes = await loadDetailNodes(uid);
      target.children = detailNodes.map(n => ({...n}));
      // 已被用户折叠的用例：子节点数据保留，但保持折叠状态
      target.data.expand = !collapsedUids.has(uid);
    }
  }
  mind.setData(data);
}

// 重新拉取测试点数据并重建脑图（评定提交后刷新 tag 状态）
async function refreshMindData() {
  try {
    const res: any = await getTestPoints(props.planId);
    if (res.code === 200 && mind) {
      await rebuildMind(res.data);
    }
  } catch (e) {
    console.error(e);
  }
}

// 左栏筛选联动：用缓存数据原地重建，不重新拉接口
watch([() => props.filterCaseIds, () => props.rootLabel, () => props.flatCases, () => props.groupByModule, () => props.moduleList], () => {
  rebuildMind({testPoints: testPoints.value, unassignedCases: unassignedCases.value});
});

async function initMind(retries = 0) {
  if (!mapRef.value) return;

  // simple-mind-map 构造函数在容器宽高为 0 时会直接 throw，等布局完成再初始化
  const {width, height} = mapRef.value.getBoundingClientRect();
  if (width === 0 || height === 0) {
    if (retries < 60) requestAnimationFrame(() => initMind(retries + 1));
    else console.error('[CaseMindMap] 容器尺寸为 0，放弃初始化');
    return;
  }

  let data;
  try {
    const res: any = await getTestPoints(props.planId);
    data = buildMindData(res.code === 200 ? res.data : null);
  } catch (e) {
    console.error(e);
    data = buildMindData(null);
  }
  if (!mapRef.value || mind) return;

  mapRef.value.innerHTML = '';
  try {
    mind = new MindMap({
      el: mapRef.value,
      data,
      readonly: true,
      layout: 'logicalStructure',
      theme: 'default',
      expandBtnSize: 20,
      // 详情节点（前置条件/步骤/预期/实际）使用富文本，标签着色、值默认色
      nodeTextUseRichText: true,
    });
    // 应用默认主题配置（含节点间距等自定义项）
    mind.setThemeConfig(THEME_CONFIGS.default);
    mind.on('node_click', (node: any) => onNodeClick(node));
    // 画布按下：隐藏操作按钮并清除选中态（点击节点时 node_click 会随后重新设置）
    // 画布按下：隐藏操作按钮并清除所有选中态。
    // readonly 模式下节点 mousedown 会冒泡到 svg，需排除目标在节点内的情况——
    // 否则点节点会先清空再选中（竞态），甚至出现再次点击节点被取消选中的现象
    mind.on('svg_mousedown', (e: any) => {
      if (e?.target?.closest?.('.smm-node')) return;
      hoverBtn.value.visible = false;
      if (selectedCaseUid) {
        selectedCaseUid = null;
        applyCaseSelection();
      }
      if (selectedOtherUid) {
        selectedOtherUid = null;
        applyOtherSelection();
      }
    });
    resizeObserver = new ResizeObserver(() => {
      if (!mapRef.value || !mind) return;
      // tab 隐藏/弹窗打开时容器宽高等 0，resize 会抛异常，需跳过
      const {width, height} = mapRef.value.getBoundingClientRect();
      if (width === 0 || height === 0) return;
      try {
        mind.resize?.();
      } catch (e) {
        console.warn('[CaseMindMap] resize 异常:', e);
      }
    });
    resizeObserver.observe(mapRef.value);
  } catch (e) {
    console.error('[CaseMindMap] 初始化失败:', e);
    mind = null;
    mapRef.value.innerHTML = '';
  }
}

onMounted(() => {
  nextTick(() => initMind());
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
  if (mind) {
    try {
      mind.destroy?.();
    } catch (e) {
      console.error('[CaseMindMap] destroy 异常:', e);
      if (mapRef.value) mapRef.value.innerHTML = '';
    }
    mind = null;
  }
});
</script>

<style>
.case-mindmap-canvas * {
  margin: 0;
  padding: 0;
}
</style>

<style scoped>
.case-mindmap-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  overflow: hidden;
  position: relative;
}
.case-mindmap-canvas {
  flex: 1;
  width: 100%;
  position: relative;
  overflow: hidden;
}
/* 画布右上角悬浮工具栏（top 44px 避开功能用例 tab 的「列表/脑图」切换按钮） */
.case-mindmap-toolbar-top {
  position: absolute;
  top: 44px;
  right: 8px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 10px;
  background: var(--color-bg-2, #ffffff);
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.case-mindmap-toolbar-top .hint {
  font-size: 13px;
  color: #86909c;
}
.case-mindmap-toolbar-top .loading {
  color: rgb(var(--primary-6));
}
.dd-section-title {
  font-weight: 600;
  margin: 4px 0 10px;
  padding-left: 8px;
  border-left: 3px solid rgb(var(--primary-6));
  line-height: 1.2;
}
.pre-view { margin: 0 0 16px; }
.pre-empty { margin: 0 0 16px; color: var(--color-text-3); font-size: 13px; }
.step-no { color: rgb(var(--primary-6)); font-weight: 600; font-size: 12px; }
.exec-form-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 4px;
}
.result-text.pass { color: #00b42a; }
.result-text.fail { color: #f53f3f; }
.result-text.block { color: #ff7d00; }
.case-hover-group {
  position: absolute;
  z-index: 20;
  display: flex;
  gap: 6px;
}
.case-hover-group :deep(.arco-btn) {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.new-bug-body { height: 100%; overflow: hidden; }
.rich-text-preview {
  line-height: 1.6;
  :deep(p) { margin: 8px 0; }
  :deep(ul), :deep(ol) { padding-left: 20px; margin: 8px 0; }
  :deep(blockquote) { border-left: 4px solid #ccc; padding-left: 10px; color: #666; margin: 8px 0; }
  :deep(img) { max-width: 100%; }
  :deep(a) { color: rgb(var(--primary-6)); }
}
/* tab 标题计数徽标（灰色） */
.tab-count-badge {
  display: inline-block;
  min-width: 20px;
  padding: 0 6px;
  margin-left: 4px;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  color: #4e5969;
  background: #e5e6eb;
  border-radius: 9px;
}
</style>
