# =============================================================
# 三端前端构建 + Nginx 统一入口
# 注意：RuoYi-Vue 用 @vue/cli-service 4.x（webpack4），
#       必须用 Node 16，Node 18+ 会因 OpenSSL3 报 digital envelope 错误。
# =============================================================

# ---------- 1) 管理端 RuoYi-Vue ----------
FROM node:16-alpine AS build-admin
WORKDIR /app
RUN npm config set registry https://registry.npmmirror.com
# 三端均有 package-lock.json（已核对），一律用 npm ci 保证依赖与锁文件一致、构建可复现
COPY ruoyi-ui/package.json ruoyi-ui/package-lock.json* ./
RUN npm ci --legacy-peer-deps --no-audit --no-fund
COPY ruoyi-ui/ .
RUN npm run build:prod

# ---------- 2) 签到 H5 ----------
FROM node:20-alpine AS build-checkin
WORKDIR /app
RUN npm config set registry https://registry.npmmirror.com
COPY checkin-h5/package.json checkin-h5/package-lock.json* ./
RUN npm ci --no-audit --no-fund
COPY checkin-h5/ .
RUN npm run build

# ---------- 3) 抽奖大屏 ----------
FROM node:20-alpine AS build-screen
WORKDIR /app
RUN npm config set registry https://registry.npmmirror.com
COPY screen/package.json screen/package-lock.json* ./
RUN npm ci --no-audit --no-fund
COPY screen/ .
RUN npm run build

# ---------- 4) 运行 ----------
FROM nginx:1.27-alpine
ENV TZ=Asia/Shanghai
RUN rm -f /etc/nginx/conf.d/default.conf
COPY docker/web/nginx.conf /etc/nginx/nginx.conf
# 安全响应头公共片段（nginx.conf http 层及各带 add_header 的 location 里 include）
COPY docker/web/security-headers.conf /etc/nginx/security-headers.conf
COPY docker/web/entrypoint.sh /release-and-run.sh
COPY docker/web/index.html /opt/web-release/index.html
COPY docker/web/kcon-mark.png /opt/web-release/kcon-mark.png
COPY docker/web/favicon.ico /opt/web-release/favicon.ico
# 新版产物暂存在挂载点之外：compose 把宿主机卷挂到 /usr/share/nginx/html
# 会遮住挂载点内的所有镜像内容，暂存目录放这里会被卷盖掉导致发布失败。
# entrypoint 启动时把它合并发布进卷，并把更早版本的孤儿资源回收。
COPY --from=build-admin   /app/dist /opt/web-release/admin
COPY --from=build-checkin /app/dist /opt/web-release/checkin
COPY --from=build-screen  /app/dist /opt/web-release/screen
RUN chmod +x /release-and-run.sh
# 覆盖 ENTRYPOINT 后基础镜像的 CMD 不会自动保留，必须显式声明，
# 否则 entrypoint 里 exec "$@" 执行空参数直接退出（现象：容器反复重启、退出码 0）
ENTRYPOINT ["/release-and-run.sh"]
CMD ["nginx", "-g", "daemon off;"]
EXPOSE 80
