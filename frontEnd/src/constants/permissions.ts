/**
 * 权限编码常量中心
 *
 * 所有权限码统一在此管理，避免页面里出现魔法字符串。
 * 新增功能时：
 * 1. 在此文件按模块新增常量；
 * 2. 后端 Controller 方法加 @SaCheckPermission("module:action:code")；
 * 3. 前端按钮加 v-permission="PERMISSIONS.MODULE_ACTION_CODE"；
 * 4. 在 role_permission / SQL 初始化里给需要该权限的角色分配权限（admin 自动拥有所有非 platform 权限）。
 */

// 团队与项目管理
export const TEAM_MEMBER_MANAGE = 'team:member:manage';
export const TEAM_ROLE_MANAGE = 'team:role:manage';
export const TEAM_DELETE = 'team:delete';

export const PROJECT_VIEW = 'project:view';
export const PROJECT_CREATE = 'project:create';
export const PROJECT_UPDATE = 'project:update';
export const PROJECT_DELETE = 'project:delete';

// 质量管理 - 需求
export const QA_REQUIREMENT_VIEW = 'qa:requirement:view';
export const QA_REQUIREMENT_CREATE = 'qa:requirement:create';
export const QA_REQUIREMENT_UPDATE = 'qa:requirement:update';
export const QA_REQUIREMENT_DELETE = 'qa:requirement:delete';
export const QA_REQUIREMENT_TRANSITION = 'qa:requirement:transition';

// 质量管理 - BUG
export const QA_BUG_VIEW = 'qa:bug:view';
export const QA_BUG_CREATE = 'qa:bug:create';
export const QA_BUG_UPDATE = 'qa:bug:update';
export const QA_BUG_DELETE = 'qa:bug:delete';
export const QA_BUG_TRANSITION = 'qa:bug:transition';
export const QA_BUG_COMMENT_VIEW = 'qa:bug:comment:view';
export const QA_BUG_COMMENT_CREATE = 'qa:bug:comment:create';
export const QA_BUG_COMMENT_DELETE = 'qa:bug:comment:delete';
export const QA_BUG_OPERATION_LOG_VIEW = 'qa:bug:operationlog:view';

// 质量管理 - 用例
export const QA_TEST_CASE_VIEW = 'qa:testcase:view';
export const QA_TEST_CASE_CREATE = 'qa:testcase:create';
export const QA_TEST_CASE_UPDATE = 'qa:testcase:update';
export const QA_TEST_CASE_DELETE = 'qa:testcase:delete';

// 质量管理 - 测试计划
export const QA_TEST_PLAN_VIEW = 'qa:testplan:view';
export const QA_TEST_PLAN_CREATE = 'qa:testplan:create';
export const QA_TEST_PLAN_UPDATE = 'qa:testplan:update';
export const QA_TEST_PLAN_DELETE = 'qa:testplan:delete';
export const QA_TEST_PLAN_EXECUTE = 'qa:testplan:execute';

// 质量管理 - 模块
export const QA_MODULE_VIEW = 'qa:module:view';
export const QA_MODULE_CREATE = 'qa:module:create';
export const QA_MODULE_UPDATE = 'qa:module:update';
export const QA_MODULE_DELETE = 'qa:module:delete';

// 质量管理 - 概览
export const QA_OVERVIEW_VIEW = 'qa:overview:view';

// 自动化测试 - UI 场景
export const AUTO_SCENE_VIEW = 'auto:scene:view';
export const AUTO_SCENE_CREATE = 'auto:scene:create';
export const AUTO_SCENE_UPDATE = 'auto:scene:update';
export const AUTO_SCENE_DELETE = 'auto:scene:delete';
export const AUTO_SCENE_EXECUTE = 'auto:scene:execute';

// 自动化测试 - API 接口
export const AUTO_API_VIEW = 'auto:api:view';
export const AUTO_API_CREATE = 'auto:api:create';
export const AUTO_API_UPDATE = 'auto:api:update';
export const AUTO_API_DELETE = 'auto:api:delete';
export const AUTO_API_EXECUTE = 'auto:api:execute';

// 自动化测试 - 任务/计划
export const AUTO_PLAN_VIEW = 'auto:plan:view';
export const AUTO_PLAN_CREATE = 'auto:plan:create';
export const AUTO_PLAN_UPDATE = 'auto:plan:update';
export const AUTO_PLAN_DELETE = 'auto:plan:delete';
export const AUTO_PLAN_EXECUTE = 'auto:plan:execute';

// 自动化测试 - Webhook
export const AUTO_PLAN_WEBHOOK_VIEW = 'auto:plan:webhook:view';
export const AUTO_PLAN_WEBHOOK_CREATE = 'auto:plan:webhook:create';
export const AUTO_PLAN_WEBHOOK_UPDATE = 'auto:plan:webhook:update';
export const AUTO_PLAN_WEBHOOK_DELETE = 'auto:plan:webhook:delete';

