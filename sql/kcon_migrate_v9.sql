-- =============================================================
-- KCon 2026 · 增量迁移 v9
--
-- 用途：把「用户管理」从 RuoYi 系统管理移到「设置」目录，作为简化的工作人员账号页
--
--   1. 收回 RuoYi 原生「系统管理 / 用户管理」的菜单可见性
--   2. 新增 KCon 菜单 3016「用户管理」（parent=3200 设置）+ 4 个按钮权限
--   3. 调整「设置」子菜单顺序：奖品设置 → 渠道来源映射 → 用户管理 → 数据管理
--   4. 给超管角色补齐授权，并从 common(2) / kcon_staff(3) 收回账号管理权限
--
-- 说明：
--   - 超管一般靠 user_id=1 旁路拿到全部菜单，但「绑定 admin 角色、user_id≠1」
--     的账号仍走 sys_role_menu，因此这里必须显式补授权，否则存量升级后看不到入口。
--   - 该页面走 /admin/staff 接口，角色由后端固定为 kcon_staff；
--     部门 / 岗位 / 角色多选 / 导入导出一律不暴露。
--
-- 幂等：可重复执行（ON DUPLICATE KEY UPDATE / 定向 DELETE）。
--      注意：重复执行会把 3016~3023 的菜单配置拉回本脚本口径（含顺序与可见性）。
-- 执行：docker compose exec -T mysql mysql -uroot -p<pwd> kcon_lucky < sql/kcon_migrate_v9.sql
-- =============================================================

USE `kcon_lucky`;

START TRANSACTION;

-- -------------------------------------------------------------
-- 1. 收回原生「系统管理 → 用户管理」可见性
-- -------------------------------------------------------------
UPDATE `sys_menu` SET `visible` = '1', `update_by` = 'admin', `update_time` = NOW()
 WHERE `menu_id` IN (1, 100);

-- -------------------------------------------------------------
-- 2. 设置 → 用户管理（页面 + 按钮）
-- -------------------------------------------------------------
INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  (3016, '用户管理', 3200, 3, 'staff', 'kcon/staff/index', '', '', 1, 0, 'C', '0', '0', 'kcon:staff:list', 'peoples', 'admin', NOW(), '现场工作人员账号（仅超管）')
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`), `parent_id` = VALUES(`parent_id`), `order_num` = VALUES(`order_num`),
  `path` = VALUES(`path`), `component` = VALUES(`component`), `query` = VALUES(`query`),
  `route_name` = VALUES(`route_name`), `is_frame` = VALUES(`is_frame`), `is_cache` = VALUES(`is_cache`),
  `menu_type` = VALUES(`menu_type`), `visible` = VALUES(`visible`), `status` = VALUES(`status`),
  `perms` = VALUES(`perms`), `icon` = VALUES(`icon`), `remark` = VALUES(`remark`);

INSERT INTO `sys_menu`
  (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`,
   `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  (3017, '账号新增', 3016, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:staff:add',      '#', 'admin', NOW(), ''),
  (3018, '重置密码', 3016, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:staff:resetPwd', '#', 'admin', NOW(), ''),
  (3019, '启用停用', 3016, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:staff:edit',     '#', 'admin', NOW(), ''),
  (3023, '账号删除', 3016, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:staff:remove',   '#', 'admin', NOW(), '')
ON DUPLICATE KEY UPDATE
  `menu_name` = VALUES(`menu_name`), `parent_id` = VALUES(`parent_id`), `order_num` = VALUES(`order_num`),
  `menu_type` = VALUES(`menu_type`), `visible` = VALUES(`visible`), `status` = VALUES(`status`),
  `perms` = VALUES(`perms`), `remark` = VALUES(`remark`);

-- -------------------------------------------------------------
-- 3. 设置子菜单顺序：奖品设置(1) → 渠道来源映射(2) → 用户管理(3) → 数据管理(4)
-- -------------------------------------------------------------
UPDATE `sys_menu` SET `order_num` = 1, `update_time` = NOW() WHERE `menu_id` = 3003;
UPDATE `sys_menu` SET `order_num` = 2, `update_time` = NOW() WHERE `menu_id` = 3009;
UPDATE `sys_menu` SET `order_num` = 3, `update_time` = NOW() WHERE `menu_id` = 3016;
UPDATE `sys_menu` SET `order_num` = 4, `update_time` = NOW() WHERE `menu_id` = 3006;

-- -------------------------------------------------------------
-- 4. 授权：超管补齐；common / kcon_staff 收回
-- -------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu`
 WHERE `menu_id` IN (3016, 3017, 3018, 3019, 3023);

DELETE FROM `sys_role_menu`
 WHERE `role_id` = 2
   AND `menu_id` IN (3016, 3017, 3018, 3019, 3023);

DELETE FROM `sys_role_menu`
 WHERE `role_id` = 3
   AND `menu_id` IN (3016, 3017, 3018, 3019, 3023);

COMMIT;

-- -------------------------------------------------------------
-- 5. 结果核对
-- -------------------------------------------------------------
SELECT `menu_id`, `menu_name`, `parent_id`, `order_num`, `visible`
  FROM `sys_menu`
 WHERE `menu_id` IN (1, 100, 3003, 3006, 3009, 3016)
 ORDER BY `menu_id`;

SELECT
  (SELECT COUNT(*) FROM `sys_role_menu` WHERE `role_id` IN (2, 3)
     AND `menu_id` IN (3016, 3017, 3018, 3019, 3023)) AS `非超管账号管理权限残留（应为0）`,
  (SELECT COUNT(*) FROM `sys_role_menu` WHERE `role_id` = 1
     AND `menu_id` IN (3016, 3017, 3018, 3019, 3023)) AS `超管账号管理授权数（应为5）`;
