# ============================================================
# 后端镜像（Railway 部署）
# 构建上下文：项目根目录（TX_Web/）
# 前端与 admin 页面会在构建时打入 jar，admin 随后端一起部署
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml ./pom.xml
RUN mvn -q -DskipTests dependency:resolve
COPY backend/src ./src
COPY frontend ./frontend
COPY admin ./admin
RUN rm -rf src/main/resources/static/frontend src/main/resources/static/admin \
    && mkdir -p src/main/resources/static \
    && cp -r frontend src/main/resources/static/frontend \
    && cp -r admin src/main/resources/static/admin
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/spectrum-1.0.0.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]
