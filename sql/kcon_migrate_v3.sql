-- =============================================================
-- KCon 签到抽奖系统 · 增量迁移 v3
--
-- 用途：spec 006 补充口径（2026-10-16 用户确认）——名单导入新增第 5 必填列
--       「是否参与抽奖」，落库为 kcon_attendee.draw_eligible。
--       已有的库需要手动执行本脚本（可重复执行，已存在的列会跳过）。
--
-- 执行：docker compose exec -T mysql mysql -uroot -p<pwd> kcon_lucky < sql/kcon_migrate_v3.sql
-- =============================================================

USE `kcon_lucky`;

-- -------------------------------------------------------------
-- 1. kcon_attendee 补 draw_eligible 列
--    口径确认（q-0/q-1）：
--      - 该列是票务侧的显式事实，不是由票种/渠道推导的；
--      - 导入为第 5 必填列：只有显式填「是」才具备基础资格，
--        留空或无法识别一律按「否」入库（fail-safe：漏填只会失去资格，
--        不会把该被排除的人放错进池）。
--      - 池计算参数优先（q-2）：渠道/票种黑名单走系统参数实时判定，
--        本列作为基础资格参与判定（详见后端 DrawEligibility）。
-- -------------------------------------------------------------
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = 'kcon_lucky' AND TABLE_NAME = 'kcon_attendee' AND COLUMN_NAME = 'draw_eligible'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE `kcon_attendee` ADD COLUMN `draw_eligible` tinyint NOT NULL DEFAULT 0 COMMENT ''是否参与抽奖：1=是 0=否（导入第 5 必填列，留空/无法识别按「否」入库）'' AFTER `ticket_type`',
  'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 2. 回填存量名单（按 spec 006 §2.2 回退推导口径）
--    存量行没有显式值，若全部按 0 处理，池子会直接清零，会前无法抽奖。
--    这里按「渠道 ∉ (G,V) 且 票种不含免费关键字」回填 1，
--    与 006 spec 的黑名单判定口径一致（不认识的票种名默认视为付费）。
--    正式开场前仍应以重新导入的名单（含显式「是否参与抽奖」列）为准。
-- -------------------------------------------------------------
UPDATE `kcon_attendee`
   SET `draw_eligible` = 1
 WHERE (`channel` IS NULL OR `channel` NOT IN ('G', 'V'))
   AND (`ticket_type` IS NULL OR (
          `ticket_type` NOT LIKE '%赠%'
      AND `ticket_type` NOT LIKE '%嘉宾%'
      AND LOWER(`ticket_type`) NOT LIKE '%vip%'
      AND `ticket_type` NOT LIKE '%媒体%'
      AND `ticket_type` NOT LIKE '%免费%'
      AND `ticket_type` NOT LIKE '%邀请%'));

-- 执行结果自检：列是否存在、回填了多少行
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'kcon_lucky' AND TABLE_NAME = 'kcon_attendee' AND COLUMN_NAME = 'draw_eligible')
    AS `draw_eligible 列已存在(1=是)`,
  (SELECT COUNT(*) FROM `kcon_attendee` WHERE `draw_eligible` = 1) AS `参与抽奖人数`,
  (SELECT COUNT(*) FROM `kcon_attendee` WHERE `draw_eligible` = 0) AS `不参与抽奖人数`;

SELECT '迁移 v3 执行完成' AS result;
