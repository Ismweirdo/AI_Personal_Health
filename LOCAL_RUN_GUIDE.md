# 本地运行指南

本文档说明如何在本地启动个人健康管理系统，并记录常用配置、验证步骤和故障排查。

## 运行方式选择

| 方式 | 适用场景 | 入口 |
| --- | --- | --- |
| Docker Compose | 想一次性启动前端、后端和全部依赖服务 | `docker-compose up -d --build` |
| Windows 启动脚本 | 已准备好 MySQL、Redis、RabbitMQ，只想快速启动前后端开发服务 | 双击 `启动项目.bat` |
| 手动启动 | 需要调试前端或后端代码 | 分别运行 Maven 和 npm 命令 |

## 前置要求

手动本地开发建议安装：

- JDK 17 或更高版本
- Maven 3.6 或更高版本
- Node.js 20 或兼容版本
- npm
- MySQL 8.0
- Redis 7
- RabbitMQ 3.12

如果使用 Docker Compose，只需要安装 Docker 和 Docker Compose。

## 方式一：Docker Compose 启动完整环境

在项目根目录执行：

```bash
docker-compose up -d --build
```

查看服务状态：

```bash
docker-compose ps
```

查看后端日志：

```bash
docker-compose logs -f backend
```

停止服务：

```bash
docker-compose down
```

如需同时删除容器数据卷：

```bash
docker-compose down -v
```

### Docker 访问地址

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 前端界面 | http://localhost | Nginx 托管 |
| 后端 API | http://localhost:8080/api | 后端上下文路径 |
| API 文档 | http://localhost:8080/api/swagger-ui/index.html | Swagger UI |
| 健康检查 | http://localhost:8080/api/actuator/health | Actuator health |
| Prometheus 指标 | http://localhost:8080/api/actuator/prometheus | 后端指标 |
| RabbitMQ 管理台 | http://localhost:15672 | `guest` / `guest` |
| Prometheus | http://localhost:9090 | 监控采集 |
| Grafana | http://localhost:3000 | `admin` / `admin` |
| MySQL 主库 | localhost:3306 | `root` / `123456` |
| MySQL 从库 | localhost:3307 | `root` / `123456` |
| Redis | localhost:6379 | 默认无密码 |

Compose 会创建 MySQL 主/从容器、Redis、RabbitMQ、Prometheus、Grafana、后端和前端。当前应用支持主从数据源配置；如需真实 MySQL 复制链路，需要在部署环境中补充复制用户和同步初始化流程。

## 方式二：Windows 快速启动脚本

双击项目根目录下的 `启动项目.bat`。

脚本会执行：

1. 检查 `java`、`mvn`、`npm` 是否可用。
2. 启动后端：`health-management-system` 下执行 `mvn spring-boot:run`。
3. 检查并安装前端依赖。
4. 启动前端：`health-management-web` 下执行 `npm run dev`。

注意：脚本不会启动 MySQL、Redis、RabbitMQ。运行脚本前请确保这些服务已经可连接。也可以先用 Docker 启动依赖服务，再用脚本启动前后端开发服务。

## 方式三：手动本地启动

### 1. 准备数据库

创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS health_management
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

后端配置 `spring.jpa.hibernate.ddl-auto=update`，启动时会根据实体更新表结构；仓库也提供 `health-management-system/src/main/resources/schema.sql` 作为表结构参考和 Docker 初始化脚本。

本地开发可以让主从数据源都连接同一个 MySQL 实例。默认配置已经这样设置：

```yaml
spring:
  datasource:
    master:
      jdbc-url: jdbc:mysql://localhost:3306/health_management
    slave:
      jdbc-url: jdbc:mysql://localhost:3306/health_management
```

### 2. 启动 Redis

默认连接：

```text
host: localhost
port: 6379
password: 空
```

可用 Docker 单独启动：

```bash
docker run --name health-redis -p 6379:6379 -d redis:7.0 redis-server --appendonly yes
```

### 3. 启动 RabbitMQ

默认连接：

```text
host: localhost
port: 5672
username: guest
password: guest
```

可用 Docker 单独启动：

```bash
docker run --name health-rabbitmq -p 5672:5672 -p 15672:15672 -d rabbitmq:3.12-management
```

管理台地址：http://localhost:15672

### 4. 启动后端

```bash
cd health-management-system
mvn spring-boot:run
```

启动成功后可看到类似日志：

```text
Started HealthApplication in ... seconds
```

后端默认运行在：

```text
http://localhost:8080/api
```

### 5. 启动前端

```bash
cd health-management-web
npm install
npm run dev
```

前端默认运行在：

```text
http://localhost:5173
```

Vite 会将 `/api` 代理到 `http://localhost:8080`。

## 默认账号和初始化数据

后端启动时会自动执行 `DataInitializer`。

默认账号：

```text
用户名: testuser
密码:   Test123456
```

如果 `health_data` 表为空，会自动生成近 30 天测试健康数据：

- 步数：`steps`
- 心率：`heart_rate`
- 睡眠：`sleep`
- 体重：`weight`
- 血压：`blood_pressure`
- 血糖：`blood_sugar`

样例数据包含部分异常时期，用于测试健康报告、异常提醒和 AI 分析。

## 常用环境变量

后端默认读取 `application.yml`，并可额外读取 `health-management-system/config/application-secret.yml`。也可以通过环境变量覆盖配置。

### 数据库

