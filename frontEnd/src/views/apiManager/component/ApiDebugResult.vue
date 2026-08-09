<template>
  <div ref="rootRef" style="height: 100%; display: flex; flex-direction: column; min-width: 0; overflow: hidden;">
    <!-- ========== 请求未发送成功（仅 HTTP）：只展示错误详情 ========== -->
    <template v-if="isRequestError && !isSqlApi">
      <a-alert type="error" :title="'请求执行失败'" style="margin-bottom: 16px; flex-shrink: 0">
        <a-typography-text type="danger" style="font-size: 14px; white-space: pre-wrap; word-break: break-all">
          {{ props.debugResult?.errorMessage || '未知错误' }}
        </a-typography-text>
      </a-alert>

      <!-- 请求信息（用于排查问题） -->
      <a-card title="请求信息" size="small" style="flex-shrink: 0; margin-bottom: 12px">
        <a-descriptions :column="1" size="small">
          <a-descriptions-item label="请求 URL">
            <a-typography-text copyable style="word-break: break-all">
              {{ props.debugResult?.requestUrl || '-' }}
            </a-typography-text>
          </a-descriptions-item>
          <a-descriptions-item label="请求方法">
            <a-tag>{{ props.debugResult?.requestMethod || '-' }}</a-tag>
          </a-descriptions-item>
        </a-descriptions>
      </a-card>

      <!-- 请求头 -->
      <a-card v-if="hasRequestHeaders" title="请求头" size="small" style="flex-shrink: 0; margin-bottom: 12px">
        <a-descriptions :column="1" size="small">
          <a-descriptions-item v-for="(value, key) in props.debugResult?.requestHeaders" :key="key" :label="key">
            {{ value }}
          </a-descriptions-item>
        </a-descriptions>
      </a-card>

      <!-- 请求体 -->
      <a-card v-if="props.debugResult?.requestBody" title="请求体" size="small"
              style="flex-shrink: 0; margin-bottom: 12px">
        <pre class="request-body" style="max-height: 30vh">{{ formatRequestBody(props.debugResult?.requestBody) }}</pre>
      </a-card>
    </template>

    <!-- ========== 正常请求/已发送：展示完整 tabs ========== -->
    <template v-else>
      <!-- 响应状态条：状态码 / 耗时 / 大小 / Content-Type -->
      <div v-if="!isSqlApi"
           style="display: flex; align-items: center; gap: 20px; padding: 6px 12px; margin-bottom: 8px; background: var(--color-fill-2); border-radius: 4px; flex-shrink: 0; font-size: 13px;">
        <span style="display: inline-flex; align-items: center; gap: 6px;">
          <span style="color: var(--color-text-3);">状态码</span>
          <a-tag :color="statusColor" size="small">{{ statusCode != null ? statusCode : '-' }}</a-tag>
        </span>
        <span style="display: inline-flex; align-items: center; gap: 6px;">
          <span style="color: var(--color-text-3);">耗时</span>
          <span style="font-weight: 600;">{{ responseTimeMs != null ? responseTimeMs + ' ms' : '-' }}</span>
        </span>
        <span style="display: inline-flex; align-items: center; gap: 6px;">
          <span style="color: var(--color-text-3);">大小</span>
          <span style="font-weight: 600;">{{ formattedSize }}</span>
        </span>
        <span
            v-if="responseContentType"
            style="display: inline-flex; align-items: center; gap: 6px; margin-left: auto;"
        >
          <span style="color: var(--color-text-3);">Content-Type</span>
          <span class="content-type-tag">{{ responseContentType }}</span>
        </span>
      </div>
      <a-tabs :default-active-key="isSqlApi ? 0 : 1" style="flex: 1; min-height: 0">
        <!-- SQL 执行元信息：tab 行最右侧 -->
        <template #extra>
          <span v-if="isSqlApi && !isRequestError" class="sql-exec-meta">
            <a-dropdown v-if="sqlTableData.length > 0" trigger="hover" @select="copySqlResult">
              <a-button type="text" size="mini">
                <template #icon><icon-copy /></template>
                复制
              </a-button>
              <template #content>
                <a-doption value="json">JSON 格式</a-doption>
                <a-doption value="csv">CSV 格式</a-doption>
              </template>
            </a-dropdown>
            <a-button v-if="sqlTableData.length > 0" type="text" size="mini" :loading="exportingExcel" @click="exportSqlResultExcel">
              <template #icon><icon-download /></template>
              导出 Excel
            </a-button>
            <template v-if="isDmlExecuted">影响 {{ sqlAffectedRows }} 行</template>
            <template v-else-if="sqlRowCount != null">
              共 {{ sqlRowCount }} 条<template v-if="sqlTruncated">（已截断：达到最大返回行数上限）</template>
            </template>
            <template v-if="sqlElapsedMs != null">，耗时 {{ sqlElapsedMs }}ms</template>
          </span>
        </template>
        <!-- SQL 执行结果：查询展示结果表格，DML/DDL 展示影响行数，失败展示错误详情 -->
        <a-tab-pane v-if="isSqlApi" :key="0" title="执行结果">
          <!-- 执行失败 -->
          <template v-if="isRequestError">
            <a-alert type="error" title="SQL 执行失败" style="margin-bottom: 16px;">
              <a-typography-text type="danger" style="font-size: 14px; white-space: pre-wrap; word-break: break-all">
                {{ props.debugResult?.errorMessage || '未知错误' }}
              </a-typography-text>
            </a-alert>
            <a-card v-if="props.debugResult?.requestUrl" title="请求信息" size="small" style="margin-bottom: 12px">
              <a-descriptions :column="1" size="small">
                <a-descriptions-item label="数据库">
                  <a-typography-text copyable style="word-break: break-all">
                    {{ props.debugResult?.requestUrl }}
                  </a-typography-text>
                </a-descriptions-item>
                <a-descriptions-item label="请求方法">
                  <a-tag>{{ props.debugResult?.requestMethod || 'SQL' }}</a-tag>
                </a-descriptions-item>
              </a-descriptions>
            </a-card>
            <a-card v-if="failedSql" title="执行的 SQL" size="small" style="margin-bottom: 12px">
              <pre class="request-body" style="max-height: 30vh">{{ failedSql }}</pre>
            </a-card>
          </template>
          <!-- DML / DDL -->
          <div v-else-if="isDmlExecuted" style="padding: 12px;">
            <a-result status="success" title="执行成功">
              <template #subtitle>
                影响 <span style="font-weight: 600; color: rgb(var(--primary-6));">{{ sqlAffectedRows }}</span> 行
              </template>
            </a-result>
          </div>
          <!-- 查询：vxe-table 虚拟滚动，大数据量下只渲染可视区 -->
          <template v-else>
            <!-- sql-table-host 是表格的直接父容器，高宽都量它（而不是根容器减魔法数字），
                 保证表格精确等于可用区域，否则 pane 的 overflow:auto 会再冒一层外滚动条 -->
            <div v-if="sqlColumns.length > 0" ref="sqlTableHostRef" class="sql-table-host">
              <vxe-table
                  :data="sqlTableData"
                  :height="tableScrollY"
                  :width="tableScrollX"
                  :scroll-y="{enabled: true, gt: 50}"
                  :scroll-x="{enabled: true, gt: 15}"
                  :row-config="{keyField: '_key'}"
                  :tooltip-config="{mode: 'title'}"
                  auto-resize
                  border
                  show-overflow
                  size="mini"
                  class="sql-result-vxe"
              >
                <vxe-column
                    v-for="col in sqlColumns"
                    :key="col"
                    :field="col"
                    :title="col"
                    :width="sqlColumnWidth(col)"
                    show-overflow
                />
              </vxe-table>
            </div>
            <a-empty v-else description="无数据"/>
          </template>
        </a-tab-pane>
        <a-tab-pane v-if="!isSqlApi" :key="1" title="实时响应">
          <ResponseViewer
              :content="props.debugResult?.bodyAsString"
              :content-type="responseContentType"
              :content-length="props.debugResult?.responseBytes"
              :raw-body="props.debugResult?.rawBody"
              show-download
          />
        </a-tab-pane>
        <a-tab-pane v-if="!isSqlApi" :key="2" title="请求头">
          <a-descriptions :column="1">
            <a-descriptions-item v-for="(value, key) in props.debugResult?.requestHeaders" :key="key" :label="key">
              {{ value }}
            </a-descriptions-item>
          </a-descriptions>
        </a-tab-pane>
        <a-tab-pane v-if="!isSqlApi" :key="3" title="请求体">
          <pre class="request-body">{{ formatRequestBody(props.debugResult?.requestBody) }}</pre>
        </a-tab-pane>
        <a-tab-pane v-if="!isSqlApi" :key="4" title="响应头">
          <a-descriptions :column="1">
            <a-descriptions-item v-for="(value, key) in props.debugResult?.responseHeaders" :key="key" :label="key">
              {{ value }}
            </a-descriptions-item>
          </a-descriptions>
        </a-tab-pane>
        <a-tab-pane :key="5">
          <template #title>
            <a-badge :count="assertBadge.count" :offset="[10, -2]" :dot-style="{ background: assertBadge.color }">断言结果</a-badge>
          </template>
          <template v-if="hasAssertionResults">
            <div class="assert-summary">
              <a-space>
                <a-tag color="green">通过: {{ assertPassCount }}</a-tag>
                <a-tag color="red">失败: {{ assertFailCount }}</a-tag>
              </a-space>
            </div>
            <a-table :columns="assertColumns" :data="assertData" bordered :pagination="false"/>
          </template>
          <template v-else>
            <a-empty description="未配置断言规则或断言结果为空"/>
          </template>
        </a-tab-pane>
        <a-tab-pane :key="6">
          <template #title>
            <a-badge :count="extractBadge.count" :offset="[10, -2]" :dot-style="{ background: extractBadge.color }">
              提取结果
            </a-badge>
          </template>
          <template v-if="hasExtractedVariables">
            <a-table :columns="extractionColumns" :data="extractionData" bordered :pagination="false"/>
          </template>
          <template v-else>
            <a-empty description="未配置提取规则或提取结果为空"/>
          </template>
        </a-tab-pane>
        <a-tab-pane :key="7">
          <template #title>
            <a-badge :count="unmatchedVars.length" :offset="[10, -2]">变量追踪</a-badge>
          </template>
          <template v-if="hasVariableTrack">
            <!-- 变量来源 -->
            <div class="track-section"
                 v-if="variableTrack.variableSources && Object.keys(variableTrack.variableSources).length > 0">
              <div class="track-section-title">变量来源（共 {{ Object.keys(variableTrack.variableSources).length }} 个）
              </div>
              <a-table :columns="sourceColumns" :data="sourceData" bordered :pagination="false" size="small"/>
            </div>

            <!-- 替换记录 -->
            <div class="track-section" v-if="trackReplaceRecords.length > 0">
              <div class="track-section-title">变量替换记录（共 {{ trackReplaceRecords.length }} 条）</div>
              <a-table :columns="trackColumns" :data="trackReplaceRecords" bordered :pagination="false" size="small"/>
            </div>

            <!-- 未匹配变量 -->
            <div class="track-section"
                 v-if="variableTrack.unmatchedVariables && variableTrack.unmatchedVariables.length > 0">
              <div class="track-section-title" style="color: #f5222d">
                未匹配变量（{{ variableTrack.unmatchedVariables.length }} 个）
              </div>
              <a-space wrap>
                <a-tag v-for="varName in variableTrack.unmatchedVariables" :key="varName" color="red">{{
                    varName
                  }}
                </a-tag>
              </a-space>
            </div>

            <!-- 提取变量 -->
            <div class="track-section" v-if="hasExtractedVariables">
              <div class="track-section-title">提取的变量（执行后）</div>
              <a-table :columns="extractionColumns" :data="extractionData" bordered :pagination="false" size="small"/>
            </div>
          </template>
          <template v-else>
            <a-empty description="本次请求未使用变量替换"/>
          </template>
        </a-tab-pane>
        <a-tab-pane :key="8" title="控制台">
          <template v-if="hasConsoleLogs">
            <div class="console-logs">
              <div
                  v-for="(log, index) in consoleLogs"
                  :key="index"
                  class="console-line"
                  :class="{ 'console-error': log.startsWith('[ERROR]') }"
              >
                {{ log }}
              </div>
            </div>
          </template>
          <template v-if="hasScriptAssertions">
            <div class="track-section-title" style="margin-top: 16px">脚本断言</div>
            <a-table :columns="scriptAssertColumns" :data="scriptAssertData" bordered :pagination="false" size="small"/>
          </template>
          <template v-if="!hasConsoleLogs && !hasScriptAssertions">
            <a-empty description="无脚本执行日志"></a-empty>
          </template>
        </a-tab-pane>
      </a-tabs>
    </template>
  </div>
