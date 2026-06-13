# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Production stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# 安装curl用于健康检查
RUN apk add --no-cache curl

# 添加健康检查脚本
COPY healthcheck.sh /app/healthcheck.sh
RUN chmod +x /app/healthcheck.sh

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD /app/healthcheck.sh

ENTRYPOINT ["java", "-jar", "app.jar"]