```powershell
$env:MYSQL_HOST="localhost"
$env:MYSQL_PORT="3306"
$env:MYSQL_DB="health_management"
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="123456"
$env:MYSQL_SLAVE_HOST="localhost"
$env:MYSQL_SLAVE_PORT="3306"
$env:MYSQL_SLAVE_USERNAME="root"
$env:MYSQL_SLAVE_PASSWORD="123456"
```

### Redis

```powershell
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_PASSWORD=""
```

### RabbitMQ

```powershell
$env:RABBITMQ_HOST="localhost"
$env:RABBITMQ_PORT="5672"
$env:RABBITMQ_USERNAME="guest"
$env:RABBITMQ_PASSWORD="guest"
$env:RABBITMQ_VHOST="/"
```

### AI

可选提供商：`mock`、`openai`、`deepseek`、`qwen`、`baidu_wenxin`。

```powershell
$env:AI_DEFAULT_PROVIDER="mock"
$env:OPENAI_API_KEY=""
$env:DEEPSEEK_API_KEY=""
$env:QWEN_API_KEY=""
$env:WENXIN_API_KEY=""
```

未配置真实 API Key 时，系统会使用或降级到模拟模式。仅本地体验建议将 `AI_DEFAULT_PROVIDER` 设为 `mock`。

### 短信与健康异常提醒

```powershell
$env:SMS_DEBUG_MODE="true"
$env:HEALTH_ALERT_SMS_ENABLED="false"
```

`SMS_DEBUG_MODE=true` 时，短信验证码会返回给前端，方便本地注册和登录。生产环境必须关闭。

### 华为运动健康

```powershell
$env:HUAWEI_CLIENT_ID=""
$env:HUAWEI_CLIENT_SECRET=""
$env:HUAWEI_REDIRECT_URI=""
```

未配置时仍可创建华为设备绑定记录，但同步接口会返回 `pending_config`，不会同步真实设备数据。

## 验证服务

### 后端健康检查

```bash
curl http://localhost:8080/api/actuator/health
```

### API 文档

浏览器访问：

```text
http://localhost:8080/api/swagger-ui/index.html
```

### 前端页面

浏览器访问：

```text
http://localhost:5173
```

登录后可进入：

- 健康管理首页
- 智能健康助手
- 目标管理
- 提醒通知
- 家庭组
- 健康周报与月报
- 设备管理
- 接口使用指南

## 常用开发命令

后端测试：

```bash
cd health-management-system
mvn test
```

后端打包：

```bash
cd health-management-system
mvn clean package
```

前端类型检查：

```bash
cd health-management-web
npm run check
```

前端构建：

```bash
cd health-management-web
npm run build
```

前端预览：

```bash
cd health-management-web
npm run preview
```

前端代码检查：

```bash
cd health-management-web
npm run lint
```

## API 路径速查

所有接口默认带 `/api` 前缀。

| 模块 | 路径 |
| --- | --- |
| 认证 | `/api/auth` |
| 健康数据 | `/api/health` |
| 健康目标 | `/api/goals` |
| 提醒通知 | `/api/reminders` |
| 家庭组 | `/api/family` |
| 设备 | `/api/device` |
| 健康报告 | `/api/reports` |
| AI 助手 | `/api/ai` |
| 监控 | `/api/actuator` |

## 常见问题

### 1. 访问 Swagger 404

确认地址带 `/api`：

```text
http://localhost:8080/api/swagger-ui/index.html
```

后端配置了：

```yaml
server:
  servlet:
    context-path: /api
```

### 2. 前端无法连接后端

检查后端是否启动：

```bash
curl http://localhost:8080/api/actuator/health
```

确认前端请求路径是 `/api/...`。本地开发时 Vite 代理配置位于 `health-management-web/vite.config.ts`。

### 3. 8080 或 5173 端口被占用

修改后端端口：

```yaml
server:
  port: 8081
```

修改前端端口：

```ts
server: {
  port: 5174
}
```

如果修改后端端口，也要同步修改前端代理目标。

### 4. 后端启动时报 MySQL 连接失败

检查：

- MySQL 服务是否启动。
- `health_management` 数据库是否存在。
- `MYSQL_HOST`、`MYSQL_PORT`、用户名和密码是否正确。
- 本地未搭从库时，`MYSQL_SLAVE_HOST` 和 `MYSQL_SLAVE_PORT` 是否指向可用 MySQL。

### 5. Redis 连接失败

检查 Redis 是否启动，并确认端口和密码配置。默认本地配置是 `localhost:6379` 且无密码。

### 6. RabbitMQ 连接失败

检查 RabbitMQ 是否启动：

```text
http://localhost:15672
```

默认账号密码是 `guest` / `guest`。

### 7. 登录或注册收不到短信

本地默认是模拟短信，不会实际发送短信。`SMS_DEBUG_MODE=true` 时，验证码会在接口响应里返回，前端可直接使用。

### 8. AI 返回模拟回复

这是正常降级行为。真实 AI 服务需要配置对应 API Key，并将 `AI_DEFAULT_PROVIDER` 设为可用提供商。

本地只验证功能流程时，可以显式使用：

```powershell
$env:AI_DEFAULT_PROVIDER="mock"
```

### 9. 华为设备同步返回 pending_config

说明尚未配置华为开发者应用参数。当前项目保留华为 Health Service Kit 授权与同步扩展点，未配置时不会同步真实健康数据。

### 10. Docker 前端地址不是 5173

Docker 模式使用 Nginx 暴露 80 端口，访问：

```text
http://localhost
```

`http://localhost:5173` 是 Vite 本地开发服务器地址。