</template>
<script setup lang="ts">
import ResponseViewer from "@/views/apiManager/component/ResponseViewer.vue";
import {computed, h, nextTick, onBeforeUnmount, onMounted, ref, watch} from "vue";
import {Message} from "@arco-design/web-vue";
import {VxeTable, VxeColumn} from 'vxe-table';
import 'vxe-table/lib/style.css';
import {ApiType} from "@/types/domain/api/apiEnum/ApiType";

const props = defineProps<{
  debugResult: any;
  apiType?: string;
}>()

const isSqlApi = computed(() => props.apiType === ApiType.SQL);

/**
 * SQL 结果表格滚动区高度（像素值）。
 * 不用 y:'100%'：Arco Table 在容器每次尺寸变化时都会重算 sticky 表头和滚动区，
 * 拖拽伸缩框时会每帧触发表格完整重排导致卡顿。
 * 改为 ResizeObserver + 120ms 防抖：拖拽过程中表格保持原高度不重排，停下后才适配。
 */
const rootRef = ref<HTMLElement | null>(null);
const sqlTableHostRef = ref<HTMLElement | null>(null);
const tableScrollY = ref(360);
const tableScrollX = ref(600);
let resizeObserver: ResizeObserver | null = null;
let hostObserver: ResizeObserver | null = null;
let resizeTimer: ReturnType<typeof setTimeout> | null = null;

