-- =============================================================
-- KCon 签到抽奖系统 · 菜单与权限初始化
-- 依赖：RuoYi 基础表（sys_menu）已存在，默认角色（role_id=2 普通角色）
-- 执行顺序：必须先执行 ry_20260320.sql（RuoYi 基础表）再执行本脚本
-- =============================================================

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

-- RuoYi 自带的测试账号，现场不需要，停用（保留行便于追溯）
UPDATE `sys_user` SET `status` = '1', `update_by` = 'admin'
 WHERE `user_name` = 'ry';

-- -------------------------------------------------------------
-- 菜单结构：6 个一级项，其中「日志」「设置」为目录（menu_type='M'）
--   1 数据总览     — 现场高频：签到率与统计
--   2 参会名单     — 开场前导入名单 · 打印签到表
--   3 抽奖控制台   — 现场高频：场次切换与抽奖操作
--   4 兑奖核验     — 现场高频：端口号+手机号核验发放
--   5 日志（目录） — 结束后复盘
--       5.1 中奖日志 / 5.2 核验日志
--   6 设置（目录） — 一次性/低频
--       6.1 奖品设置 / 6.2 数据管理
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
  -- 日志子菜单
  (3004, '中奖日志',   3100, 1, 'winner',    'kcon/winner/index',    '', '', 1, 0, 'C', '0', '0', 'kcon:winner:list',  'money', 'admin', NOW(), '中奖名单与状态'),
  (3008, '核验日志',   3100, 2, 'verifylog', 'kcon/verifylog/index', '', '', 1, 0, 'C', '0', '0', 'kcon:verify:query', 'list',  'admin', NOW(), '核验操作追溯'),
  -- 设置子菜单
  (3003, '奖品设置',   3200, 1, 'prize',     'kcon/prize/index',     '', '', 1, 0, 'C', '0', '0', 'kcon:prize:list',   'star',  'admin', NOW(), '场次奖项档位'),
  (3006, '数据管理',   3200, 2, 'data',      'kcon/data/index',      '', 'KconDataManage', 1, 0, 'C', '0', '0', 'kcon:data:reset', 'tool', 'admin', NOW(), '数据清场与全量备份迁移（仅超管）'),
  (3009, '渠道来源映射', 3200, 3, 'channel-mapping', 'system/dict/index', '', '', 1, 0, 'C', '0', '0', 'system:dict:list', 'dict', 'admin', NOW(), '渠道来源 → 权益类型 / 参与抽奖');

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
  (3051, '抽奖操作', 3007, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'kcon:draw:operate', '#', 'admin', NOW(), '抽奖/作废/切换场次');
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

-- 渠道来源映射使用 RuoYi 原生字典页面，普通角色没有 system:dict:* 权限，只留给超管
DELETE FROM `sys_role_menu`
 WHERE `role_id` = 2
   AND `menu_id` = 3009;

-- -------------------------------------------------------------
-- 渠道来源映射字典（spec 009）
--   dict_label = 渠道来源
--   dict_value = 权益类型
--   remark     = 是 / 否（参与抽奖）
-- 只补缺失项，不覆盖现场已修改的值
-- -------------------------------------------------------------
INSERT INTO `sys_dict_type` (`dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
SELECT 'KCon 渠道来源映射', 'kcon_channel_mapping', '0', 'admin', NOW(), '渠道来源 → 权益类型 / 参与抽奖'
 WHERE NOT EXISTS (SELECT 1 FROM `sys_dict_type` WHERE `dict_type` = 'kcon_channel_mapping');

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

SELECT CONCAT('菜单初始化完成，共 ', COUNT(*), ' 条') AS `结果`
FROM `sys_menu` WHERE `menu_id` BETWEEN 3000 AND 3999;
