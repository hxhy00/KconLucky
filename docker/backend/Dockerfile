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
ENV TZ=Asia/Shanghai JAVA_OPTS="-Xms256m -Xmx768m"

# mysql-client：管理端「数据迁移」功能需要调 mysqldump / mysql
# 导出全量 .sql 备份与从 .sql 恢复（需求 AC-11），不自研 SQL 导出器。
# 清华镜像源，避免现场/构建时走境外源。
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends mysql-client; \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /src/ruoyi-admin/target/ruoyi-admin.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