const updateTableSize = () => {
  // 优先量表格直接父容器（精确等于可用区域，外层 pane 不会再出滚动条）；
  // 表格未挂载（无数据）时退回根容器估算
  const host = sqlTableHostRef.value;
  if (host) {
    const h = host.clientHeight;
    const w = host.clientWidth;
    if (h > 0) tableScrollY.value = h;
    if (w > 0) tableScrollX.value = w;
    return;
  }
  const h = rootRef.value?.clientHeight ?? 0;
  const w = rootRef.value?.clientWidth ?? 0;
  tableScrollY.value = Math.max(200, h - 48);
  if (w > 0) tableScrollX.value = w;
};

onMounted(() => {
  if (!rootRef.value) return;
  updateTableSize();
  resizeObserver = new ResizeObserver(() => {
    if (resizeTimer) clearTimeout(resizeTimer);
    resizeTimer = setTimeout(updateTableSize, 120);
  });
  resizeObserver.observe(rootRef.value);
});

// 表格宿主在拿到查询结果后才挂载（v-if），挂载后给它单独挂 observer 并立即校准尺寸
watch(sqlTableHostRef, (el, oldEl) => {
  if (oldEl && hostObserver) hostObserver.unobserve(oldEl);
  if (el) {
    if (!hostObserver) {
      hostObserver = new ResizeObserver(() => {
        if (resizeTimer) clearTimeout(resizeTimer);
        resizeTimer = setTimeout(updateTableSize, 120);
      });
    }
    hostObserver.observe(el);
    nextTick(updateTableSize);
  }
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  hostObserver?.disconnect();
  if (resizeTimer) clearTimeout(resizeTimer);
});

