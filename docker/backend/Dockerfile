# --- 构建阶段 ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Maven 国内镜像（由 compose 以 build context=项目根 + dockerfile 指定，见下方 COPY 说明）
COPY docker/maven-settings.xml /root/.m2/settings.xml

# 先拷 pom，利用 Docker 层缓存加速依赖下载
COPY ruoyi/pom.xml ./
COPY ruoyi/ruoyi-admin/pom.xml ruoyi-admin/
COPY ruoyi/ruoyi-common/pom.xml ruoyi-common/
COPY ruoyi/ruoyi-framework/pom.xml ruoyi-framework/
COPY ruoyi/ruoyi-system/pom.xml ruoyi-system/
COPY ruoyi/ruoyi-quartz/pom.xml ruoyi-quartz/
COPY ruoyi/ruoyi-generator/pom.xml ruoyi-generator/
RUN mvn -B dependency:go-offline -DskipTests

COPY ruoyi/ .
RUN mvn -B clean package -DskipTests

# --- 运行阶段 ---
FROM eclipse-temurin:17-jre
WORKDIR /app
# 堆上限按「宿主机总内存」定，不是按业务量定。
# 线上实例为 1.7G 内存且无 swap，原先 -Xmx768m 会让 JVM 常驻逼近 900m，
# 叠加 MySQL(约 400m) 与系统进程后直接 OOM Kill。
# 本系统是单场活动、并发极低的写入型负载，-Xmx512m 足够；
# 宁可让 GC 多跑几次，也不要把机器推到 OOM。
#
# !! 这里的值会被 docker-compose.yml 的 environment.JAVA_OPTS 覆盖 !!
# compose 里写的是 ${JAVA_OPTS:-<同上默认值>}，两处默认值必须保持一致；
# 只想临时调参时改 .env 即可，不要只改本文件——改了也不生效。
ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms256m -Xmx512m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError"

# mysql-client：管理端「数据迁移」功能需要调 mysqldump / mysql
# 导出全量 .sql 备份与从 .sql 恢复（需求 AC-11），不自研 SQL 导出器。
# apt 源为 Debian 官方 CDN（deb.debian.org），未换国内镜像；构建时如遇网络问题再评估切换
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends mysql-client; \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /src/ruoyi-admin/target/ruoyi-admin.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
