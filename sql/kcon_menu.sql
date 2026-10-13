-- =============================================================
-- KCon 签到抽奖系统 · 菜单与权限初始化
-- 依赖：RuoYi 基础表（sys_menu）已存在，默认角色（role_id=2 普通角色）
-- 执行顺序：必须先执行 ry_20260320.sql（RuoYi 基础表）再执行本脚本
-- =============================================================

USE `kcon_lucky`;

-- 清理旧数据（可重复执行）
DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 3000 AND 3999;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 3000 AND 3999;

-- -------------------------------------------------------------
-- 隐藏 RuoYi 原生菜单（现场无需展示：首页/系统管理/系统监控/系统工具/官网/源码/文档）
-- visible='1' 表示隐藏，仅隐藏不删除，便于排障时临时放开
-- 注意：隐藏只是 UI 层面，真正收回权限靠下面的 sys_role_menu 清理
-- -------------------------------------------------------------
UPDATE `sys_menu` SET `visible` = '1'
 WHERE `menu_id` IN ('1', '2', '3', '4', '5');

UPDATE `sys_menu` SET `visible` = '1'
 WHERE `parent_id` IN ('1', '2', '3', '4', '5', '108');

-- -------------------------------------------------------------
-- 收回普通角色（RuoYi 默认 role_id=2）对原生菜单的授权
-- 若只隐藏菜单而不做这一步，隐藏只是心理安慰：
-- 任何持有普通角色的账号仍可直接调接口提权（改角色 / 跑建表 SQL / 删定时任务）
--
-- 本系统只有 KCon 现场功能（菜单 ID 3000-3999），其余原生菜单一律收回。
-- 注意不能只按 perms 前缀删——目录型菜单（系统管理/系统监控/系统工具/若依官网/日志管理）
-- 的 perms 为空，必须按 menu_id 区间一起清掉，否则它们仍会留在该角色的菜单树里。
-- -------------------------------------------------------------
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 2
   AND (`menu_id` < 3000 OR `menu_id` > 3999);

-- RuoYi 自带的测试账号，现场不需要，停用（保留行便于追溯）
UPDATE `sys_user` SET `status` = '1', `update_by` = 'admin'
 WHERE `user_name` = 'ry';

-- -------------------------------------------------------------
-- 一级菜单：平铺，不再套「KCon 现场」目录
--   现场高频：现场看板 → 兑奖核验 → 抽奖控制台
--   开场前：  参会名单 → 奖品设置
--   结束后：  中奖记录 → 兑奖日志
--   一次性：  数据管理（数据清场 + 数据迁移合并为一页两 tab）
-- -------------------------------------------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  (3001, '现场看板',   0, 1, 'dashboard', 'kcon/dashboard/index', '', '', 1, 0, 'C', '0', '0', 'kcon:attendee:list', 'dashboard', 'admin', NOW(), '签到率与统计'),
  (3005, '兑奖核验',   0, 2, 'verify',    'kcon/verify/index',    '', '', 1, 0, 'C', '0', '0', 'kcon:verify:query',  'search',    'admin', NOW(), '端口号+手机号核验发放'),
  (3007, '抽奖控制台', 0, 3, 'draw',      'kcon/draw/index',      '', '', 1, 0, 'C', '0', '0', 'kcon:draw:query',    'guide',     'admin', NOW(), '场次切换与抽奖操作（FR-16/19/20）'),
  (3002, '参会名单',   0, 4, 'attendee',  'kcon/attendee/index',  '', '', 1, 0, 'C', '0', '0', 'kcon:attendee:list', 'user',      'admin', NOW(), '名单管理与导入'),
  (3003, '奖品设置',   0, 5, 'prize',     'kcon/prize/index',     '', '', 1, 0, 'C', '0', '0', 'kcon:prize:list',    'star',      'admin', NOW(), '场次奖项档位'),
  (3004, '中奖记录',   0, 6, 'winner',    'kcon/winner/index',    '', '', 1, 0, 'C', '0', '0', 'kcon:winner:list',   'money',     'admin', NOW(), '中奖名单与状态'),
  (3008, '兑奖日志',   0, 7, 'verifylog', 'kcon/verifylog/index', '', '', 1, 0, 'C', '0', '0', 'kcon:verify:query',  'list',      'admin', NOW(), '核验操作追溯'),
  (3006, '数据管理',   0, 8, 'data',      'kcon/data/index',      '', '', 1, 0, 'C', '0', '0', 'kcon:data:reset',    'trash',     'admin', NOW(), '数据清场与全量备份迁移（仅超管）');

-- -------------------------------------------------------------
-- 按钮权限
-- -------------------------------------------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  -- 名单
  (3010, '名单查询', 3002, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:query',  '#', 'admin', NOW(), ''),
  (3011, '名单新增', 3002, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:add',    '#', 'admin', NOW(), ''),
  (3012, '名单修改', 3002, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:edit',   '#', 'admin', NOW(), ''),
  (3013, '名单删除', 3002, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:remove', '#', 'admin', NOW(), ''),
  (3014, '名单导入', 3002, 5, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:import', '#', 'admin', NOW(), ''),
  (3015, '名单导出', 3002, 6, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:export', '#', 'admin', NOW(), ''),
  -- 奖品
  (3020, '奖品新增', 3003, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:prize:add',    '#', 'admin', NOW(), ''),
  (3021, '奖品修改', 3003, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:prize:edit',   '#', 'admin', NOW(), ''),
  (3022, '奖品删除', 3003, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:prize:remove', '#', 'admin', NOW(), ''),
  -- 核验
  (3030, '确认发放', 3005, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:verify:receive', '#', 'admin', NOW(), ''),
  -- 中奖
  (3040, '中奖作废', 3004, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:winner:void', '#', 'admin', NOW(), ''),
  -- 抽奖控制台（FR-16/19/20）
  (3050, '场次查询', 3007, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:session:list', '#', 'admin', NOW(), ''),
  (3051, '抽奖操作', 3007, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:draw:operate', '#', 'admin', NOW(), '抽奖/作废/切换场次');
  -- 数据迁移不再单独设按钮权限：后端用 @ss.hasRole('admin') 控制，
  -- 前端对应按钮用 v-hasRole，避免留下一个谁也不校验的 kcon:data:migrate 权限点

-- -------------------------------------------------------------
-- 授权给超级管理员（role_id=1）与普通角色（role_id=2）
-- -------------------------------------------------------------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 3000 AND 3999;

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 3000 AND 3999;

-- -------------------------------------------------------------
-- 高危菜单只留给超管（role_id=1），从普通角色收回：
--   数据管理(3006) 内含「数据清场」与「数据迁移」——前者清空名单/签到/中奖/核验日志，
--   后者会用备份整体覆盖数据库。后端分别以 @ss.hasPermi('kcon:data:reset') 与
--   @ss.hasRole('admin') 控制；若仍授权给普通角色，轻则「侧边栏有入口、点进去 403」，
--   重则普通角色真能清场。
-- -------------------------------------------------------------
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 2
   AND `menu_id` = 3006;

SELECT CONCAT('菜单初始化完成，共 ', COUNT(*), ' 条') AS `结果`
FROM `sys_menu` WHERE `menu_id` BETWEEN 3000 AND 3999;
