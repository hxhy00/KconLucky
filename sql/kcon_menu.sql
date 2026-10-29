-- =============================================================
-- KCon 签到抽奖系统 · 菜单与权限初始化
-- 依赖：RuoYi 基础表（sys_menu）已存在，默认角色（role_id=2 普通角色）
-- 执行顺序：必须先执行 sql/00-ry-base.sql（RuoYi 基础表，已删除 ry 测试账号）再执行本脚本
-- =============================================================

-- 自保护：手工导入时客户端可能按 latin1 建连，导致中文菜单双重编码入库
SET NAMES utf8mb4;
USE `kcon_lucky`;

-- 清理旧数据（可重复执行）
DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 3000 AND 3999;
DELETE FROM `sys_menu` WHERE `menu_id` BETWEEN 3000 AND 3999;

-- -------------------------------------------------------------
-- 隐藏 RuoYi 原生菜单（现场无需展示：首页/系统管理/系统监控/系统工具/官网/源码/文档）
-- visible='1' 表示隐藏，仅隐藏不删除，便于排障时临时放开
-- 注意：隐藏只是 UI 层面，真正收回权限靠下面的 sys_role_menu 清理
-- -------------------------------------------------------------
UPDATE `sys_menu` SET `visible` = '1'
 WHERE `menu_id` IN ('1', '2', '3', '4', '5');

UPDATE `sys_menu` SET `visible` = '1'
 WHERE `parent_id` IN ('1', '2', '3', '4', '5', '108');

-- -------------------------------------------------------------
-- 收回普通角色（RuoYi 默认 role_id=2）对原生菜单的授权
-- 若只隐藏菜单而不做这一步，隐藏只是心理安慰：
-- 任何持有普通角色的账号仍可直接调接口提权（改角色 / 跑建表 SQL / 删定时任务）
--
-- 本系统只有 KCon 现场功能（菜单 ID 3000-3999），其余原生菜单一律收回。
-- 注意不能只按 perms 前缀删——目录型菜单（系统管理/系统监控/系统工具/若依官网/日志管理）
-- 的 perms 为空，必须按 menu_id 区间一起清掉，否则它们仍会留在该角色的菜单树里。
-- -------------------------------------------------------------
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 2
   AND (`menu_id` < 3000 OR `menu_id` > 3999);

