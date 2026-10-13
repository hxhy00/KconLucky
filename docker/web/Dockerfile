# =============================================================
# 三端前端构建 + Nginx 统一入口
# 注意：RuoYi-Vue 用 @vue/cli-service 4.x（webpack4），
#       必须用 Node 16，Node 18+ 会因 OpenSSL3 报 digital envelope 错误。
# =============================================================

# ---------- 1) 管理端 RuoYi-Vue ----------
FROM node:16-alpine AS build-admin
WORKDIR /app
RUN npm config set registry https://registry.npmmirror.com
COPY ruoyi-ui/package.json ruoyi-ui/package-lock.json* ./
RUN npm install --legacy-peer-deps --no-audit --no-fund
COPY ruoyi-ui/ .
RUN npm run build:prod

# ---------- 2) 签到 H5 ----------
FROM node:20-alpine AS build-checkin
WORKDIR /app
RUN npm config set registry https://registry.npmmirror.com
COPY checkin-h5/package.json checkin-h5/package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY checkin-h5/ .
RUN npm run build

# ---------- 3) 抽奖大屏 ----------
FROM node:20-alpine AS build-screen
WORKDIR /app
RUN npm config set registry https://registry.npmmirror.com
COPY screen/package.json screen/package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY screen/ .
RUN npm run build

# ---------- 4) 运行 ----------
FROM nginx:1.27-alpine
ENV TZ=Asia/Shanghai
RUN rm -f /etc/nginx/conf.d/default.conf
COPY docker/web/nginx.conf /etc/nginx/nginx.conf
COPY docker/web/index.html /usr/share/nginx/html/index.html
COPY docker/web/kcon-logo.png /usr/share/nginx/html/kcon-logo.png
COPY --from=build-admin   /app/dist /usr/share/nginx/html/admin
COPY --from=build-checkin /app/dist /usr/share/nginx/html/checkin
COPY --from=build-screen  /app/dist /usr/share/nginx/html/screen
EXPOSE 80
