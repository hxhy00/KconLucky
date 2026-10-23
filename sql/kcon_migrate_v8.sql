-- =============================================================
-- KCon 2026 · 增量迁移 v8
--
-- 用途：权限分工 —— 现场工作人员与超级管理员分离
--
--   1. 新增角色「现场工作人员」role_key = kcon_staff
--      （不硬编码 role_id：sys_role.role_id 自增，且 role_key 没有唯一约束，
--        写死 3 在非标准库上可能绑到别人的角色上；一律按 role_key 解析）
--   2. 新增账号 staff（显示名：现场工作人员），仅绑定 kcon_staff，
--      **默认停用**（status='1'）：账号口令以 BCrypt 落在脚本里，属公开已知值，
--      启用前必须由超管在「设置 → 用户管理 → 重置密码」改掉并手动启用。
--      若已存在同名账号则不动它的状态与密码。
--   3. 按「日常能用」授权，明确收回以下高危权限：
--        kcon:attendee:remove  删名单
--        kcon:attendee:import  名单导入（含 replace 模式可整表替换，等效删名单）
--        kcon:winner:void      作废中奖
--        kcon:prize:*          奖品增删改
--        kcon:data:reset       数据清场（含整个「数据管理」菜单）
--        kcon:channel:*        渠道来源映射增删改（含菜单）
--        kcon:staff:*          用户管理（账号只能由超管维护）
--      数据迁移走 @ss.hasRole('admin')，「数据管理」菜单不给 staff，
--      所以工作人员连入口都看不到。
--
--   4. 清场二次验证：后端 ExportController#reset 已要求输入当前登录人密码，
--      本脚本不涉及；staff 即使猜到接口地址，@PreAuthorize 也会先拦下。
--
-- 幂等：可重复执行（NOT EXISTS 防重插 / 按 role_key 解析后重建授权）。
-- 执行：docker compose exec -T mysql mysql -uroot -p<pwd> kcon_lucky < sql/kcon_migrate_v8.sql
-- =============================================================

USE `kcon_lucky`;

-- -------------------------------------------------------------
-- 1. 角色：现场工作人员
--    role_sort 排在 common(2) 之后；data_scope=1 表示全部数据，
--    本项目不使用部门数据权限，该字段只是占位。
-- -------------------------------------------------------------
INSERT INTO `sys_role`
  (`role_name`, `role_key`, `role_sort`, `data_scope`, `menu_check_strictly`, `dept_check_strictly`, `status`, `del_flag`, `create_by`, `create_time`, `remark`)
