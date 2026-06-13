#!/bin/sh

# 健康检查脚本
# 检查应用是否正常运行

HEALTH_URL="http://localhost:8080/api/actuator/health"

# 尝试访问健康检查端点
if curl -f -s "$HEALTH_URL" > /dev/null; then
    echo "Application is healthy"
    exit 0
else
    echo "Application is unhealthy"
    exit 1
fi