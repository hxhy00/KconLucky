-- =============================================================
-- KCon 2026 · 增量迁移 v8
--
-- 用途：权限分工 —— 现场工作人员与超级管理员分离
--
--   1. 新增角色「现场工作人员」role_key = kcon_staff（role_id = 3）
--   2. 新增账号 staff（显示名：现场工作人员），仅绑定 kcon_staff
--   3. 按「日常能用」授权，明确收回以下高危权限：
--        kcon:attendee:remove  删名单
--        kcon:winner:void      作废中奖
--        kcon:prize:*          奖品增删改
--        kcon:data:reset       数据清场（含整个「数据管理」菜单）
--        kcon:channel:*        渠道来源映射增删改（含菜单）
--      数据迁移走 @ss.hasRole('admin')，「数据管理」菜单不给 staff，
--      所以工作人员连入口都看不到。
--
--   4. 清场二次验证：后端 ExportController#reset 已要求输入当前登录人密码，
--      本脚本不涉及；staff 即使猜到接口地址，@PreAuthorize 也会先拦下。
--
-- 初始密码：Kcon2026Staff.
--   ★ 上线后请立刻在「系统管理 → 用户管理」里改成自己的密码 ★
--   散列与 kcon 库现有账号一致（BCrypt）。
--
-- 幂等：可重复执行（INSERT IGNORE / DELETE 后重建授权）。
-- 执行：docker compose exec -T mysql mysql -uroot -p<pwd> kcon_lucky < sql/kcon_migrate_v8.sql
-- =============================================================

USE `kcon_lucky`;

-- -------------------------------------------------------------
-- 1. 角色：现场工作人员
--    role_sort 排在 common(2) 之后；data_scope=1 表示仅本人数据，
--    本项目不使用部门数据权限，该字段只是占位。
-- -------------------------------------------------------------
INSERT IGNORE INTO `sys_role`
  (`role_id`, `role_name`, `role_key`, `role_sort`, `data_scope`, `menu_check_strictly`, `dept_check_strictly`, `status`, `del_flag`, `create_by`, `create_time`, `remark`)
VALUES
  (3, '现场工作人员', 'kcon_staff', 3, '1', 1, 1, '0', '0', 'admin', NOW(),
   '现场签到/抽奖/核验操作员：可操作日常业务，不可清场、不可作废中奖、不可改映射与奖品');

-- -------------------------------------------------------------
-- 2. 账号：staff
--    若已存在同名账号，不覆盖其密码（避免重复执行把用户改过的密码刷回初始值）
-- -------------------------------------------------------------
INSERT IGNORE INTO `sys_user`
  (`user_id`, `dept_id`, `user_name`, `nick_name`, `user_type`, `email`, `phonenumber`, `sex`, `avatar`, `password`, `status`, `del_flag`, `login_ip`, `login_date`, `create_by`, `create_time`, `remark`)
VALUES
  (3, NULL, 'staff', '现场工作人员', '00', '', '',
   '2', '',
   '$2a$10$h30sQpojfdnRzS82ER//2eFEzdBj2SudcZv/Aj0lDVvWsd1ME2GiC',
   '0', '0', '', NULL, 'admin', NOW(),
   '现场操作账号，绑定「现场工作人员」角色');

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`) VALUES (3, 3);

-- 确保只绑定 kcon_staff：若被误加了其它角色，这里清掉（保留 admin 除外）
DELETE FROM `sys_user_role`
 WHERE `user_id` = 3
   AND `role_id` <> 3;

-- -------------------------------------------------------------
-- 3. 授权：工作人员 = 全部业务菜单 - 高危项
--
--    sys_menu 中 3000~3999 是 KCon 业务菜单。
--    先整体授予，再逐项收回 —— 这样将来新增普通业务菜单会自动给到工作人员，
--    只需在下方「收回清单」里补新高危项即可。
-- -------------------------------------------------------------
DELETE FROM `sys_role_menu` WHERE `role_id` = 3;

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 3, `menu_id` FROM `sys_menu`
 WHERE `menu_id` BETWEEN 3000 AND 3999
   AND `status` = '0';

-- 收回清单（含菜单本身，否则侧边栏有入口点进去 403）
--   3006 数据管理（清场 + 迁移）
--   3009 渠道来源映射 + 3060/3061/3062 映射增删改
--   3003 奖品设置 + 3020/3021/3022 奖品增删改
--   3013 名单删除
--   3040 中奖作废
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 3
   AND `menu_id` IN (
        3006,                                   -- 数据管理（清场/迁移）
        3003, 3020, 3021, 3022,                 -- 奖品设置及其按钮
        3009, 3060, 3061, 3062,                 -- 渠道来源映射及其按钮
        3013,                                   -- 名单删除
        3040                                    -- 中奖作废
   );

-- 设置目录(3200)下若已无可见子菜单，一并隐藏：工作人员登录后不该看到空的「设置」目录。
-- 奖品/映射/数据管理收回后，3200 下确实一无所有，故直接收回目录本身。
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 3
   AND `menu_id` = 3200
   AND NOT EXISTS (
        SELECT 1 FROM (
          SELECT rm2.menu_id
            FROM `sys_role_menu` rm2
            JOIN `sys_menu` m2 ON m2.menu_id = rm2.menu_id
           WHERE rm2.role_id = 3 AND m2.parent_id = 3200
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
   AND `menu_id` IN (3013, 3040, 3020, 3021, 3022);

-- -------------------------------------------------------------
-- 5. 结果核对
-- -------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM `sys_role_menu` WHERE `role_id` = 3) AS `staff授权菜单数`,
  (SELECT COUNT(*) FROM `sys_role_menu` rm
     JOIN `sys_menu` m ON m.menu_id = rm.menu_id
    WHERE rm.role_id = 3 AND m.perms LIKE 'kcon:%' AND m.perms <> '') AS `staff业务权限数`,
  (SELECT COUNT(*) FROM `sys_user_role` WHERE `user_id` = 3) AS `staff角色绑定数`,
  (SELECT COUNT(*) FROM `sys_role_menu`
    WHERE `role_id` = 3 AND `menu_id` IN (3006, 3013, 3040)) AS `高危项残留数（应为0）`;
