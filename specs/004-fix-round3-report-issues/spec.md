# 004 修复第三轮测试报告的 1 个真实缺口 + 1 个补强

## 背景与问题（必填）

第三轮测试报告列出 5 项。逐项核查后，4 项为复测误差，1 项为真实缺口：

- **问题 2 全角数字**：`checkin-h5/src/App.vue` 的 `normalizeDigits`（`[\uFF10-\uFF19]`）在本地与云端产物中均已验证存在。复测设备缓存旧 JS，不修
- **问题 3 导入模板端口号列**：`AttendeeController.template()` 已 `hideColumn("portNo")`（spec 002），线上模板无此列。不修
- **问题 4 防重文案**：后端 `RepeatSubmit.message()` 默认值与前端拦截器文案均已改为「操作过于频繁」（spec 002/003）；云端后端 jar 解包实测只含新文案。复测环境为旧实例，不修
- **问题 1 空表单清空赞助商**：二次确认在产物中已生效，但核查发现真实缺口——`loadSetting` 失败时静默 catch，表单呈"假空"，用户此时点保存仍会误清线上赞助商。需补强
- **问题 5 部署保留旧静态资源**：真实缺口。web 镜像重建时容器内静态资源全量替换，已打开设备（缓存旧 index.html、引用旧 hash chunk）刷新即 404 白屏，现场只能强刷。需实现

## 修复清单

| 报告项 | 问题 | 根因 | 修复方式 |
|---|---|---|---|
| 1（补强） | 设置读取失败后点保存会误清赞助商 | `loadSetting` 静默 catch，表单"假空" | 新增 `settingLoaded` 状态：未成功读取时禁用保存按钮，点保存引导「重新读取」 |
| 5 | 换版后已打开设备刷新 404 白屏 | 镜像重建 = 容器内静态资源全量替换 | 静态资源持久化到宿主机卷 `data/web-static/`；entrypoint「合并发布」：hash 文件名不冲突，旧 chunk 自然留存一代；清单回收更早版本的孤儿文件，卷内始终只保留两代 |

## 实现要点（问题 5）

- `docker-compose.yml`：web 服务挂 `./data/web-static:/usr/share/nginx/html`
- `docker/web/Dockerfile`：新版产物暂存 `/opt/web-release`（挂载点之外——卷会遮住挂载点内的镜像内容）；显式声明 `CMD ["nginx", "-g", "daemon off;"]`（覆盖 ENTRYPOINT 后基础镜像 CMD 不会保留）
- `docker/web/entrypoint.sh`：合并发布 + 清单回收（新版清单从 `/opt/web-release` 统计，不能从卷统计——卷里混着旧文件会把孤儿当新版保留；快照按磁盘实际存在过滤，避免已删条目残留）
- nginx 不变：hash 文件名不冲突，合并后旧资源天然可访问

## 验收标准

- [x] 管理端抽奖设置：设置未成功读取时保存按钮禁用，点保存提示先「重新读取」
- [x] 设置读取失败有明确报错提示（不再静默）
- [x] 换版（`up -d --build web`）后，旧 hash 资源请求仍 200（实测 `index-C7lUo3VV.js` 重建后 200）
- [x] 注入孤儿文件后下一次发布被自动回收（实测 orphan2/orphan3/orphanA 均被清）
- [x] 快照收敛：已删条目不永久残留（实测 `.last-files` 回到 227 条、零 orphan）
- [x] 三端入口 + 后端 API 全部 200，web 容器稳定运行
