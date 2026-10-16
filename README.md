<p align="center">
  <img src="docs/images/logo.png" alt="MokaTest" width="200" />
</p>
<h3 align="center">AI 驱动的一站式自动化测试平台</h3>
<p align="center">
  <img src="https://img.shields.io/badge/license-BSL%201.1-blue" alt="License">
  <img src="https://img.shields.io/badge/Spring%20Boot-3-brightgreen" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue-3-brightgreen" alt="Vue">
  <img src="https://img.shields.io/badge/MySQL-8.0-orange" alt="MySQL">
  <img src="https://img.shields.io/badge/Docker-compose-blue" alt="Docker">
</p>
<p align="center">
  <a href="https://mokatest.cn">在线演示</a>（账号 <code>mokatest</code> / <code>mokatest</code>） ·
  <a href="https://mokatest.cn/docs/">使用文档</a>
</p>
<p align="center">
  <a href="https://github.com/Jinglong233/MokaTest">GitHub</a> ·
  <a href="https://gitee.com/Jinglong233/MokaTest">Gitee（国内镜像）</a>
</p>

---

MokaTest 是面向测试团队的 AI 驱动一站式自动化测试平台，覆盖从需求到报告的完整测试流程：

- **AI 能力**：对话式 AI 工作台、AI 生成测试用例（功能用例四阶段流水线 / 接口调试用例）、AI 需求评审、执行失败智能分诊（自动判别数据/环境/产品 BUG）、知识库增强（RAG），AI 贯穿测试全流程
- **UI 自动化**：场景可视化编排、元素库集中管理、调试支持暂停/断点/执行到指定步骤，定位失败时 AI 自动修复
- **质量管理**：需求池（Epic/Story 层级）、BUG 池、测试用例、血缘追踪，缺陷全生命周期可追溯
- **API 测试**：接口调试、断言/提取/脚本、参数级 Mock、数据模板、多环境一键切换
- **测试计划**：定时/手动触发、并发执行、Webhook 通知、测试报告
- **团队协作**：团队/项目/成员三级权限隔离，角色灵活配置，站内信通知

## AI 功能

AI 不是附属插件，而是贯穿测试全流程的核心能力：

| 能力 | 说明 |
|------|------|
| **AI 工作台** | 自然语言对话查需求/用例/BUG/知识库，对话式生成并入库用例，支持快捷指令模板 |
| **AI 生成用例** | 需求 → 拆测试点 → 逐点生成 → 覆盖审计 → 采纳入库，全流程 AI 辅助、自动去重 |
| **接口用例生成** | 基于接口定义生成带断言/参数变体的调试用例，一键挂到接口下即可执行 |
| **AI 需求评审** | 从完整性/可测性/边界/安全维度评审需求，识别描述不清之处，评分回显需求列表 |
| **失败智能分诊** | 场景执行失败自动判别是数据问题、环境问题还是产品 BUG，后者一键生成 BUG 单 |
| **AI 定位自愈** | UI 元素定位失效时自动推断新定位器并验证重试，是否写回元素库由人工确认 |
| **知识库（RAG）** | 上传项目文档建立索引，所有 AI 功能自动参考并标注出处，让输出贴合项目实际 |


| AI工作台                                                          | SKILL                             |
|----------------------------------------------------------------|-----------------------------------|
| ![AI-1.png](docs/images/AI-1.png)                              | ![AI-2.png](docs/images/AI-2.png) |
| **知识库**                                                        | **AI配置**                          |
| ![AI-3.png](docs/images/AI-3.png) | ![AI-4.png](docs/images/AI-4.png) |
| **AI生成接口用例**                                                        |                           |
| ![AI-5.png](docs/images/AI-5.png) |  |


## 项目状态

本项目目前处于早期阶段（v0.x），由个人独立开发维护：功能持续迭代中，接口和表结构可能变动；部分模块尚不完善，可能存在缺陷。生产环境使用请自行充分评估，也欢迎通过 Issue 反馈你遇到的问题。

## UI 展示

| 质量管理                          | API 测试                          |
|-------------------------------|---------------------------------|
| ![质量管理](docs/images/QM.png)   | ![API 测试](docs/images/API.png)  |
| **UI 自动化**                    | **团队协作**                        |
| ![UI 自动化](docs/images/UI.png) | ![团队协作](docs/images/Team.png)   |
| **计划任务**                      | **报告**                          |
| ![UI 自动化](docs/images/Plan.png) | ![报告](docs/images/Report.png) |

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Arco Design Vue + TypeScript |
| 后端 | Spring Boot 3 + MyBatis-Plus + Sa-Token |
| AI | LangChain4j（Agent / RAG / 护栏）+ Qdrant（向量检索，可选）|
| 数据库 | MySQL 8.0 |
| 其他 | Playwright（UI 自动化）、MinIO（文件存储）|

