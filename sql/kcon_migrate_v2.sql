-- =============================================================
-- KCon 签到抽奖系统 · 增量迁移 v2
--
-- 用途：为已部署的库补齐后续新增的字段与索引。
--       docker-entrypoint-initdb.d 只在 MySQL 首次初始化时执行，
--       已有的库需要手动执行本脚本（可重复执行，已存在的对象会跳过）。
--
-- 执行：docker compose exec -T mysql mysql -uroot -p<pwd> kcon_lucky < sql/kcon_migrate_v2.sql
-- =============================================================

USE `kcon_lucky`;

-- -------------------------------------------------------------
-- 1. kcon_winner 补 email 列
--    需求 FR-13 / FR-21 / FR-23：名单、中奖记录、核验结果都要能看到邮箱，
--    兑奖时用于二次核对身份。原表设计遗漏，导致会前补录的邮箱在兑奖环节用不上。
-- -------------------------------------------------------------
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = 'kcon_lucky' AND TABLE_NAME = 'kcon_winner' AND COLUMN_NAME = 'email'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE `kcon_winner` ADD COLUMN `email` varchar(128) DEFAULT NULL COMMENT ''邮箱（冗余，来自名单）'' AFTER `phone`',
  'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. kcon_verify_log 补查询用索引
--    兑奖日志此前没有任何查询入口，补索引后按时间倒序查最近记录才够快。
-- -------------------------------------------------------------
SET @exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = 'kcon_lucky' AND TABLE_NAME = 'kcon_verify_log' AND INDEX_NAME = 'idx_create_time'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE `kcon_verify_log` ADD INDEX `idx_create_time` (`create_time`)',
  'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 3. 回填历史中奖记录的邮箱（名单表里还在的话）
-- -------------------------------------------------------------
UPDATE `kcon_winner` w
  JOIN `kcon_attendee` a ON a.`id` = w.`attendee_id`
   SET w.`email` = a.`email`
 WHERE (w.`email` IS NULL OR w.`email` = '')
   AND a.`email` IS NOT NULL AND a.`email` <> '';

-- -------------------------------------------------------------
-- 4. kcon_checkin 补「当天」查询索引
--    需求 FR-39：签到记录按日期判断。当天口径统一后，
--    抽奖池与看板都要按 checkin_date 过滤，需要索引支撑。
-- -------------------------------------------------------------
SET @exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = 'kcon_lucky' AND TABLE_NAME = 'kcon_checkin' AND INDEX_NAME = 'idx_date_time'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE `kcon_checkin` ADD INDEX `idx_date_time` (`checkin_date`, `checkin_time`)',
  'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT '迁移 v2 执行完成' AS result;