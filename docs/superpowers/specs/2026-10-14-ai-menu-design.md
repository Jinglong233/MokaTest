# AI 中心菜单重组设计

日期：2026-10-14
状态：已确认（方案 A)

## 背景

前端 AI 相关菜单分散在三处：

- 「质量保障」(`/qa`, order 3)下：AI 工作台、AI 技能（可见）,AI 评审、用例生成（隐藏页）
- 顶级菜单「知识库」(`/knowledge`, order 92)
- 顶级菜单「AI 配置」(`/aiConfig`, order 92)

目标：新建顶级菜单「AI 中心」，将上述菜单收纳为子菜单。仅调整路由与国际化，不移动视图文件、不改后端、不改权限编码。

## 约束

- 页面代码大量使用路由 name 跳转（如 `router.push({name: 'AiReview'})`、`{name: 'KnowledgeEdit'}`)，所有路由 name 必须保持不变，只改 path。
- 父级菜单 `meta.permission` 数组语义为「任一满足即显示」(`hooks/permission.ts` 的 `accessRouterByPermission` / `hasAnyPermission`)。
- 旧路径需保留 redirect，避免旧链接/书签失效；redirect 需透传 query（知识库编辑页通过 query 传 id)。

## 方案

### 1. 新建 `frontEnd/src/router/routes/modules/ai.ts`

顶级路由 `/ai`,name `Ai`,redirect `/ai/workbench`，组件 `DEFAULT_LAYOUT`。
meta:`locale: 'menu.ai'`、`icon: 'icon-robot'`、`order: 5`、`requiresAuth: false`、`requiresProject: true`、`permission: ['qa:ai:use', 'knowledge:view', 'project:aiconfig:update']`。

子路由（组件文件均不移动）:

| path | name | 组件 | 菜单可见 |
|---|---|---|---|
| `workbench` | `AiWorkbench` | `@/views/qa/ai/workbench.vue` | 是 |
| `skills` | `AiSkills` | `@/views/qa/ai/skills.vue` | 是 |
| `knowledge` | `KnowledgeIndex` | `@/views/knowledge/index.vue` | 是 |
| `config` | `AiConfigIndex` | `@/views/aiConfig/index.vue` | 是 |
| `review` | `AiReview` | `@/views/qa/ai/review.vue` | 否（hideInMenu) |
| `caseGen` | `AiCaseGen` | `@/views/qa/ai/caseGen.vue` | 否（hideInMenu) |
| `knowledge/edit` | `KnowledgeEdit` | `@/views/knowledge/edit.vue` | 否（hideInMenu,activeMenu: 'KnowledgeIndex') |

各子路由 permission 沿用原值：AI 工作台/技能/评审/用例生成 `qa:ai:use`；知识库 `knowledge:view`;AI 配置 `project:aiconfig:update`。`ignoreCache` 沿用原设置（知识库、AI 配置为 true)。

### 2. 旧路由改为 redirect

- `qa.ts`:4 个 AI 子路由改为 redirect 到 `/ai/*`；父级 permission 数组移除 `qa:ai:use`。redirect 路由不占用已搬迁的 name。
- `knowledge.ts`：整模块改为 redirect(`/knowledge` → `/ai/knowledge`,`/knowledge/edit` → `/ai/knowledge/edit`),`hideInMenu`,name `Knowledge` 保留在父级 redirect 上。
- `aiConfig.ts`：整模块改为 redirect(`/aiConfig` → `/ai/config`),`hideInMenu`,name `AiConfig` 保留。
- query 透传：redirect 使用函数形式 `to => ({ path: ..., query: to.query })`。

### 3. 国际化

- `frontEnd/src/locale/zh-CN.ts` 增加 `'menu.ai': 'AI 中心'`
- `frontEnd/src/locale/en-US.ts` 增加 `'menu.ai': 'AI Center'`
- 子菜单复用现有 key(`menu.qa.aiWorkbench`、`menu.qa.aiSkills`、`menu.knowledge`、`menu.aiConfig` 等）,不新增。

## 明确不做

- 不移动 `views/qa/ai/`、`views/knowledge/`、`views/aiConfig/` 等视图文件
- 不改动后端 `platform-ai` 模块
- 不改动权限编码定义本身

## 验证

- 前端构建通过（`npm run build` 或类型检查）
- 菜单「AI 中心」出现 4 个可见子项，点击可正常打开
- 旧路径（`/qa/aiWorkbench`、`/knowledge/index`、`/knowledge/edit?id=xx`、`/aiConfig/index`）跳转后落到新路径且 query 保留
- 无 `qa:ai:use` 权限的用户不再看到「质量保障」菜单中残留的 AI 项
