-- =============================================================
-- KCon 2026 · 名单导入模板 8 列调整（v4）
--
-- 背景：新票务导出模板为
--   姓名 / 手机号 / 公司 / 职位 / 邮箱 / 渠道来源 / 权益类型 / 参与抽奖
--
-- 语义变化：
--   - channel     由「渠道标识（单字母）」改为「渠道来源（自由文本）」
--   - ticket_type 由「票种」改为「权益类型（自由文本）」
--
-- 本次只放宽长度，不改列名；旧数据保持不变。
-- MODIFY COLUMN 幂等，可重复执行。
-- =============================================================

ALTER TABLE `kcon_attendee`
  MODIFY COLUMN `channel` VARCHAR(64) DEFAULT NULL
    COMMENT '渠道来源：自由文本（旧数据可能为 S/T/C/H/D/G/V 单字母）',
  MODIFY COLUMN `ticket_type` VARCHAR(128) DEFAULT NULL
    COMMENT '权益类型：自由文本（旧数据为票种）';

SELECT
  (SELECT CHARACTER_MAXIMUM_LENGTH
     FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'kcon_lucky'
      AND TABLE_NAME = 'kcon_attendee'
      AND COLUMN_NAME = 'channel') AS `channel 长度`,
  (SELECT CHARACTER_MAXIMUM_LENGTH
     FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'kcon_lucky'
      AND TABLE_NAME = 'kcon_attendee'
      AND COLUMN_NAME = 'ticket_type') AS `ticket_type 长度`;
