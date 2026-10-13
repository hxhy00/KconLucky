-- =============================================================
-- KCon 2026 签到 + 抽奖系统 · 演示/演练数据脚本
-- 版本：v1.0
-- 日期：2026-10-12
-- 说明：仅用于开发联调与现场演练，**正式开场前请用 scripts/reset.sh 清场**。
--       执行：mysql -h127.0.0.1 -P3307 -uroot -proot123 kcon_lucky < sql/kcon_demo_data.sql
--       表头口径：姓名 / 手机号 / 渠道标识 / 票种（公司等选填字段留空）
-- =============================================================

USE `kcon_lucky`;

INSERT INTO `kcon_attendee`
  (`name`, `phone`, `channel`, `ticket_type`, `phone_tail4`, `import_batch`, `create_time`)
VALUES
  ('张三',   '13800000001', 'S', '散客票',     '0001', 'DEMO', NOW()),
  ('李四',   '13800000002', 'T', '合作渠道票', '0002', 'DEMO', NOW()),
  ('王五',   '13800000003', 'C', '合作渠道票', '0003', 'DEMO', NOW()),
  ('赵六',   '13800000004', 'H', '早鸟票',     '0004', 'DEMO', NOW()),
  ('钱七',   '13800000005', 'D', '对公票',     '0005', 'DEMO', NOW()),
  ('孙八',   '13800000006', 'G', '赠票',       '0006', 'DEMO', NOW()),
  ('周九',   '13800000007', 'V', '嘉宾票',     '0007', 'DEMO', NOW()),
  ('吴十',   '13800000008', 'S', '散客票',     '0008', 'DEMO', NOW()),
  ('郑一',   '13800000009', 'H', '标准票',     '0009', 'DEMO', NOW()),
  ('王二',   '13800000010', 'T', '合作渠道票', '0010', 'DEMO', NOW()),
  ('测试甲', '13911112222', 'S', '散客票',     '2222', 'DEMO', NOW()),
  ('测试乙', '13922222222', 'H', '标准票',     '2222', 'DEMO', NOW())
ON DUPLICATE KEY UPDATE
  `name`       = VALUES(`name`),
  `channel`    = VALUES(`channel`),
  `ticket_type`= VALUES(`ticket_type`),
  `update_time`= NOW();

SELECT CONCAT('演示名单导入完成，共 ', COUNT(*), ' 人') AS `结果` FROM `kcon_attendee`;
