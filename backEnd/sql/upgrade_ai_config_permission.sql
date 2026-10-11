-- =============================================================
-- 升级脚本：补 project:aiconfig:update 权限（项目维度 AI 配置）
-- 背景：/aiConfig 菜单与 /api/ai/config/* 接口要求该权限码，
--       但权限表从未插入该行，导致除超管外无人可见/可用
--       （含项目 owner、团队管理员）。
-- 执行一次即可；owner 与团队管理员自动获得（服务端按
-- 非 platform/team 权限全量下发），普通成员需在角色里分配。
-- =============================================================

INSERT INTO permission (name, code, type, parent_id, sort)
SELECT 'AI 配置管理', 'project:aiconfig:update', 'BUTTON', id, 29
FROM permission WHERE code = 'project'
  AND NOT EXISTS (SELECT 1 FROM permission WHERE code = 'project:aiconfig:update');

UPDATE permission SET scope_type='PROJECT', category='project_manage', is_project_management=1, is_project_execution=0
  WHERE code = 'project:aiconfig:update';