// 请求是否未发送成功（status === 'error' 表示请求未发出就失败了）
const isRequestError = computed(() => props.debugResult?.status === 'error');

/** SQL 执行失败时实际执行的语句（后端放在 X-Sql-Statement 请求头里，避免 byte[] 被序列化成 Base64） */
const failedSql = computed(() => {
  const headers = props.debugResult?.requestHeaders || {};
  return headers['X-Sql-Statement'] || headers['x-sql-statement'] || '';
});

// ===== 响应状态条：状态码 / 耗时 / 大小 =====
const statusCode = computed(() => props.debugResult?.statusCode);
const statusColor = computed(() => {
  const c = statusCode.value;
  if (c == null) return 'gray';
  if (c >= 200 && c < 300) return 'green';
  if (c >= 300 && c < 400) return 'orange';
  if (c >= 400) return 'red';
  return 'gray';
});
const responseTimeMs = computed(() => props.debugResult?.responseTimeMs);
const formattedSize = computed(() => {
  const b = props.debugResult?.responseBytes;
  if (b == null) return '-';
  if (b < 1024) return b + ' B';
  if (b < 1024 * 1024) return (b / 1024).toFixed(2) + ' KB';
  return (b / 1024 / 1024).toFixed(2) + ' MB';
});

const hasRequestHeaders = computed(() => {
  const headers = props.debugResult?.requestHeaders;
  return headers && Object.keys(headers).length > 0;
});

const responseContentType = computed(() => {
  const headers = props.debugResult?.responseHeaders || {};
  return headers['Content-Type']
      || headers['content-type']
      || headers['CONTENT-TYPE']
      || '';
});

