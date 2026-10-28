#!/bin/sh
# =============================================================
# web 容器启动脚本：合并发布新版静态资源，旧 hash 资源留存一代
# -------------------------------------------------------------
# 背景（第三轮测试报告问题 5）：三端产物文件名带 hash，每次构建
# hash 全变。镜像重建 = 全量替换，已打开的旧页面（浏览器还缓存着
# 旧 index.html，引用旧 hash chunk）刷新时请求旧资源直接 404 白屏。
#
# 方案：静态目录挂宿主机卷（compose: ./data/web-static →
# /usr/share/nginx/html），发布采用「合并」而非「替换」——hash
# 文件名不冲突，把新版拷进现有目录后旧 chunk 自然留存，旧页面
# 刷新就能拿到。
#
# 无限累积防护：新版清单从镜像暂存目录 /opt/web-release 统计（不
# 能从卷统计——卷里混着旧版文件，会把孤儿当"新版"保留）。回收
# 规则：卷内文件既不在新版清单、也不在上一版快照中 → 删除，卷内
# 始终只保留两代资源。入口 HTML 无 hash，每次被新版直接覆盖。
#
# 注意：暂存目录必须在挂载点之外——卷挂载会遮住挂载点内的镜像内容。
# =============================================================
set -e

STATIC=/usr/share/nginx/html
RELEASE=/opt/web-release
MARKER=$STATIC/.release

# 入口分流页静态文件（无 hash，直接覆盖）
for f in index.html kcon-mark.png favicon.ico; do
    [ -f "$RELEASE/$f" ] && cp -a "$RELEASE/$f" "$STATIC/$f"
done

# 三端产物合并进卷（首次启动卷为空则铺初版）
for app in admin checkin screen; do
    if [ -d "$RELEASE/$app" ]; then
        mkdir -p "$STATIC/$app"
        cp -a "$RELEASE/$app/." "$STATIC/$app/"
    fi
done

# 新版清单（从镜像暂存目录统计，与卷内旧文件无关）
: > "$STATIC/.newlist"
for app in admin checkin screen; do
    find "$RELEASE/$app" -type f | sed "s|^$RELEASE/||"
done | sort > "$STATIC/.newlist"

if [ -f "$MARKER" ]; then
    echo "[release] 合并发布完成，回收更早版本的孤儿资源"
    if [ -s "$STATIC/.last-files" ]; then
        # 保留集 = 新版清单 ∪ 上一版快照；孤儿 = 卷内 - 保留集
        sort -u "$STATIC/.last-files" "$STATIC/.newlist" > "$STATIC/.keep"
        for app in admin checkin screen; do
            find "$STATIC/$app" -type f | sed "s|^$STATIC/||" | sort \
                | comm -23 - "$STATIC/.keep" \
                | while read -r f; do rm -f "$STATIC/$f"; done
        done
    fi
    # 兜底：.release 存在但 .last-files 丢失（卷被部分清理/手工删过）时，
    # sort 会因文件不存在报错，叠加 set -e 直接退出 → nginx 不启动、容器 crashloop
    [ -f "$STATIC/.last-files" ] || : > "$STATIC/.last-files"
    # 发布后快照 = 新版 ∪ 实际留存的上一版文件。按磁盘实际存在过滤：
    # 直接用 .keep 会让「上一版快照里已不存在的条目」永久残留
    sort -u "$STATIC/.last-files" "$STATIC/.newlist" | \
        while read -r f; do [ -f "$STATIC/$f" ] && echo "$f"; done \
        > "$STATIC/.last-files"
    rm -f "$STATIC/.keep"
else
    # 首次发布：快照初版清单，供下次换版时区分孤儿
    cp "$STATIC/.newlist" "$STATIC/.last-files"
    touch "$MARKER"
fi
rm -f "$STATIC/.newlist"

echo "[release] 静态资源就绪（旧 hash 资源已保留一代）"
exec "$@"
