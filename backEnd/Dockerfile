# ============================================
# MokaTest 后端 - Docker 构建文件
# 适用：2C4G 服务器环境
# 说明：使用 Playwright 官方 Java 镜像作为运行基础，
#      已内置 Chrome 浏览器及所有系统依赖，无需额外安装
# ============================================

# ---- 阶段 1：构建 ----
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 配置阿里云 Maven 镜像，加速依赖下载
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' >> /root/.m2/settings.xml && \
    echo '        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' >> /root/.m2/settings.xml && \
    echo '        xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">' >> /root/.m2/settings.xml && \
    echo '  <mirrors>' >> /root/.m2/settings.xml && \
    echo '    <mirror>' >> /root/.m2/settings.xml && \
    echo '      <id>aliyunmaven</id>' >> /root/.m2/settings.xml && \
    echo '      <name>阿里云公共仓库</name>' >> /root/.m2/settings.xml && \
    echo '      <url>https://maven.aliyun.com/repository/public</url>' >> /root/.m2/settings.xml && \
    echo '      <mirrorOf>central</mirrorOf>' >> /root/.m2/settings.xml && \
    echo '    </mirror>' >> /root/.m2/settings.xml && \
    echo '  </mirrors>' >> /root/.m2/settings.xml && \
    echo '</settings>' >> /root/.m2/settings.xml

# 先复制所有 pom.xml，利用 Docker 缓存层（依赖不变时不重复下载）
COPY pom.xml .
COPY platform-automation/pom.xml platform-automation/
COPY platform-qa/pom.xml platform-qa/
COPY platform-starter/pom.xml platform-starter/

# 预下载依赖（使用阿里云镜像）
RUN mvn -s /root/.m2/settings.xml dependency:go-offline -pl platform-starter -am -q

# 复制完整源码
COPY . .

# 打包（跳过测试，使用阿里云镜像）
RUN mvn -s /root/.m2/settings.xml clean package -pl platform-starter -am -DskipTests -q

# ---- 阶段 2：运行 ----
# Playwright 官方 Java 镜像，已包含 Chrome + 依赖
FROM mcr.azure.cn/playwright/java:v1.52.0

WORKDIR /app

# 设置容器 locale 为 UTF-8，避免中文文件名乱码
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# 复制构建好的 jar
COPY --from=builder /build/platform-starter/target/api.jar app.jar

# JVM 参数：按容器内存动态分配，2G 容器时堆约 1.2G
ENV JAVA_OPTS="\
  -server \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:MaxRAMPercentage=60.0 \
  -XX:InitialRAMPercentage=40.0 \
  -XX:MinRAMPercentage=40.0 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/tmp/heapdump.hprof \
  -Djava.security.egd=file:/dev/./urandom \
  -Dfile.encoding=UTF-8 \
  -Dsun.jnu.encoding=UTF-8 \
  -Dspring.profiles.active=docker"

EXPOSE 7529

# 健康检查（Actuator health 端点，start-period 给 Playwright/Spring Boot 留足启动时间）
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD wget -qO- http://localhost:7529/api/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