// ===== SQL 结果（单语句：查询返回行数组，DML/DDL 返回 {affectedRows}） =====
const sqlParsedBody = computed(() => {
  const body = props.debugResult?.bodyAsString;
  if (!body) return null;
  try { return JSON.parse(body); } catch { return null; }
});

/** 查询结果（行数组） */
const isSqlResult = computed(() => {
  return Array.isArray(sqlParsedBody.value);
});

const isDmlExecuted = computed(() => {
  const parsed = sqlParsedBody.value;
  return parsed && !Array.isArray(parsed) && typeof parsed.affectedRows === 'number';
});

const sqlAffectedRows = computed(() => {
  const parsed = sqlParsedBody.value;
  return parsed?.affectedRows ?? 0;
});

const sqlRowCount = computed(() => {
  const headers = props.debugResult?.responseHeaders || {};
  const count = headers['X-Sql-Row-Count'] || headers['x-sql-row-count'];
  return count != null ? parseInt(count) : null;
});

const sqlTruncated = computed(() => {
  const headers = props.debugResult?.responseHeaders || {};
  return headers['X-Sql-Truncated'] === 'true' || headers['x-sql-truncated'] === 'true';
});

/** SQL 语句在数据库端的执行耗时（区别于整个请求耗时） */
const sqlElapsedMs = computed(() => {
  const headers = props.debugResult?.responseHeaders || {};
  const v = headers['X-Sql-Elapsed-Ms'] || headers['x-sql-elapsed-ms'];
  return v != null ? parseInt(v) : null;
});

/** 查询结果列名 */
const sqlColumns = computed<string[]>(() => {
  const parsed = sqlParsedBody.value;
  if (!Array.isArray(parsed) || parsed.length === 0) return [];
  return Object.keys(parsed[0]);
});

const sqlTableData = computed(() => {
  const parsed = sqlParsedBody.value;
  if (!Array.isArray(parsed)) return [];
  return parsed.map((row: any, idx: number) => ({...row, _key: idx}));
});

/**
 * 列宽按列名长度估算（中文按 2 个字符宽），固定 width 而不是 min-width：
 * vxe-table 对只给 min-width 的弹性列会分配剩余宽度，叠加 scroll-x 虚拟滚动时
 * 首列会被无限拉宽；固定宽度后超出容器由 scroll-x 出横向滚动条。
 */
const sqlColumnWidth = (col: string): number => {
  const charWidth = Array.from(col).reduce((w, ch) => w + (ch.charCodeAt(0) > 255 ? 2 : 1), 0);
  return Math.min(300, Math.max(150, charWidth * 9 + 24));
};

/** 导出 SQL 查询结果为 Excel（xlsx 按需动态加载，避免增大首屏体积） */
const exportingExcel = ref(false);
const exportSqlResultExcel = async () => {
  if (sqlTableData.value.length === 0) return;
  exportingExcel.value = true;
  try {
    const XLSX = await import('xlsx');
    const rows = sqlTableData.value.map(({_key, ...rest}) => rest);
    const sheet = XLSX.utils.json_to_sheet(rows, {header: sqlColumns.value});
    const book = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(book, sheet, '查询结果');
    const now = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    const fileName = `SQL查询结果_${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}.xlsx`;
    XLSX.writeFile(book, fileName);
    Message.success({content: `已导出 ${rows.length} 行`, duration: 2000});
  } catch (e) {
    Message.error({content: '导出失败', duration: 2000});
  } finally {
    exportingExcel.value = false;
  }
};
/** 复制 SQL 查询结果到剪贴板（JSON / CSV） */
const copySqlResult = async (format: string | number | Record<string, any> | undefined) => {
  const rows = sqlTableData.value.map(({_key, ...rest}) => rest);
  let text: string;
  if (format === 'csv') {
    const escapeCsv = (v: any): string => {
      if (v === null || v === undefined) return '';
      const s = typeof v === 'object' ? JSON.stringify(v) : String(v);
      return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    };
    const header = sqlColumns.value.map(escapeCsv).join(',');
    const lines = rows.map((r: any) => sqlColumns.value.map((c: string) => escapeCsv(r[c])).join(','));
    text = [header, ...lines].join('\n');
  } else {
    text = JSON.stringify(rows, null, 2);
  }
  try {
    await navigator.clipboard.writeText(text);
    Message.success({content: `已复制 ${rows.length} 行（${format === 'csv' ? 'CSV' : 'JSON'}）`, duration: 2000});
  } catch {
    // 非安全上下文（http 内网）降级：隐藏 textarea + execCommand
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    if (ok) {
      Message.success({content: `已复制 ${rows.length} 行（${format === 'csv' ? 'CSV' : 'JSON'}）`, duration: 2000});
    } else {
      Message.error({content: '复制失败，请手动选择复制', duration: 2000});
    }
  }
};

