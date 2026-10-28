# =============================================================
# 发行版数据库镜像：把基线 SQL 打进镜像
# -------------------------------------------------------------
# 目的：外部用户只拿镜像 + 编排文件就能起库，不需要仓库里的 sql/ 目录。
# 首次启动（数据卷为空）由 mysql 官方 entrypoint 按文件名顺序执行，
# 已有数据不会重复执行。
#
# 基线三脚本：
#   00-ry-base.sql   RuoYi 基础表
#   kcon_lucky.sql   KCon 业务表
#   kcon_menu.sql    菜单 / 角色 / 权限 / 账号
# =============================================================
FROM mysql:8.0

ENV TZ=Asia/Shanghai

COPY sql/00-ry-base.sql   /docker-entrypoint-initdb.d/00-ry-base.sql
COPY sql/kcon_lucky.sql   /docker-entrypoint-initdb.d/01-kcon_lucky.sql
COPY sql/kcon_menu.sql    /docker-entrypoint-initdb.d/02-kcon-menu.sql
