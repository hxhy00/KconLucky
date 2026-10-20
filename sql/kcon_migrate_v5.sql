-- =============================================================
-- KCon 2026 · 增量迁移 v5
--
-- 用途：渠道来源映射（spec 009）
--   - 字典类型 kcon_channel_mapping
--       dict_label = 渠道来源
--       dict_value = 权益类型
--       remark     = 是 / 否（参与抽奖）
--   - 设置 → 渠道来源映射菜单（复用 RuoYi 字典管理）
--
-- 幂等：已存在的字典项/菜单会跳过，不覆盖用户修改。
-- 执行：docker compose exec -T mysql mysql -uroot -p<pwd> kcon_lucky < sql/kcon_migrate_v5.sql
-- =============================================================

USE `kcon_lucky`;

-- -------------------------------------------------------------
-- 1. 字典类型
-- -------------------------------------------------------------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT 'KCon 渠道来源映射', 'kcon_channel_mapping', '0', 'admin', NOW(), '渠道来源 → 权益类型 / 参与抽奖'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'kcon_channel_mapping');

-- -------------------------------------------------------------
-- 2. 字典数据（只补缺失项，不改用户已编辑的值）
-- -------------------------------------------------------------
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 1, '重要嘉宾赠票', '含自助午餐、伴手礼', 'kcon_channel_mapping', NULL, NULL, 'N', '0', 'admin', NOW(), '否'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'kcon_channel_mapping' AND `dict_label` = '重要嘉宾赠票');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 2, '其他（赠票）', '无', 'kcon_channel_mapping', NULL, NULL, 'N', '0', 'admin', NOW(), '否'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'kcon_channel_mapping' AND `dict_label` = '其他（赠票）');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 3, 'TGO会员（会员权益）', '含自助午餐', 'kcon_channel_mapping', NULL, NULL, 'N', '0', 'admin', NOW(), '是'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'kcon_channel_mapping' AND `dict_label` = 'TGO会员（会员权益）');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 4, '嘶吼平台卖票', '含自助午餐、帽衫、伴手礼', 'kcon_channel_mapping', NULL, NULL, 'N', '0', 'admin', NOW(), '是'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'kcon_channel_mapping' AND `dict_label` = '嘶吼平台卖票');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 5, 'TGO卖票', '含自助午餐、帽衫、伴手礼', 'kcon_channel_mapping', NULL, NULL, 'N', '0', 'admin', NOW(), '是'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'kcon_channel_mapping' AND `dict_label` = 'TGO卖票');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 6, '海凤卖票', '含自助午餐、帽衫、伴手礼', 'kcon_channel_mapping', NULL, NULL, 'N', '0', 'admin', NOW(), '是'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'kcon_channel_mapping' AND `dict_label` = '海凤卖票');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `css_class`, `list_class`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
SELECT 7, 'IT资讯平台卖票', '含自助午餐、帽衫、伴手礼', 'kcon_channel_mapping', NULL, NULL, 'N', '0', 'admin', NOW(), '是'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_data` WHERE `dict_type` = 'kcon_channel_mapping' AND `dict_label` = 'IT资讯平台卖票');

-- -------------------------------------------------------------
-- 3. 设置 → 渠道来源映射（复用字典管理页面，仅超管可见）
-- -------------------------------------------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 3009, '渠道来源映射', 3200, 3, 'channel-mapping', 'system/dict/index', '', '', 1, 0, 'C', '0', '0', 'system:dict:list', 'dict', 'admin', NOW(), '渠道来源 → 权益类型 / 参与抽奖'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 3009);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 3009);

SELECT
  (SELECT COUNT(*) FROM `sys_dict_data` WHERE `dict_type` = 'kcon_channel_mapping') AS `映射条数`,
  (SELECT COUNT(*) FROM `sys_menu` WHERE `menu_id` = 3009) AS `菜单条数`;