const formatRequestBody = (body: any): string => {
  if (!body) return '';
  if (typeof body === 'string') return body;
  if (Array.isArray(body)) {
    try {
      const decoder = new TextDecoder('utf-8');
      return decoder.decode(new Uint8Array(body));
    } catch (e) {
      return String(body);
    }
  }
  return String(body);
};

const hasExtractedVariables = computed(() => {
  const details = props.debugResult?.extractionDetails;
  if (details && details.length > 0) return true;
  const vars = props.debugResult?.extractedVariables;
  return vars && Object.keys(vars).length > 0;
});

/** 提取类型英文 → 中文映射 */
const extractTypeMap: Record<string, string> = {
  JSON_PATH: 'JSONPath',
  REGEX: '正则',
  HEADER: '响应头',
  COOKIE: 'Cookie',
  STATUS_CODE: '状态码'
};

const extractionColumns = [
  {title: '变量名', dataIndex: 'variableName', width: '20%'},
  {title: '提取类型', dataIndex: 'type', width: '12%'},
  {title: '表达式', dataIndex: 'expression', width: '23%'},
  {title: '提取值', dataIndex: 'value', width: '25%'},
  {title: '来源', dataIndex: 'source', width: '10%'}
];

const extractionData = computed(() => {
  // 优先使用带有来源信息的 extractionDetails
  const details = props.debugResult?.extractionDetails;
  if (details && details.length > 0) {
    return details.map((d: any) => ({
      variableName: d.variableName || '-',
      type: extractTypeMap[d.type] || d.type || '-',
      expression: d.expression || '-',
      value: d.value === null || d.value === undefined ? 'null' : String(d.value),
      source: sourceMap[d.source] || d.source || '-'
    }));
  }
  // 兼容旧数据：回退到 extractedVariables
  const vars = props.debugResult?.extractedVariables;
  if (!vars) return [];
  return Object.entries(vars).map(([key, value]) => ({
    variableName: key,
    type: '-',
    expression: '-',
    value: value === null || value === undefined ? 'null' : (String(value) === '' ? '(空字符串)' : String(value)),
    source: '-'
  }));
});

const hasAssertionResults = computed(() => {
  const results = props.debugResult?.assertionResults;
  return results && results.length > 0;
});

const assertPassCount = computed(() => {
  const results = props.debugResult?.assertionResults || [];
  return results.filter((r: any) => r.success).length;
});

const assertFailCount = computed(() => {
  const results = props.debugResult?.assertionResults || [];
  return results.filter((r: any) => !r.success).length;
});

/** 断言 tab 徽标：有失败红色显示失败数，全部通过绿色显示通过数，无断言不显示 */
const assertBadge = computed(() => {
  if (assertFailCount.value > 0) return {count: assertFailCount.value, color: '#f5222d'};
  if (assertPassCount.value > 0) return {count: assertPassCount.value, color: '#52c41a'};
  return {count: 0, color: '#f5222d'};
});

/** 提取 tab 徽标：有未命中橙色显示未命中数，全部成功绿色显示成功数，无提取不显示 */
const extractBadge = computed(() => {
  const missed = missedExtractions.value.length;
  if (missed > 0) return {count: missed, color: '#ff7d00'};
  const total = extractionData.value.length;
  if (total > 0) return {count: total, color: '#52c41a'};
  return {count: 0, color: '#ff7d00'};
});

// ===== 失败计数（用于 Tab 标题徽标，免去逐个 Tab 排查）=====
const unmatchedVars = computed(() =>
    props.debugResult?.variableTrack?.unmatchedVariables || []
);
// 配置了提取规则但没取到值（value 为空/null）视为提取未命中
const missedExtractions = computed(() => {
  const details = props.debugResult?.extractionDetails || [];
  return details.filter((d: any) =>
      d.value === null || d.value === undefined || String(d.value).trim() === ''
  );
});