-- -------------------------------------------------------------
-- 菜单结构：6 个一级项，其中「日志」「设置」为目录（menu_type='M'）
--   1 数据总览     — 现场高频：签到率与统计
--   2 参会名单     — 开场前导入名单 · 打印签到表
--   3 抽奖控制台   — 现场高频：场次切换与抽奖操作
--   4 兑奖核验     — 现场高频：端口号+手机号核验发放
--   5 日志（目录） — 结束后复盘
--       5.1 权益发放 / 5.2 中奖日志 / 5.3 奖品发放
--   6 设置（目录） — 一次性/低频
--       6.1 奖品设置 / 6.2 渠道来源 / 6.3 用户管理 / 6.4 数据管理
--
-- 目录型菜单（parent_id=0, type='M'）经后端 buildMenus 处理后：
--   path -> /{path}，子菜单在其下挂载，最终 URL = /{目录path}/{子菜单path}
--   （如 /log/winner、/setting/prize），因此目录 path 用短英文。
--   这里 component 故意留空：本项目 SysMenuServiceImpl#getComponent 对
--   「parent_id=0 且 type='M'」会兜底为 Layout，无需在 DB 里写 'Layout'
--   （写了也照样生效，两条路径等价）。
--
-- 【菜单 ID 固定不变】仅调整 parent_id / order_num / menu_name。
-- 这样 sys_role_menu 中已有的授权关系无需重建，权限点 perms 也保持稳定。
--
-- 【route_name 必须显式指定】
-- 路由名默认由 path 推导（capitalize(path)，只处理首字符），会和 RuoYi 自带路由重名：
--   3006 data  → Data，与 ruoyi-ui 内置的字典页路由 name:'Data' 撞名
--   3100 log   → Log ，与 RuoYi 自带菜单「日志管理」(menu_id 108, path=log) 撞名
-- vue-router 的 name 映射只保留先注册的那一个，重名会让按 name 跳转与
-- 缓存判定变得不可预期，故这两处显式命名。
--
-- 命名约束（两条，缺一不可）：
--   ① 避开全部内置路由名与 RuoYi 自带菜单的推导名；
--   ② 避开现有 8 个页面组件名（KconDashboard / KconAttendee / KconDrawConsole /
--      KconVerify / KconWinner / KconVerifyLog / KconPrize / KconData）。
--
-- 关于 ② 的原因：若将来到「菜单管理」里重新打开页签栏（settings.js 的 tagsView
-- 现为 false，且 TagsView 组件已移除），tagsView 会把**路由名**写入 cachedViews，
-- 而 AppMain 的 <keep-alive :include="cachedViews"> 是按**组件名**匹配的，
-- 两者一相等就等于开启页面缓存，返回时会看到旧数据（日后写了
-- `<keep-alive>` 却发现「数据没重新加载」，根因通常就在这里）。
-- 当前页签栏关闭、cachedViews 不含任何真实组件名，不存在缓存问题，此约束是为将来留的。
--
-- 注意：撞名只在 name 层消除了，path 层 3100 与 108 仍同为 log。
-- 请勿在「菜单管理」界面编辑 3100：SysMenuServiceImpl#checkRouteConfigUnique
-- 的「根目录路由冲突」分支会因 108 同为 path=log 而判重拦截，导致改任何字段都存不进去。
-- （反向不受影响：108 的 route_name 仍是 log，与 3100 的 KconLog 不同，可正常编辑。）
-- 需要调整 3100 时改本文件或走 SQL。
-- -------------------------------------------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  -- 一级菜单（4 个平铺页面）
  (3001, '数据总览',   0, 1, 'dashboard', 'kcon/dashboard/index', '', '', 1, 0, 'C', '0', '0', 'kcon:attendee:list', 'dashboard', 'admin', NOW(), '签到率与统计'),
  (3002, '参会名单',   0, 2, 'attendee',  'kcon/attendee/index',  '', '', 1, 0, 'C', '0', '0', 'kcon:attendee:list', 'user',      'admin', NOW(), '名单管理与导入'),
  (3007, '抽奖控制台', 0, 3, 'draw',      'kcon/draw/index',      '', '', 1, 0, 'C', '0', '0', 'kcon:draw:query',    'guide',     'admin', NOW(), '场次切换与抽奖操作（FR-16/19/20）'),
  (3005, '兑奖核验',   0, 4, 'verify',    'kcon/verify/index',    '', '', 1, 0, 'C', '0', '0', 'kcon:verify:query',  'search',    'admin', NOW(), '端口号+手机号核验发放'),
  -- 一级目录：日志
  (3100, '日志',       0, 5, 'log',       NULL,                   '', 'KconLog',      1, 0, 'M', '0', '0', '',            'log',       'admin', NOW(), '日志目录'),
  -- 一级目录：设置
  (3200, '设置',       0, 6, 'setting',   NULL,                   '', '', 1, 0, 'M', '0', '0', '',                   'system',    'admin', NOW(), '设置目录'),
  -- 日志子菜单（顺序：权益发放 → 中奖日志 → 奖品发放）
  (3008, '权益发放',   3100, 1, 'benefit',    'kcon/benefit/index',    '', 'KconBenefit', 1, 0, 'C', '0', '0', 'kcon:benefit:list', 'list',  'admin', NOW(), '权益发放记录（BENEFIT）'),
  (3004, '中奖日志',   3100, 2, 'winner',     'kcon/winner/index',     '', '', 1, 0, 'C', '0', '0', 'kcon:winner:list',  'money', 'admin', NOW(), '中奖名单与状态'),
  (3026, '奖品发放',   3100, 3, 'prize-grant','kcon/prizegrant/index', '', 'KconPrizeGrant', 1, 0, 'C', '0', '0', 'kcon:prizegrant:list', 'shopping', 'admin', NOW(), '奖品发放记录（PRIZE）'),
  -- 设置子菜单（顺序：奖品设置 → 渠道来源 → 用户管理 → 数据管理）
  (3003, '奖品设置',   3200, 1, 'prize',     'kcon/prize/index',     '', '', 1, 0, 'C', '0', '0', 'kcon:prize:list',   'star',  'admin', NOW(), '场次奖项档位'),
  (3009, '渠道来源',   3200, 2, 'channel-mapping', 'kcon/channel/index', '', '', 1, 0, 'C', '0', '0', 'kcon:channel:list', 'dict', 'admin', NOW(), '渠道来源 → 权益类型 / 参与抽奖'),
  (3016, '用户管理',   3200, 3, 'staff',     'kcon/staff/index',     '', '', 1, 0, 'C', '0', '0', 'kcon:staff:list',   'peoples', 'admin', NOW(), '现场工作人员账号（仅超管）'),
  (3006, '数据管理',   3200, 4, 'data',      'kcon/data/index',      '', 'KconDataManage', 1, 0, 'C', '0', '0', 'kcon:data:reset', 'tool', 'admin', NOW(), '数据清场与全量备份迁移（仅超管）');