// 自动化测试 - 元素库
export const AUTO_ELEMENT_VIEW = 'auto:element:view';
export const AUTO_ELEMENT_CREATE = 'auto:element:create';
export const AUTO_ELEMENT_UPDATE = 'auto:element:update';
export const AUTO_ELEMENT_DELETE = 'auto:element:delete';

// 自动化测试 - 环境
export const AUTO_ENV_VIEW = 'auto:env:view';
export const AUTO_ENV_CREATE = 'auto:env:create';
export const AUTO_ENV_UPDATE = 'auto:env:update';
export const AUTO_ENV_DELETE = 'auto:env:delete';

// 自动化测试 - 全局变量
export const AUTO_GLOBAL_VAR_VIEW = 'auto:globalvar:view';
export const AUTO_GLOBAL_VAR_UPDATE = 'auto:globalvar:update';
export const AUTO_GLOBAL_VAR_DELETE = 'auto:globalvar:delete';

// 自动化测试 - 测试步骤
export const AUTO_STEP_VIEW = 'auto:step:view';
export const AUTO_STEP_CREATE = 'auto:step:create';
export const AUTO_STEP_UPDATE = 'auto:step:update';
export const AUTO_STEP_DELETE = 'auto:step:delete';

// 自动化测试 - 数据模板
export const AUTO_TEMPLATE_VIEW = 'auto:template:view';
export const AUTO_TEMPLATE_CREATE = 'auto:template:create';
export const AUTO_TEMPLATE_UPDATE = 'auto:template:update';
export const AUTO_TEMPLATE_DELETE = 'auto:template:delete';

// 自动化测试 - 自定义函数
export const AUTO_FUNCTION_VIEW = 'auto:function:view';
export const AUTO_FUNCTION_CREATE = 'auto:function:create';
export const AUTO_FUNCTION_UPDATE = 'auto:function:update';
export const AUTO_FUNCTION_DELETE = 'auto:function:delete';

// 自动化测试 - 概览
export const AUTO_OVERVIEW_VIEW = 'auto:overview:view';

// 测试报告
export const REPORT_VIEW = 'report:view';
export const REPORT_DELETE = 'report:delete';

// 平台级权限（仅超管可分配）
export const PLATFORM_TEAM_MANAGE = 'platform:team:manage';
export const PLATFORM_USER_MANAGE = 'platform:user:manage';
export const PLATFORM_PERMISSION_MANAGE = 'platform:permission:manage';

/**
 * 权限编码集合，便于遍历或校验
 */
export const ALL_PERMISSIONS = [
  TEAM_MEMBER_MANAGE,
  TEAM_ROLE_MANAGE,
  TEAM_DELETE,
  PROJECT_VIEW,
  PROJECT_CREATE,
  PROJECT_UPDATE,
  PROJECT_DELETE,
  QA_REQUIREMENT_VIEW,
  QA_REQUIREMENT_CREATE,
  QA_REQUIREMENT_UPDATE,
  QA_REQUIREMENT_DELETE,
  QA_REQUIREMENT_TRANSITION,
  QA_BUG_VIEW,
  QA_BUG_CREATE,
  QA_BUG_UPDATE,
  QA_BUG_DELETE,
  QA_BUG_TRANSITION,
  QA_BUG_COMMENT_VIEW,
  QA_BUG_COMMENT_CREATE,
  QA_BUG_COMMENT_DELETE,
  QA_BUG_OPERATION_LOG_VIEW,
  QA_TEST_CASE_VIEW,
  QA_TEST_CASE_CREATE,
  QA_TEST_CASE_UPDATE,
  QA_TEST_CASE_DELETE,
  QA_TEST_PLAN_VIEW,
  QA_TEST_PLAN_CREATE,
  QA_TEST_PLAN_UPDATE,
  QA_TEST_PLAN_DELETE,
  QA_TEST_PLAN_EXECUTE,
  QA_MODULE_VIEW,
  QA_MODULE_CREATE,
  QA_MODULE_UPDATE,
  QA_MODULE_DELETE,
  QA_OVERVIEW_VIEW,
  AUTO_SCENE_VIEW,
  AUTO_SCENE_CREATE,
  AUTO_SCENE_UPDATE,
  AUTO_SCENE_DELETE,
  AUTO_SCENE_EXECUTE,
  AUTO_API_VIEW,
  AUTO_API_CREATE,
  AUTO_API_UPDATE,
  AUTO_API_DELETE,
  AUTO_API_EXECUTE,
  AUTO_PLAN_VIEW,
  AUTO_PLAN_CREATE,
  AUTO_PLAN_UPDATE,
  AUTO_PLAN_DELETE,
  AUTO_PLAN_EXECUTE,
  AUTO_PLAN_WEBHOOK_VIEW,
  AUTO_PLAN_WEBHOOK_CREATE,
  AUTO_PLAN_WEBHOOK_UPDATE,
  AUTO_PLAN_WEBHOOK_DELETE,
  AUTO_ELEMENT_VIEW,
  AUTO_ELEMENT_CREATE,
  AUTO_ELEMENT_UPDATE,
  AUTO_ELEMENT_DELETE,
  AUTO_ENV_VIEW,
  AUTO_ENV_CREATE,
  AUTO_ENV_UPDATE,
  AUTO_ENV_DELETE,
  AUTO_GLOBAL_VAR_VIEW,
  AUTO_GLOBAL_VAR_UPDATE,
  AUTO_GLOBAL_VAR_DELETE,
  AUTO_STEP_VIEW,
  AUTO_STEP_CREATE,
  AUTO_STEP_UPDATE,
  AUTO_STEP_DELETE,
  AUTO_TEMPLATE_VIEW,
  AUTO_TEMPLATE_CREATE,
  AUTO_TEMPLATE_UPDATE,
  AUTO_TEMPLATE_DELETE,
  AUTO_FUNCTION_VIEW,
  AUTO_FUNCTION_CREATE,
  AUTO_FUNCTION_UPDATE,
  AUTO_FUNCTION_DELETE,
  AUTO_OVERVIEW_VIEW,
  REPORT_VIEW,
  REPORT_DELETE,
  PLATFORM_TEAM_MANAGE,
  PLATFORM_USER_MANAGE,
  PLATFORM_PERMISSION_MANAGE,
];