const assertColumns = [
  {
    title: '状态',
    dataIndex: 'success',
    width: '10%',
    render: ({record}: any) => {
      return record.success
          ? h('span', {style: {color: '#52c41a'}}, '✓ 通过')
          : h('span', {style: {color: '#f5222d'}}, '✗ 失败');
    }
  },
  {title: '来源', dataIndex: 'source', width: '10%'},
  {title: '断言类型', dataIndex: 'assertType', width: '10%'},
  {title: '断言关系', dataIndex: 'assertRelationship', width: '10%'},
  {title: '提示信息', dataIndex: 'assertTip', width: '30%'},
  {
    title: '实际值',
    dataIndex: 'actualValue',
    width: '30%',
    render: ({record}: any) => {
      const value = record.actualValue;
      if (!value || value === '-') return h('span', '-');
      // 超过 60 个字符则截断，hover 时显示完整内容
      const display = value.length > 60 ? value.substring(0, 60) + '...' : value;
      if (value.length > 60) {
        return h('a-tooltip', {content: value, position: 'top'}, {
          default: () => h('span', {class: 'actual-value'}, display)
        });
      }
      return h('span', {class: 'actual-value'}, display);
    }
  }
];

/** 断言类型英文 → 中文映射 */
const assertTypeMap: Record<string, string> = {
  HEADER: '响应头',
  BODY: '响应体',
  STATUS_CODE: '状态码',
  RESPONSE_TIME: '响应时间',
  CUSTOM: '自定义',
  SQL_ASSERT: 'SQL断言',
  SCHEMA: '结构校验'
};

/** 断言关系英文 → 中文映射 */
const assertRelationshipMap: Record<string, string> = {
  EQUALS: '等于',
  NOT_EQUALS: '不等于',
  CONTAINS: '包含',
  NOT_CONTAINS: '不包含',
  GT: '大于',
  LT: '小于',
  GE: '大于等于',
  LE: '小于等于',
  REGULAR: '正则匹配'
};

/** 规则来源英文 → 中文映射 */
const sourceMap: Record<string, string> = {
  GLOBAL: '全局配置',
  ENVIRONMENT: '环境配置',
  SCENE: '场景配置',
  API: '接口配置'
};

const assertData = computed(() => {
  const results = props.debugResult?.assertionResults;
  if (!results) return [];
  return results.map((r: any) => ({
    success: r.success,
    source: sourceMap[r.source] || r.source || '-',
    assertType: assertTypeMap[r.assertType] || r.assertType || '-',
    assertRelationship: assertRelationshipMap[r.assertRelationship] || r.assertRelationship || '-',
    assertTip: r.assertTip || '',
    actualValue: r.actualValue || '-'
  }));
});

// ========== 变量追踪 ==========

const variableTrack = computed(() => props.debugResult?.variableTrack);

const hasVariableTrack = computed(() => {
  const vt = variableTrack.value;
  if (!vt) return false;
  // 有变量来源、有替换记录、有未匹配变量、有提取变量，都算有追踪
  return (vt.variableSources && Object.keys(vt.variableSources).length > 0)
      || (vt.urlReplace || vt.headerReplaces?.length || vt.cookieReplaces?.length
          || vt.queryReplaces?.length || vt.bodyReplace)
      || (vt.unmatchedVariables && vt.unmatchedVariables.length > 0)
      || (vt.extractedVariables && Object.keys(vt.extractedVariables).length > 0);
});

const sourceColumns = [
  {title: '变量名', dataIndex: 'name', width: '40%'},
  {title: '变量值', dataIndex: 'value', width: '60%'}
];

const sourceData = computed(() => {
  const sources = variableTrack.value?.variableSources;
  if (!sources) return [];
  return Object.entries(sources).map(([name, value]) => ({
    name,
    value: value === null || value === undefined ? 'null' : String(value)
  }));
});

const trackColumns = [
  {title: '位置', dataIndex: 'location', width: '12%'},
  {title: '参数名', dataIndex: 'name', width: '15%'},
  {title: '替换前', dataIndex: 'before', width: '28%'},
  {title: '替换后', dataIndex: 'after', width: '28%'},
  {title: '涉及变量', dataIndex: 'variables', width: '17%'}
];

