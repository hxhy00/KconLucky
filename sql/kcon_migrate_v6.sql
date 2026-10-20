-- =============================================================
-- KCon 2026 · 增量迁移 v6
--
-- 用途：渠道来源映射从「字典 + 备注」改为独立表 + 独立单页 CRUD（spec 010）
--   - 建表 kcon_channel_mapping：一条记录直接表达
--       渠道来源 / 权益类型 / 是否参与抽奖
--     不再借用 sys_dict_data 的 remark 承载第三个字段
--   - 把 v5 写入字典的历史数据迁入新表
--   - 删除字典类型与字典数据
--   - 菜单 3009 由「字典管理」改为「渠道来源映射」独立页面
--   - 补齐按钮权限（list/add/edit/remove）
--
-- 幂等：可重复执行；已有映射不会被覆盖。
-- 执行：docker compose exec -T mysql mysql -uroot -p<pwd> kcon_lucky < sql/kcon_migrate_v6.sql
-- =============================================================

USE `kcon_lucky`;

-- -------------------------------------------------------------
-- 1. 映射表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kcon_channel_mapping` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `channel_source` VARCHAR(64)  NOT NULL                COMMENT '渠道来源（与导入表同名，唯一）',
  `benefit_type`   VARCHAR(128) DEFAULT NULL            COMMENT '权益类型，空表示无权益',
  `draw_eligible`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否参与抽奖：1=是 0=否',
  `sort`           INT          DEFAULT 0               COMMENT '排序（小的在前）',
  `create_by`      VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  `create_time`    DATETIME     DEFAULT NULL            COMMENT '创建时间',
  `update_by`      VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  `update_time`    DATETIME     DEFAULT NULL            COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_source` (`channel_source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='渠道来源映射';

-- -------------------------------------------------------------
-- 2. 迁移 v5 字典数据（仅当新表为空时执行，避免覆盖用户已编辑的映射）
--    dict_label = 渠道来源 / dict_value = 权益类型 / remark = 是|否
-- -------------------------------------------------------------
INSERT INTO `kcon_channel_mapping`
  (`channel_source`, `benefit_type`, `draw_eligible`, `sort`, `create_by`, `create_time`)
SELECT d.`dict_label`, NULLIF(d.`dict_value`, ''), IF(d.`remark` = '是', 1, 0), d.`dict_sort`, 'admin', NOW()
  FROM `sys_dict_data` d
 WHERE d.`dict_type` = 'kcon_channel_mapping'
   AND d.`status` = '0'
   AND NOT EXISTS (SELECT 1 FROM `kcon_channel_mapping` m WHERE m.`channel_source` = d.`dict_label`)
 ORDER BY d.`dict_sort` ASC;

-- v5 未执行过时，补一份现场默认映射
-- 用会话变量先取一次行数：若字典迁移已写入数据，则不再补默认值，
-- 避免重复执行时把用户主动删掉的默认映射又插回来。
SET @mapping_count = (SELECT COUNT(*) FROM `kcon_channel_mapping`);

INSERT INTO `kcon_channel_mapping` (`channel_source`, `benefit_type`, `draw_eligible`, `sort`, `create_by`, `create_time`)
SELECT t.`channel_source`, t.`benefit_type`, t.`draw_eligible`, t.`sort`, 'admin', NOW()
  FROM (
    SELECT '重要嘉宾赠票' AS `channel_source`, '含自助午餐、伴手礼' AS `benefit_type`, 0 AS `draw_eligible`, 1 AS `sort`
    UNION ALL SELECT '其他（赠票）', '无', 0, 2
    UNION ALL SELECT 'TGO会员（会员权益）', '含自助午餐', 1, 3
    UNION ALL SELECT '嘶吼平台卖票', '含自助午餐、帽衫、伴手礼', 1, 4
    UNION ALL SELECT 'TGO卖票', '含自助午餐、帽衫、伴手礼', 1, 5
    UNION ALL SELECT '海凤卖票', '含自助午餐、帽衫、伴手礼', 1, 6
    UNION ALL SELECT 'IT资讯平台卖票', '含自助午餐、帽衫、伴手礼', 1, 7
  ) t
 WHERE @mapping_count = 0;

-- -------------------------------------------------------------
-- 3. 删除字典类型与字典数据
-- -------------------------------------------------------------
DELETE FROM `sys_dict_data` WHERE `dict_type` = 'kcon_channel_mapping';
DELETE FROM `sys_dict_type` WHERE `dict_type` = 'kcon_channel_mapping';

-- -------------------------------------------------------------
-- 4. 菜单 3009：改为独立页面
-- -------------------------------------------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 3009, '渠道来源映射', 3200, 3, 'channel-mapping', 'kcon/channel/index', '', '', 1, 0, 'C', '0', '0', 'kcon:channel:list', 'dict', 'admin', NOW(), '渠道来源 → 权益类型 / 参与抽奖'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 3009);

UPDATE `sys_menu`
   SET `menu_name` = '渠道来源映射',
       `parent_id` = 3200,
       `order_num` = 3,
       `path`      = 'channel-mapping',
       `component` = 'kcon/channel/index',
       `perms`     = 'kcon:channel:list',
       `remark`    = '渠道来源 → 权益类型 / 参与抽奖'
 WHERE `menu_id` = 3009;

-- 按钮权限
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 3060, '映射新增', 3009, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:channel:add', '#', 'admin', NOW(), ''
 WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 3060);

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 3061, '映射修改', 3009, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:channel:edit', '#', 'admin', NOW(), ''
 WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 3061);

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 3062, '映射删除', 3009, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:channel:remove', '#', 'admin', NOW(), ''
 WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 3062);

-- -------------------------------------------------------------
-- 5. 授权：映射配置仍只给超管（role_id=1）
-- -------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 3009);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 3060);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 3061);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 3062);

DELETE FROM `sys_role_menu` WHERE `role_id` = 2 AND `menu_id` IN (3009, 3060, 3061, 3062);

SELECT
  (SELECT COUNT(*) FROM `kcon_channel_mapping`) AS `映射条数`,
  (SELECT COUNT(*) FROM `sys_dict_data` WHERE `dict_type` = 'kcon_channel_mapping') AS `残留字典条数`,
  (SELECT COUNT(*) FROM `sys_menu` WHERE `menu_id` BETWEEN 3009 AND 3062) AS `菜单条数`;
