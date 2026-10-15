# AI 工作台「生成测试用例」意图分流设计

日期：2026-10-15
状态：已评审（用户确认方案 A）

## 背景

AI 工作台（`/ai/workbench`）意图识别基于 LangChain4j function calling：LLM 读工具描述自主选路，无前置分类器。现有 `start_case_generation` 只覆盖「需求 → 功能用例」链路；「接口 → 接口用例」链路（`AiGenerateChatModal`，platform-automation）未接入工作台。

## 目标

用户说「生成测试用例」时：

1. 意图不明 → 推**类型选择卡片**（功能用例 / 接口用例 / UI 自动化用例-置灰"即将上线"）
2. 选择「功能用例」→ 走现有 `start_case_generation` → caseGen 工作流页链路
3. 选择「接口用例」（或直接表达"给 XX 接口生成用例"）→ `search_apis` 推**接口候选卡片** → 点击候选直接唤起 `AiGenerateChatModal`（`scene="apiCase"`）
4. UI 自动化用例为规划中能力，本次只预留置灰入口

## 方案（A：工具 + 白名单 + 卡片，对齐现有 casegen/review 模式）

### 后端（platform-qa，agent 包）

| 改动 | 说明 |
|---|---|
| 新增 `AskCaseGenTypeTool` | `@Component implements AgentTool`，name=`ask_case_gen_type`，无参，权限 `qa:ai:use`，返回简单 JSON 触发前端卡片 |
| 新增 `SearchApisTool` | name=`search_apis`，入参 `keyword`，权限用接口模块的查看权限，调用 platform-automation 的接口查询服务，返回候选列表 `[{id, name, method, path}]`（上限 10 条） |
| `AgentController` 结果白名单 | 加入上述两个工具名，使 tool_result 推送给前端 |
| `AgentAssistant.SYSTEM_PROMPT` 路由规则 | ①生成用例未指明类型 → 调 `ask_case_gen_type`；②接口用例未指明接口 → 调 `search_apis` 列候选；③一次只为一个接口生成 |

### 前端

| 改动 | 说明 |
|---|---|
| `workbench.vue` `onToolEnd` | 新增两个卡片分支：`case-type-select`、`api-candidates` |
| `CaseTypeCard.vue`（新） | 三个选项按钮；功能/接口用例点击 = 替用户发送明确消息（复用现有 LLM 路由）；UI 自动化置灰 |
| `ApiCandidateCard.vue`（新） | 接口列表（名称/方法/路径），点击 emit `cardEvent`，workbench 直接 `open()` `AiGenerateChatModal`（不经过 LLM） |
| `AiChatPanel.vue` fallback | 加两个 cardType 分支 |
| `workbench.vue` | 引入 `AiGenerateChatModal` + ref，处理 `selectApi` 卡片事件 |
| i18n | 新工具标签、卡片文案入 locale |

### 不做的事（YAGNI）

- 不加 `start_api_case_generation` 工具：候选卡片点击直接唤起弹窗，弹窗自身校验接口与权限
- 不做后端前置意图分类器
- UI 自动化用例生成能力本身不在本期

## 数据流

```
用户: "帮我生成测试用例"
  → LLM 调 ask_case_gen_type → tool_result(白名单) → 前端 CaseTypeCard
用户点击「接口用例」→ 前端发送 "我要生成接口测试用例"
  → LLM 追问关键词或调 search_apis → tool_result(白名单) → ApiCandidateCard
用户点击某接口 → 前端 cardEvent → AiGenerateChatModal.open(scene="apiCase", entityId=apiId)
  → 走现有 /api/ai/apiCase 生成-草稿-采纳流程
```

## 验证

- 后端：platform-qa 模块编译通过
- 前端：`npm run build` 通过
- 手动：见交付时的验证清单（意图不明分流、直接表达接口意图、功能用例回归、权限兜底）