const trackReplaceRecords = computed(() => {
  const vt = variableTrack.value;
  if (!vt) return [];
  const records: any[] = [];

  if (vt.urlReplace) {
    records.push({
      location: 'URL',
      name: vt.urlReplace.name || '-',
      before: vt.urlReplace.before || '-',
      after: vt.urlReplace.after || '-',
      variables: vt.urlReplace.variables?.join(', ') || '-'
    });
  }

  (vt.headerReplaces || []).forEach((r: any) => records.push({
    location: 'Header',
    name: r.name || '-',
    before: r.before || '-',
    after: r.after || '-',
    variables: r.variables?.join(', ') || '-'
  }));

  (vt.cookieReplaces || []).forEach((r: any) => records.push({
    location: 'Cookie',
    name: r.name || '-',
    before: r.before || '-',
    after: r.after || '-',
    variables: r.variables?.join(', ') || '-'
  }));

  (vt.queryReplaces || []).forEach((r: any) => records.push({
    location: 'Query',
    name: r.name || '-',
    before: r.before || '-',
    after: r.after || '-',
    variables: r.variables?.join(', ') || '-'
  }));

  if (vt.bodyReplace) {
    records.push({
      location: 'Body',
      name: vt.bodyReplace.name || '-',
      before: vt.bodyReplace.before || '-',
      after: vt.bodyReplace.after || '-',
      variables: vt.bodyReplace.variables?.join(', ') || '-'
    });
  }

  return records;
});

// ========== 控制台日志 ==========

const consoleLogs = computed(() => props.debugResult?.scriptConsoleLog || []);
const hasConsoleLogs = computed(() => consoleLogs.value.length > 0);

const scriptAssertions = computed(() => props.debugResult?.scriptAssertions || []);
const hasScriptAssertions = computed(() => scriptAssertions.value.length > 0);

const scriptAssertColumns = [
  {
    title: '状态',
    dataIndex: 'success',
    width: '15%',
    render: ({record}: any) => {
      return record.success
          ? h('span', {style: {color: '#52c41a'}}, '✓ 通过')
          : h('span', {style: {color: '#f5222d'}}, '✗ 失败');
    }
  },
  {title: '断言信息', dataIndex: 'message', width: '85%'}
];

const scriptAssertData = computed(() => {
  return scriptAssertions.value.map((a: any) => ({
    success: a.success,
    message: a.message || ''
  }));
});

</script>
<style scoped lang="less">
/* 横向截断：tabs 是根 flex 容器的子项，宽表格（多列 SQL 结果）会把 min-width:auto 的
   flex 项撑破容器；全链 min-width:0 后由 vxe-table 自身的 scroll-x 接管横向滚动 */
:deep(.arco-tabs) {
  min-width: 0;
}
:deep(.arco-tabs-content) {
  min-width: 0;
}

.request-body {
  margin: 0;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 4px;
  font-family: 'SF Mono', 'Monaco', 'Cascadia Code', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 60vh;
  overflow: auto;
}

.assert-summary {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f8f9fa;
  border-radius: 4px;
}

.actual-value {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

/* 让 Tabs 组件充满父容器高度 */
:deep(.arco-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.arco-tabs-content) {
  flex: 1;
  min-height: 0;
}

:deep(.arco-tabs-content-list) {
  height: 100%;
}

/* Arco 默认 content-item 是 height:0/active 时 height:auto，百分比高度链在这里断掉，
   显式 100% 后 pane 才能填满 tabs 内容区 */
:deep(.arco-tabs-content-item) {
  height: 100%;
}

:deep(.arco-tabs-pane) {
  height: 100%;
  overflow: auto;
}

.track-section {
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
}

.track-section-title {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
  padding-left: 4px;
  border-left: 3px solid #0969da;
}

.console-logs {
  background: #1e1e1e;
  border-radius: 4px;
  padding: 12px;
  max-height: 50vh;
  overflow: auto;
  font-family: 'SF Mono', 'Monaco', 'Cascadia Code', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.console-line {
  color: #d4d4d4;
  white-space: pre-wrap;
  word-break: break-all;
  padding: 2px 0;
}

.console-error {
  color: #f48771;
}

.sql-exec-meta {
  font-size: 13px;
  color: var(--color-text-3);
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* vxe-table 贴合 Arco 风格 */
.sql-result-vxe {
  font-size: 13px;
  --vxe-ui-font-color: var(--color-text-1);
  --vxe-ui-table-header-background-color: var(--color-fill-2);
  --vxe-ui-table-row-hover-background-color: var(--color-fill-1);
}

/* 查询结果表格宿主：填满 pane，滚动只发生在 vxe-table 内部 */
.sql-table-host {
  height: 100%;
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.content-type-tag {
  padding: 2px 8px;
  background: var(--color-primary-light-1);
  border: 1px solid var(--color-primary-light-3);
  border-radius: 4px;
  color: rgb(var(--primary-6));
  font-family: 'SF Mono', 'Monaco', 'Cascadia Code', 'Fira Code', monospace;
  font-size: 12px;
}

</style>
