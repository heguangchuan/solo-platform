# ============================================================
#  solo 生产镜像（运行态，仅打包运行）
#
#  编译由宿主机 / CI 的 Maven 完成：Jenkinsfile 已配置 tools maven/jdk21，
#  并在 prepare 阶段执行 `mvn clean package`，产出 target/solo-*.jar。
#  因此本 Dockerfile 不再包含 Maven 构建阶段，只 COPY 预编译的 fat jar。
#
#  与 Jenkinsfile 对齐：
#    Jenkins 以 `docker build --build-arg profile=${BRANCH_NAME} -t solo-platform/solo:<version> .`
#    在本目录（solo/）触发构建；profile 决定激活 application-<profile>.yaml（默认 prod）。
#
#  本地/CI 构建步骤：
#    1) mvn clean package -DskipTests
#    2) docker build --build-arg profile=prod -t solo-platform/solo:1.0.0 .
# ============================================================

FROM eclipse-temurin:21-jre

# 接收 Jenkins 传入的 profile（= 分支名），默认 prod
ARG profile=prod
WORKDIR /app

# 创建非 root 用户，降低容器被攻破后的影响面
RUN groupadd -r solo && useradd -r -g solo -m -d /app solo

# 仅拷贝宿主机已编译好的 fat jar（由 CI 的 Maven 产出）
COPY target/solo-*.jar /app/app.jar

# 激活与构建参数一致的 Spring profile（application-<profile>.yaml 须存在）
ENV SPRING_PROFILES_ACTIVE=${profile} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# 暴露端口：
#   10003  -> HTTP 管理面 / API
#   15060  -> GB28181 SIP 信令（TCP + UDP）
# 若启用级联 RTP 收发，另需发布 50000-60000/udp（见 application-prod.yaml 的 cascade 段）
EXPOSE 10003 15060 15060/udp

# 存活探针：端口级 TCP 探测（镜像未内置 curl）。
# 建议生产引入 spring-boot-starter-actuator 后改用：
#   CMD-SHELL curl -fsS http://localhost:10003/actuator/health || exit 1
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD bash -c 'cat < /dev/null > /dev/tcp/127.0.0.1/10003'

USER solo
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