SELECT '现场工作人员', 'kcon_staff', 3, '1', 1, 1, '0', '0', 'admin', NOW(),
       '现场签到/抽奖/核验操作员：可操作日常业务，不可清场、不可作废中奖、不可改映射与奖品'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_key` = 'kcon_staff' AND `del_flag` = '0');

-- 解析真实角色 ID，后续全部使用该变量（若为空，下面的插入会因 role_id 非空而失败并暴露问题）
SET @staff_role_id := (
  SELECT `role_id` FROM `sys_role`
   WHERE `role_key` = 'kcon_staff' AND `del_flag` = '0'
   ORDER BY `role_id` LIMIT 1
);

-- -------------------------------------------------------------
-- 2. 账号：staff（默认停用，需超管重置密码后启用）
--    不硬编码 user_id：目标库的自增段可能已被手工建号占用。
--    按 user_name 幂等：不存在才插入，存在则沿用原账号（不改密码/状态）。
-- -------------------------------------------------------------
INSERT INTO `sys_user`
  (`dept_id`, `user_name`, `nick_name`, `user_type`, `email`, `phonenumber`, `sex`, `avatar`, `password`, `status`, `del_flag`, `login_ip`, `login_date`, `create_by`, `create_time`, `remark`)
SELECT NULL, 'staff', '现场工作人员', '00', '', '', '2', '',
  '$2a$10$h30sQpojfdnRzS82ER//2eFEzdBj2SudcZv/Aj0lDVvWsd1ME2GiC',
  '1', '0', '', NULL, 'admin', NOW(),
  '现场操作账号，默认停用；启用前必须由超管重置密码'
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `user_name` = 'staff' AND `del_flag` = '0');

-- 角色绑定按 user_name + 解析出的 role_id 关联
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.`user_id`, @staff_role_id FROM `sys_user` u
 WHERE u.`user_name` = 'staff' AND u.`del_flag` = '0';

-- 确保只绑定 kcon_staff：清掉该账号被误加的其它角色（按 user_name 定位，不会伤及他人）
DELETE ur FROM `sys_user_role` ur
  JOIN `sys_user` u ON u.`user_id` = ur.`user_id`
 WHERE u.`user_name` = 'staff'
   AND ur.`role_id` <> @staff_role_id;

-- -------------------------------------------------------------
-- 3. 授权：工作人员 = 全部业务菜单 - 高危项
--
--    sys_menu 中 3000~3999 是 KCon 业务菜单。
--    先整体授予，再逐项收回 —— 这样将来新增普通业务菜单会自动给到工作人员，
--    只需在下方「收回清单」里补新高危项即可。
-- -------------------------------------------------------------
DELETE FROM `sys_role_menu` WHERE `role_id` = @staff_role_id;

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT @staff_role_id, `menu_id` FROM `sys_menu`
 WHERE `menu_id` BETWEEN 3000 AND 3999
   AND `status` = '0';

-- 收回清单（含菜单本身，否则侧边栏有入口点进去 403）
--   3006 数据管理（清场 + 迁移）
--   3009 渠道来源映射 + 3060/3061/3062 映射增删改
--   3003 奖品设置 + 3020/3021/3022 奖品增删改
--   3013 名单删除
--   3014 名单导入（replace 模式可整表替换，等效删名单）
--   3016~3019/3023 用户管理（账号只能由超管维护）
--   3040 中奖作废
DELETE FROM `sys_role_menu`
 WHERE `role_id` = @staff_role_id
   AND `menu_id` IN (
        3006,                                   -- 数据管理（清场/迁移）
        3003, 3020, 3021, 3022,                 -- 奖品设置及其按钮
        3009, 3060, 3061, 3062,                 -- 渠道来源映射及其按钮
        3013,                                   -- 名单删除
        3014,                                   -- 名单导入（replace 模式可整表替换，等效删名单）
        3016, 3017, 3018, 3019, 3023,           -- 用户管理（工作人员不能管理账号）
        3040                                    -- 中奖作废
   );

-- 设置目录(3200)下若已无可见子菜单，一并隐藏：工作人员登录后不该看到空的「设置」目录。
DELETE FROM `sys_role_menu`
 WHERE `role_id` = @staff_role_id
   AND `menu_id` = 3200
   AND NOT EXISTS (
        SELECT 1 FROM (
          SELECT rm2.menu_id
            FROM `sys_role_menu` rm2
            JOIN `sys_menu` m2 ON m2.menu_id = rm2.menu_id
           WHERE rm2.role_id = @staff_role_id AND m2.parent_id = 3200
        ) AS child
   );

-- -------------------------------------------------------------
-- 4. 兜底：把 common(2) 也按同样口径收紧
--    common 此前仍持有「删名单」「作废中奖」「奖品增删改」，
--    与「普通角色」的定位不符，且账号 ry 现为停用状态，
--    一并收紧可避免日后启用时留下口子。
-- -------------------------------------------------------------
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 2
   AND `menu_id` IN (3013, 3040, 3020, 3021, 3022,
                     3016, 3017, 3018, 3019, 3023);

-- -------------------------------------------------------------
-- 5. 结果核对
-- -------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM `sys_role_menu` WHERE `role_id` = @staff_role_id) AS `staff授权菜单数`,
  (SELECT COUNT(*) FROM `sys_role_menu` rm
     JOIN `sys_menu` m ON m.menu_id = rm.menu_id
    WHERE rm.role_id = @staff_role_id AND m.perms LIKE 'kcon:%' AND m.perms <> '') AS `staff业务权限数`,
  (SELECT COUNT(*) FROM `sys_user_role` ur
     JOIN `sys_user` u ON u.user_id = ur.user_id
    WHERE u.user_name = 'staff' AND u.del_flag = '0') AS `staff角色绑定数`,
  (SELECT COUNT(*) FROM `sys_role_menu`
    WHERE `role_id` = @staff_role_id
      AND `menu_id` IN (3006, 3013, 3014, 3016, 3017, 3018, 3019, 3023, 3040)) AS `高危项残留数（应为0）`;
