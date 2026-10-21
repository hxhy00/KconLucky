-- =============================================================
-- KCon 2026 签到 + 抽奖系统 · 建库脚本
-- 版本：v1.0
-- 日期：2026-10-12
-- 数据库：MySQL 8.0
-- 说明：可重复执行（幂等）。首次启动由 docker-entrypoint-initdb.d 自动执行；
--       手动执行：mysql -h127.0.0.1 -P3307 -uroot -proot123 < sql/kcon_lucky.sql
-- =============================================================

CREATE DATABASE IF NOT EXISTS `kcon_lucky`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `kcon_lucky`;

-- -------------------------------------------------------------
-- 3.1 kcon_attendee 参会人名单
--     导入必填 5 列：姓名 / 手机号 / 渠道标识 / 票种 / 是否参与抽奖（spec 006）
--     选填 4 列：公司 / 邮箱 / 第几次参加 KCon / 职务·年级（可后端补录）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `kcon_attendee`;
CREATE TABLE `kcon_attendee` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`         VARCHAR(64)  NOT NULL                COMMENT '姓名',
  `phone`        VARCHAR(20)  NOT NULL                COMMENT '手机号（业务主键，唯一）',
  `channel`      VARCHAR(64)  DEFAULT NULL            COMMENT '渠道来源：自由文本（旧数据可能为 S/T/C/H/D/G/V 单字母）',
  `ticket_type`  VARCHAR(128) DEFAULT NULL            COMMENT '权益类型：自由文本（旧数据为票种）',
  `ticket_type_manual` TINYINT NOT NULL DEFAULT 0     COMMENT '权益类型是否人工改过：1=是，映射重算与导入均不覆盖该行权益',
  `draw_eligible` TINYINT    NOT NULL DEFAULT 0       COMMENT '是否参与抽奖：1=是 0=否（选填列，留空按渠道来源映射带出；无映射按 0）',
  `company`      VARCHAR(128) DEFAULT NULL            COMMENT '公司',
  `email`        VARCHAR(128) DEFAULT NULL            COMMENT '邮箱',
  `attend_count` INT          DEFAULT NULL            COMMENT '第几次参加 KCon（1=首次，空=未知）',
  `position`     VARCHAR(64)  DEFAULT NULL            COMMENT '职务/年级（自由文本）',
  `phone_tail4`  CHAR(4)      DEFAULT NULL            COMMENT '手机号后四位（冗余，加速查询）',
  `import_batch` VARCHAR(32)  DEFAULT NULL            COMMENT '导入批次号',
  `create_by`    VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  `create_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
  `update_by`    VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  `update_time`  DATETIME     DEFAULT NULL            COMMENT '更新时间',
  `remark`       VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_tail4` (`phone_tail4`),
  KEY `idx_company` (`company`),
  KEY `idx_channel` (`channel`),
  KEY `idx_ticket_type` (`ticket_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='参会人名单';

-- -------------------------------------------------------------
-- 3.2 kcon_checkin 签到记录（一人一条，端口号不变）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `kcon_checkin`;
CREATE TABLE `kcon_checkin` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `attendee_id`  BIGINT      NOT NULL                COMMENT '参会人ID',
  `phone`        VARCHAR(20) NOT NULL                COMMENT '手机号（唯一，防重复签到）',
  `port_no`      INT         NOT NULL                COMMENT '分配的端口号（1-65535）',
  `checkin_date` DATE        NOT NULL                COMMENT '签到日期',
  `checkin_time` DATETIME    NOT NULL                COMMENT '签到时间',
  `create_time`  DATETIME    DEFAULT NULL            COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_port` (`port_no`),
  KEY `idx_date` (`checkin_date`),
  KEY `idx_date_time` (`checkin_date`, `checkin_time`),
  KEY `idx_attendee` (`attendee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='签到记录';