-- -------------------------------------------------------------
-- 按钮权限
-- 注意：按钮权限的 parent_id 仍指向「业务页面菜单 ID」，与页面挂在哪一层无关。
-- -------------------------------------------------------------
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
  -- 名单
  (3010, '名单查询', 3002, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:query',  '#', 'admin', NOW(), ''),
  (3011, '名单新增', 3002, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:add',    '#', 'admin', NOW(), ''),
  (3012, '名单修改', 3002, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:edit',   '#', 'admin', NOW(), ''),
  (3013, '名单删除', 3002, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:remove', '#', 'admin', NOW(), ''),
  (3014, '名单导入', 3002, 5, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:import', '#', 'admin', NOW(), ''),
  (3015, '名单导出', 3002, 6, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:attendee:export', '#', 'admin', NOW(), ''),
  (3024, '权益发放', 3002, 7, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:benefit:grant', '#', 'admin', NOW(), '名单页发放按钮'),
  -- 奖品
  (3020, '奖品新增', 3003, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:prize:add',    '#', 'admin', NOW(), ''),
  (3021, '奖品修改', 3003, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:prize:edit',   '#', 'admin', NOW(), ''),
  (3022, '奖品删除', 3003, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:prize:remove', '#', 'admin', NOW(), ''),
  -- 核验
  (3030, '确认发放', 3005, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:verify:receive', '#', 'admin', NOW(), ''),
  -- 中奖
  (3040, '中奖作废', 3004, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:winner:void', '#', 'admin', NOW(), ''),
  -- 抽奖控制台（FR-16/19/20）
  (3050, '场次查询', 3007, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:session:list', '#', 'admin', NOW(), ''),
  (3051, '抽奖操作', 3007, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:draw:operate', '#', 'admin', NOW(), '抽奖/作废/切换场次'),
  -- 渠道来源映射（spec 010）
  (3060, '映射新增', 3009, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:channel:add',    '#', 'admin', NOW(), ''),
  (3061, '映射修改', 3009, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:channel:edit',   '#', 'admin', NOW(), ''),
  (3062, '映射删除', 3009, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:channel:remove', '#', 'admin', NOW(), ''),
  -- 工作人员账号（设置 → 用户管理）
  (3017, '账号新增',   3016, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:staff:add',      '#', 'admin', NOW(), ''),
  (3018, '重置密码',   3016, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:staff:resetPwd', '#', 'admin', NOW(), ''),
  (3019, '启用停用',   3016, 3, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:staff:edit',     '#', 'admin', NOW(), ''),
  (3023, '账号删除',   3016, 4, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:staff:remove',   '#', 'admin', NOW(), ''),
  -- 发放日志（权益 / 奖品两张日志，各自由对应页面导出）
  (3025, '权益发放日志导出', 3008, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:benefit:export',    '#', 'admin', NOW(), ''),
  (3027, '奖品发放日志导出', 3026, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:prizegrant:export', '#', 'admin', NOW(), '');
-- 数据迁移不再单独设按钮权限：后端用 @ss.hasRole('admin') 控制，
-- 前端对应按钮用 v-hasRole，避免留下一个谁也不校验的 kcon:data:migrate 权限点

-- -------------------------------------------------------------
-- 授权给超级管理员（role_id=1）与普通角色（role_id=2）
-- -------------------------------------------------------------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 3000 AND 3999;

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 3000 AND 3999;

-- -------------------------------------------------------------
-- 高危菜单只留给超管（role_id=1），从普通角色收回：
--   数据管理(3006) 内含「数据清场」与「数据迁移」——前者清空名单/签到/中奖/核验日志，
--   后者会用备份整体覆盖数据库。后端分别以 @ss.hasPermi('kcon:data:reset') 与
--   @ss.hasRole('admin') 控制；若仍授权给普通角色，轻则「侧边栏有入口、点进去 403」，
--   重则普通角色真能清场。
-- -------------------------------------------------------------
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 2
   AND `menu_id` = 3006;

-- 渠道来源映射属于低频配置（spec 010），只留给超管
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 2
   AND `menu_id` IN (3009, 3060, 3061, 3062);

-- 普通角色不该持有「删名单」「名单导入」「权益发放」「发放日志导出」「账号管理」：
-- 这些动作会改写现场已产生的数据或涉及发放留痕（导入 replace 模式等效删名单），
-- 账号管理只留给超管。同时收回抽奖/兑奖/奖品配置全部入口：
-- role 2 是历史遗留的「未使用角色」，基线无账号绑定，但保留这些权限码意味着
-- 未来任何人被绑上 role 2 即可抽奖、发放奖品、改奖项配置——不符合最小授权
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 2
   AND `menu_id` IN (3013, 3014, 3024, 3025, 3027, 3040, 3020, 3021, 3022,
                     3016, 3017, 3018, 3019, 3023,
                     3003, 3005, 3007, 3030, 3050, 3051);

-- -------------------------------------------------------------
-- 现场工作人员（方案 B：按岗位三分，role_id 3/4/5）
--
--   role 3 kcon_checkin_staff 签到台：总览 + 名单（查/增/改/导出/权益发放）+ 日志（看）
--   role 4 kcon_draw_staff    抽奖台：总览 + 名单（只读）+ 抽奖控制台（抽奖/切场次）+ 日志（看）
--   role 5 kcon_verify_staff  兑奖台：总览 + 名单（只读）+ 兑奖核验（含确认发放）
--                                   + 日志（看，且可导出奖品发放）
--
-- 日志查看不做岗位区分：权益发放 / 中奖日志 / 奖品发放三个页面三岗位都能看；
-- 导出按钮不跟随：只给兑奖台（奖品发放），其余岗位只查不导。
--
-- 关键互斥（岗位天然隔离）：
--   抽奖台无「作废中奖」（作废只留给超管），杜绝自己抽自己撤；
--   兑奖台无权益发放动作（权益发放与奖品发放分离）；
--   三个岗位都无删名单/导入/设置类权限。
-- 一人多岗位：RuoYi 支持一账号绑多角色，换班叠加绑定即可。
--
-- 角色、授权与占位账号均由本脚本创建（新装基线，不再有增量迁移脚本）。
-- 注意：这里写死 role_id 3/4/5 只适用于**新装基线**（00-ry-base 只创建 1/2 两个角色，
-- 3/4/5 必然空闲）。
-- -------------------------------------------------------------
INSERT IGNORE INTO `sys_role`
  (`role_id`, `role_name`, `role_key`, `role_sort`, `data_scope`, `menu_check_strictly`, `dept_check_strictly`, `status`, `del_flag`, `create_by`, `create_time`, `remark`)
VALUES
  (3, '签到台', 'kcon_checkin_staff', 3, '1', 1, 1, '0', '0', 'admin', NOW(),
   '签到台：名单查询/补录/权益发放；日志只查；不可删名单、不可导入、不可抽奖兑奖'),
  (4, '抽奖台', 'kcon_draw_staff', 4, '1', 1, 1, '0', '0', 'admin', NOW(),
   '抽奖台：抽奖/切场次；日志只查；不可作废、不可兑奖发放、不可改名单'),
  (5, '兑奖台', 'kcon_verify_staff', 5, '1', 1, 1, '0', '0', 'admin', NOW(),
   '兑奖台：核验发放；日志只查、可导出奖品发放；不可抽奖、不可权益发放、不可改名单');

-- 三岗位先整体授予全部 KCon 业务菜单，再按下表逐项收回。
-- 每个岗位一张收回清单，改动授权矩阵时同时改三处。
DELETE FROM `sys_role_menu` WHERE `role_id` IN (3, 4, 5);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
  FROM (SELECT 3 AS role_id UNION SELECT 4 UNION SELECT 5) r
  JOIN `sys_menu` m ON m.`menu_id` BETWEEN 3000 AND 3999 AND m.`status` = '0';

-- 签到台（3）：只保留 总览(3001) 名单页(3002+3010/3011/3012/3015/3024)
--              + 日志(3008 权益发放 / 3004 中奖日志 / 3026 奖品发放，均只查)
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 3
   AND `menu_id` IN (
        3007, 3050, 3051,         -- 抽奖控制台
        3005, 3030,               -- 兑奖核验 + 确认发放
        3040,                     -- 中奖作废
        3025, 3027,               -- 发放日志导出（签到台只查不导）
        3003, 3020, 3021, 3022,   -- 奖品设置
        3009, 3060, 3061, 3062,   -- 渠道来源
        3013,                     -- 名单删除
        3014,                     -- 名单导入
        3016, 3017, 3018, 3019, 3023, -- 用户管理
        3006                      -- 数据管理
   );

-- 抽奖台（4）：只保留 总览(3001) 名单只读(3002+3010) 抽奖控制台(3007+3050/3051)
--              + 日志(3008 / 3004 / 3026，均只查)
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 4
   AND `menu_id` IN (
        3011, 3012,               -- 名单新增/修改（只读）
        3013, 3014,               -- 名单删除/导入
        3015,                     -- 名单导出
        3024,                     -- 权益发放
        3005, 3030,               -- 兑奖核验 + 确认发放
        3040,                     -- 中奖作废（作废只留给超管）
        3025, 3027,               -- 发放日志导出（只查不导）
        3003, 3020, 3021, 3022,   -- 奖品设置
        3009, 3060, 3061, 3062,   -- 渠道来源
        3016, 3017, 3018, 3019, 3023, -- 用户管理
        3006                      -- 数据管理
   );

-- 兑奖台（5）：只保留 总览(3001) 名单只读(3002+3010) 兑奖核验(3005+3030)
--              + 日志(3008 / 3004 / 3026) 且可导出奖品发放(3027)
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 5
   AND `menu_id` IN (
        3011, 3012,               -- 名单新增/修改（只读）
        3013, 3014,               -- 名单删除/导入
        3015,                     -- 名单导出
        3024,                     -- 权益发放（与奖品发放分离）
        3007, 3050, 3051,         -- 抽奖控制台
        3040,                     -- 中奖作废
        3025,                     -- 权益发放日志导出（兑奖台只导奖品发放）
        3003, 3020, 3021, 3022,   -- 奖品设置
        3009, 3060, 3061, 3062,   -- 渠道来源
        3016, 3017, 3018, 3019, 3023, -- 用户管理
        3006                      -- 数据管理
   );
-- 三岗位最终保留集（核对用）：
--   签到台 3001 3002 3010 3011 3012 3015 3024 3008 3004 3026 3100
--   抽奖台 3001 3002 3010 3007 3050 3051 3004 3008 3026 3100
--   兑奖台 3001 3002 3010 3005 3030 3004 3008 3026 3027 3100

-- 「设置」目录下已无任何岗位可见子菜单，三个岗位连目录一并收回。
DELETE FROM `sys_role_menu` WHERE `role_id` IN (3, 4, 5) AND `menu_id` = 3200;

-- -------------------------------------------------------------
-- staff 占位账号（默认停用，绑定「签到台」；需超管在「设置 → 用户管理」重置密码后启用）
-- 全员手机号登录：user_name 仅作内部标识（自动生成），登录身份是 phonenumber。
-- 按 phonenumber 幂等：不存在才插入，存在则沿用原账号（不改密码/状态/角色）。
-- 必须放在上面岗位授权完成之后，否则角色绑定会被前面的 DELETE 清掉。
-- -------------------------------------------------------------
INSERT INTO `sys_user`
  (`dept_id`, `user_name`, `nick_name`, `user_type`, `email`, `phonenumber`, `sex`, `avatar`, `password`, `status`, `del_flag`, `login_ip`, `login_date`, `create_by`, `create_time`, `remark`)
SELECT NULL, 'staff_auto', '现场工作人员', '00', '', '10000000000', '2', '',
  '$2a$10$h30sQpojfdnRzS82ER//2eFEzdBj2SudcZv/Aj0lDVvWsd1ME2GiC',
  '1', '0', '', NULL, 'admin', NOW(),
  '现场操作占位账号，默认停用；启用前必须由超管重置密码'
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `phonenumber` = '10000000000' AND `del_flag` = '0');

INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.`user_id`, 3 FROM `sys_user` u
 WHERE u.`phonenumber` = '10000000000' AND u.`del_flag` = '0';

SELECT CONCAT('菜单初始化完成，共 ', COUNT(*), ' 条') AS `结果`
FROM `sys_menu` WHERE `menu_id` BETWEEN 3000 AND 3999;
