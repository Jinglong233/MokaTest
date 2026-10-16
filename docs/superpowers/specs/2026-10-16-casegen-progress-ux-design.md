# 用例生成工作流页（caseGen）进度看板改版设计

日期：2026-10-16
状态：已评审（用户确认方向 A：进度看板为主）
范围：仅 `frontEnd/src/views/qa/ai/caseGen.vue` 及其子组件；后端零改动；AI 工作台链路、需求池列表页不在范围内

## 背景

需求池「AI 生成」按钮跳转到的用例生成工作流页（caseGen.vue）生成过程交互割裂：
进度叙事写在右下角 240px 聊天框的 `_init` 假气泡里，维度状态在左栏，产出在右上，
三处互不关联，用户生成中不知道看哪。调研主流 AI 产品（Deep Research / Manus / v0 等）
已收敛出共识：计划清单逐步点亮 + 过程折叠 + 产出实时可见 + 固定的"当前阶段/进度"区域。

后端 V2 链路事件流（`stage` / `dimensions` / `point_start` / `point_done` / `case` /
`audit` / `result` / `error` / `stopped`）已完整支撑新呈现，无需后端改动。

## 目标

1. 生成中用户一眼能回答：进行到哪一步、进度多少、当前在生成什么、花了多久；
2. 所有终态（完成/停止/失败/断线）都有确定 UI 态和明确的下一步动作；
3. 问答/逐点补充能力完整保留，但不再与进度展示争抢视线。

## 设计：三区职责重划（布局骨架不动）

### ① Header → 进度指挥条（唯一权威进度源）

新组件 `caseGen/GenProgressBar.vue`：
阶段名 + 进度条 + 当前生成维度名 + 已用时间 + 停止按钮。
阶段映射：`context`→解析需求上下文、`dimensions` 到达后→逐点生成 x/y、
`audit`→覆盖审计、`result`→完成。生成结束后进度条收起，恢复原覆盖计数展示。
数据来源全部为现有事件与 `dimensions` 状态计数，无新数据需求。

### ② TestPointSidebar → 任务清单（小改）

- 正在生成的维度行高亮 + 呼吸动画，列表自动滚动跟随；
- 完成维度挂产出计数（`✓ 标题 · N 条`，计数由 caseGen 按 drafts.coverPoints 聚合传入）；
- FAILED 维度显示「重试」入口（点击 = 对该维度发逐点重生 `streamCases({recordNo, targetTpId})`）。

### ③ PointDrafts → 产出主区（小改）

- streaming 草稿落入加入场动画；
- streaming 区草稿标注来源维度标题（`coverPoints[0]` 映射维度名）；
- 其余（采纳/编辑/删除/已采纳折叠区）不动。

### ④ PointChat → 可折叠「过程日志 + 追问」栏

- 生成中：消息区默认折叠成单行事件跑马灯（最近一条事件，如
  "✓ 完成 登录成功路径（3 条）→ 正在生成 密码长度边界"），点击展开完整过程；
- 生成结束：自动收起为单行总结；
- 输入框常驻，逐点追问/补充生成逻辑（`onPointSend`）不变；
- 批量生成进度不再写聊天气泡；`_init` 假气泡的进度叙事移除
  （自动开始的用户消息「生成用例」保留，完成时推一条总结性 assistant 消息）。

### ⑤ 意外防护（终态 → UI 态映射）

| 终态 | UI | 下一步动作 |
|---|---|---|
| `result` | 进度条完成态 + 总结行 | 采纳 / 追问补充 |
| `stopped` | 「已停止，已保留 N 条草稿」 | 继续生成 / 采纳现有 |
| `error`（含单点 FAILED） | 失败维度标红 + 原因 | 单维度重试（不重跑全部） |
| 断线/刷新 | 自动重连续流（2026-10-16 已修） | 无感知 |
| 重连失败（expired） | 静态恢复 + 明确提示 | 「继续生成」按钮 |

## 约束（必须保留的既有行为）

- 停止：`stopGeneration` 带 `tpId` 精确停止、气泡 `_stopped` 标记（2026-10-16 修复）；
- 刷新重连：`resumeActiveStream` / `reattachStream` / `onExpired` 静态恢复（2026-10-16 修复）；
- `restoredRecordNo` 去重、`selectTestPoint` 生成中先停止（BUG#4）、逐点消息按 tpId 隔离（BUG#6）；
- 采纳/编辑/删除的 `saveDrafts` 持久化逻辑。

## 改动清单

| 文件 | 改动 |
|---|---|
| `caseGen/GenProgressBar.vue`（新） | 进度指挥条组件 |
| `caseGen.vue` | 编排重构：进度状态机、跑马灯数据、折叠控制；保留全部既有修复 |
| `caseGen/TestPointSidebar.vue` | 高亮/计数/重试/自动跟随 |
| `caseGen/PointDrafts.vue` | 入场动画 + 来源维度标签 |
| `caseGen/PointChat.vue` | 支持消息区折叠（输入框常驻） |
| `views/qa/locale/zh-CN/ai.ts`、`en-US/ai.ts` | 新文案 |

## 验证

- `npm run build` 通过；
- 手动：新生成全流程（进度条阶段流转正确）、生成中停止、单维度失败重试、
  生成中刷新（重连续流且进度条正确）、生成结束后折叠总结、逐点追问回归。
