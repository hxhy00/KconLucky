-- =============================================================
-- KCon 2026 · 增量迁移 v7
--
-- 用途：手工修改的「权益类型」不被自动逻辑冲掉
--   - kcon_attendee 新增 ticket_type_manual 列
--       0 = 权益类型来自渠道来源映射（可被重算/导入覆盖）
--       1 = 已被人工改过，重算与导入均跳过该行权益
--   - 存量数据全部按 0 处理：历史上没有区分，无法反推谁被手工改过，
--     一律维持「以映射为准」的现有行为，不做任何猜测性回填。
--
-- 幂等：可重复执行；列已存在时跳过。
-- 执行：docker compose exec -T mysql mysql -uroot -p<pwd> kcon_lucky < sql/kcon_migrate_v7.sql
-- =============================================================

USE `kcon_lucky`;

-- -------------------------------------------------------------
-- 1. 新增标记列（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断）
-- -------------------------------------------------------------
SET @col_existed = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'kcon_attendee'
     AND COLUMN_NAME  = 'ticket_type_manual'
);

SET @ddl = IF(@col_existed = 0,
  'ALTER TABLE `kcon_attendee` ADD COLUMN `ticket_type_manual` TINYINT NOT NULL DEFAULT 0 COMMENT ''权益类型是否人工改过：1=是，映射重算与导入均不覆盖该行权益'' AFTER `ticket_type`',
  'SELECT ''ticket_type_manual 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. 存量数据统一按「非手工」处理
--    只对 NULL 兜底，不覆盖已有的 1（避免重复执行把人工标记抹掉）
-- -------------------------------------------------------------
UPDATE `kcon_attendee`
   SET `ticket_type_manual` = 0
 WHERE `ticket_type_manual` IS NULL;

SELECT
  (SELECT COUNT(*) FROM `kcon_attendee`) AS `名单总数`,
  (SELECT COUNT(*) FROM `kcon_attendee` WHERE `ticket_type_manual` = 1) AS `手工权益人数`,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kcon_attendee'
      AND COLUMN_NAME = 'ticket_type_manual') AS `标记列存在`;