## 快速开始（Docker 一键部署）

```bash
git clone https://github.com/Jinglong233/MokaTest.git 
# git clone https://gitee.com/Jinglong233/MokaTest.git  #gitee 仓库

cp .env.example .env       # 按需修改密码和端口
docker compose up -d --build
```

首次构建需要拉取基础镜像和依赖，耗时较长，请耐心等待。启动完成后：

- 访问地址：`http://<服务器IP>`（端口由 `.env` 的 `HOST_HTTP_PORT` 控制，默认 80）
- 默认账号：`admin` / `abc123`（**生产部署请立即修改**）

服务包含：MySQL、后端、前端 Nginx、MinIO 对象存储，全部容器化，无需本机安装任何依赖。

**启用 AI 功能**：登录后进入「AI 中心 → AI 配置」，添加模型配置（填入你的模型服务地址与 API Key）即可使用全部 AI 功能。知识库的语义检索（RAG）需额外部署 [Qdrant](https://qdrant.tech/) 向量库（可选，不部署不影响其他 AI 功能，仅知识库引用失效）。

## 本地开发

### 后端

```bash
# 准备数据库：创建数据库并导入表结构
mysql -u root -p < backEnd/sql/mokatest_full_schema.sql

cd backEnd
```

后端默认端口：`7529`

### 前端

```bash
cd frontEnd
yarn install
yarn dev
```

前端默认端口：`5173`（Vite 默认）

## 项目结构

```
MokaTest/
├── backEnd/                    # 后端服务
│   ├── platform-starter/       # 启动模块（Spring Boot 入口）
│   ├── platform-ai/            # AI 能力中枢（Agent / RAG / 模型管理 / 护栏）
│   ├── platform-automation/    # UI/API 自动化测试核心模块
│   ├── platform-qa/            # 质量管理模块（需求/BUG/用例）
│   ├── platform-common/        # 公共模块
│   └── sql/                    # 数据库脚本
├── frontEnd/                   # 前端（Vue 3 + Arco Design）
├── docs/images/                # README 截图
├── docker-compose.yml          # Docker 一键部署
├── .env.example                # 环境变量模板
└── LICENSE                     # BSL 1.1
```

---

## 参与贡献

欢迎任何形式的参与：

- 遇到 Bug 或有功能建议，请提交 Issue（请尽量附上复现步骤、环境信息）
- 欢迎提交 Pull Request，较大改动建议先开 Issue 讨论方案
- 觉得项目有用，点个 Star 就是对作者最大的支持

---

## 联系方式

欢迎交流使用问题、提需求建议或洽谈商业授权，添加微信时请备注「MokaTest」：

<p align="center">
  <img src="docs/images/wechat.png" alt="微信二维码" width="200" />
</p>

也可以关注公众号「测试开发日记」，回复「MokaTest录制插件」获取浏览器录制插件。
<p align="center">
  <img src="docs/images/official-account-qrcode.png" alt="微信二维码" width="200" />
</p>

QQ群
<p align="center">
  <img src="docs/images/QQ.png" alt="微信二维码" width="200" />
</p>

---

## License

本项目采用 [Business Source License 1.1](LICENSE)（BSL）：

- 学习、测试、评估、非生产环境使用：**完全免费**
- 公司内网部署、内部业务自用（含生产环境）：**免费**，无需授权
- 以下用途需获得**商业授权**：将本软件作为托管/云服务提供给第三方；转售；包装进与作者构成竞争关系的产品或服务
- 每个版本发布满 4 年后自动转为 **Apache License 2.0**，届时可自由商用

商业授权请联系项目作者。

---

## 品牌与商标

- **MokaTest 名称与 Logo 不随代码协议授权**：你可以自由使用、修改、分发代码，但不得在对外发布的产品或服务中使用 MokaTest 名称或 Logo，以免造成混淆。
- 本项目界面中的 Logo 及部分配图为 AI 生成图片，未主张版权；如对素材有更高要求，可自行替换。
- 前端部分页面基于 [Arco Design Pro Vue](https://github.com/arco-design/arco-design-pro-vue)（MIT License）开发，其自带 banner 图片素材版权归原项目所有，遵循 MIT 协议使用，详见 [NOTICE](NOTICE)。

## 第三方组件与素材

本项目依赖的所有开源组件及其协议见 [NOTICE](NOTICE) 文件。