export default {
  TEAM_MEMBER_MANAGE,
  TEAM_ROLE_MANAGE,
  TEAM_DELETE,
  PROJECT_VIEW,
  PROJECT_CREATE,
  PROJECT_UPDATE,
  PROJECT_DELETE,
  QA_REQUIREMENT_VIEW,
  QA_REQUIREMENT_CREATE,
  QA_REQUIREMENT_UPDATE,
  QA_REQUIREMENT_DELETE,
  QA_REQUIREMENT_TRANSITION,
  QA_BUG_VIEW,
  QA_BUG_CREATE,
  QA_BUG_UPDATE,
  QA_BUG_DELETE,
  QA_BUG_TRANSITION,
  QA_BUG_COMMENT_VIEW,
  QA_BUG_COMMENT_CREATE,
  QA_BUG_COMMENT_DELETE,
  QA_BUG_OPERATION_LOG_VIEW,
  QA_TEST_CASE_VIEW,
  QA_TEST_CASE_CREATE,
  QA_TEST_CASE_UPDATE,
  QA_TEST_CASE_DELETE,
  QA_TEST_PLAN_VIEW,
  QA_TEST_PLAN_CREATE,
  QA_TEST_PLAN_UPDATE,
  QA_TEST_PLAN_DELETE,
  QA_TEST_PLAN_EXECUTE,
  QA_MODULE_VIEW,
  QA_MODULE_CREATE,
  QA_MODULE_UPDATE,
  QA_MODULE_DELETE,
  QA_OVERVIEW_VIEW,
  AUTO_SCENE_VIEW,
  AUTO_SCENE_CREATE,
  AUTO_SCENE_UPDATE,
  AUTO_SCENE_DELETE,
  AUTO_SCENE_EXECUTE,
  AUTO_API_VIEW,
  AUTO_API_CREATE,
  AUTO_API_UPDATE,
  AUTO_API_DELETE,
  AUTO_API_EXECUTE,
  AUTO_PLAN_VIEW,
  AUTO_PLAN_CREATE,
  AUTO_PLAN_UPDATE,
  AUTO_PLAN_DELETE,
  AUTO_PLAN_EXECUTE,
  AUTO_PLAN_WEBHOOK_VIEW,
  AUTO_PLAN_WEBHOOK_CREATE,
  AUTO_PLAN_WEBHOOK_UPDATE,
  AUTO_PLAN_WEBHOOK_DELETE,
  AUTO_ELEMENT_VIEW,
  AUTO_ELEMENT_CREATE,
  AUTO_ELEMENT_UPDATE,
  AUTO_ELEMENT_DELETE,
  AUTO_ENV_VIEW,
  AUTO_ENV_CREATE,
  AUTO_ENV_UPDATE,
  AUTO_ENV_DELETE,
  AUTO_GLOBAL_VAR_VIEW,
  AUTO_GLOBAL_VAR_UPDATE,
  AUTO_GLOBAL_VAR_DELETE,
  AUTO_STEP_VIEW,
  AUTO_STEP_CREATE,
  AUTO_STEP_UPDATE,
  AUTO_STEP_DELETE,
  AUTO_TEMPLATE_VIEW,
  AUTO_TEMPLATE_CREATE,
  AUTO_TEMPLATE_UPDATE,
  AUTO_TEMPLATE_DELETE,
  AUTO_FUNCTION_VIEW,
  AUTO_FUNCTION_CREATE,
  AUTO_FUNCTION_UPDATE,
  AUTO_FUNCTION_DELETE,
  AUTO_OVERVIEW_VIEW,
  REPORT_VIEW,
  REPORT_DELETE,
  PLATFORM_TEAM_MANAGE,
  PLATFORM_USER_MANAGE,
  PLATFORM_PERMISSION_MANAGE,
};