-- -------------------------------------------------------------
-- 3.3 kcon_port_pool 端口号池（1-65535 预生成，不放回）
--     分配时 status 置 1，天然不放回
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `kcon_port_pool`;
CREATE TABLE `kcon_port_pool` (
  `port_no`   INT         NOT NULL              COMMENT '端口号 1-65535',
  `status`    TINYINT     NOT NULL DEFAULT 0    COMMENT '0=空闲 1=已分配',
  `phone`     VARCHAR(20) DEFAULT NULL          COMMENT '分配给的手机号',
  `used_time` DATETIME    DEFAULT NULL          COMMENT '分配时间',
  PRIMARY KEY (`port_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='端口号池';

-- -------------------------------------------------------------
-- 3.4 kcon_session 抽奖场次
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `kcon_session`;
CREATE TABLE `kcon_session` (
  `id`     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`   VARCHAR(16) NOT NULL                COMMENT '场次编码 AM/PM',
  `name`   VARCHAR(32) DEFAULT NULL            COMMENT '场次名',
  `status` TINYINT     DEFAULT 0               COMMENT '0=未开始 1=进行中 2=已结束',
  `sort`   INT         DEFAULT 0               COMMENT '排序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='抽奖场次';

INSERT INTO `kcon_session` (`code`, `name`, `status`, `sort`) VALUES
  ('AM', '上午场（极客装备轮）', 0, 1),
  ('PM', '下午场（压轴大奖轮）', 0, 2);

-- -------------------------------------------------------------
-- 3.5 kcon_prize 奖品档位
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `kcon_prize`;
CREATE TABLE `kcon_prize` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_code` VARCHAR(16)  NOT NULL                COMMENT '场次 AM/PM',
  `tier_name`    VARCHAR(32)  NOT NULL                COMMENT '特等奖/一等奖/二等奖/三等奖',
  `quantity`     INT          NOT NULL DEFAULT 1      COMMENT '数量',
  `drawn_count`  INT          NOT NULL DEFAULT 0      COMMENT '已抽出数量',
  `draw_order`   INT          DEFAULT 0               COMMENT '抽取顺序（小的先抽）',
  `remark`       VARCHAR(255) DEFAULT NULL            COMMENT '备注',
  `create_by`    VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  `create_time`  DATETIME     DEFAULT NULL            COMMENT '创建时间',
  `update_by`    VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  `update_time`  DATETIME     DEFAULT NULL            COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='奖品档位';

INSERT INTO `kcon_prize` (`session_code`, `tier_name`, `quantity`, `drawn_count`, `draw_order`, `remark`, `create_time`) VALUES
  ('AM', '三等奖', 3, 0, 1, '先抽三等', NOW()),
  ('AM', '二等奖', 2, 0, 2, NULL,      NOW()),
  ('AM', '一等奖', 1, 0, 3, '最后抽一等', NOW()),
  ('PM', '二等奖', 2, 0, 1, '先抽二等', NOW()),
  ('PM', '一等奖', 1, 0, 2, NULL,      NOW()),
  ('PM', '特等奖', 1, 0, 3, '最后抽特等', NOW());

-- -------------------------------------------------------------
-- 3.6 kcon_channel_mapping 渠道来源映射
--   一条记录直接表达：渠道来源 / 权益类型 / 是否参与抽奖
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `kcon_channel_mapping`;
CREATE TABLE `kcon_channel_mapping` (
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

INSERT INTO `kcon_channel_mapping` (`channel_source`, `benefit_type`, `draw_eligible`, `sort`, `create_time`) VALUES
  ('重要嘉宾赠票',         '含自助午餐、伴手礼',          0, 1, NOW()),
  ('其他（赠票）',         '无',                          0, 2, NOW()),
  ('TGO会员（会员权益）',  '含自助午餐',                  1, 3, NOW()),
  ('嘶吼平台卖票',         '含自助午餐、帽衫、伴手礼',    1, 4, NOW()),
  ('TGO卖票',              '含自助午餐、帽衫、伴手礼',    1, 5, NOW()),
  ('海凤卖票',             '含自助午餐、帽衫、伴手礼',    1, 6, NOW()),
  ('IT资讯平台卖票',       '含自助午餐、帽衫、伴手礼',    1, 7, NOW());

-- -------------------------------------------------------------
-- 3.7 kcon_winner 中奖记录
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `kcon_winner`;
CREATE TABLE `kcon_winner` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `port_no`      INT          NOT NULL                COMMENT '中奖端口号',
  `attendee_id`  BIGINT       DEFAULT NULL            COMMENT '参会人ID',
  `name`         VARCHAR(64)  DEFAULT NULL            COMMENT '姓名（冗余）',
  `company`      VARCHAR(128) DEFAULT NULL            COMMENT '公司（冗余）',
  `position`     VARCHAR(64)  DEFAULT NULL            COMMENT '职务/年级（冗余，大屏展示）',
  `phone`        VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
  `email`        VARCHAR(128) DEFAULT NULL            COMMENT '邮箱（冗余，来自名单）',
  `session_code` VARCHAR(16)  NOT NULL                COMMENT '场次 AM/PM',
  `prize_id`     BIGINT       DEFAULT NULL            COMMENT '奖品档位ID',
  `tier_name`    VARCHAR(32)  DEFAULT NULL            COMMENT '奖项名（冗余）',
  `status`       TINYINT      NOT NULL DEFAULT 0      COMMENT '0=已中奖 1=已领取 2=已作废',
  `win_time`     DATETIME     NOT NULL                COMMENT '中奖时间',
  `receive_time` DATETIME     DEFAULT NULL            COMMENT '领取时间',
  `void_time`    DATETIME     DEFAULT NULL            COMMENT '作废时间',
  `operator`     VARCHAR(64)  DEFAULT NULL            COMMENT '操作人',
  PRIMARY KEY (`id`),
  KEY `idx_port` (`port_no`),
  KEY `idx_session` (`session_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='中奖记录';

-- -------------------------------------------------------------
-- 3.8 kcon_verify_log 兑奖核验日志
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `kcon_verify_log`;
CREATE TABLE `kcon_verify_log` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `port_no`     INT         DEFAULT NULL            COMMENT '被查询的端口号',
  `input_phone` VARCHAR(20) DEFAULT NULL            COMMENT '输入的手机号（建议掩码存储）',
  `result`      VARCHAR(32) DEFAULT NULL            COMMENT 'VALID/NOT_ISSUED/PHONE_MISMATCH/RECEIVED',
  `operator`    VARCHAR(64) DEFAULT NULL            COMMENT '操作人',
  `create_time` DATETIME    DEFAULT NULL            COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_port` (`port_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='兑奖核验日志';

-- -------------------------------------------------------------
-- 端口号池灌数：1-65535（幂等：已存在则跳过）
-- -------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_fill_port_pool`;
DELIMITER $$
CREATE PROCEDURE `sp_fill_port_pool`()
BEGIN
  DECLARE i INT DEFAULT 1;
  IF (SELECT COUNT(*) FROM `kcon_port_pool`) = 0 THEN
    WHILE i <= 65535 DO
      INSERT INTO `kcon_port_pool` (`port_no`, `status`) VALUES (i, 0);
      SET i = i + 1;
    END WHILE;
  END IF;
END$$
DELIMITER ;

CALL `sp_fill_port_pool`();
DROP PROCEDURE IF EXISTS `sp_fill_port_pool`;

-- -------------------------------------------------------------
-- 自检
-- -------------------------------------------------------------
SELECT 'kcon_port_pool 行数' AS `检查项`, COUNT(*) AS `值` FROM `kcon_port_pool`
UNION ALL SELECT 'kcon_session 行数', COUNT(*) FROM `kcon_session`
UNION ALL SELECT 'kcon_prize 行数',   COUNT(*) FROM `kcon_prize`
UNION ALL SELECT 'kcon_channel_mapping 行数', COUNT(*) FROM `kcon_channel_mapping`;
