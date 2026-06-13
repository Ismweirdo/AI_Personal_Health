# 个人健康管理系统

个人健康管理系统是一个前后端分离的健康数据管理平台，包含健康指标记录、趋势分析、目标与提醒、家庭健康协作、设备接入、健康报告和 AI 健康助手等功能。

## 项目结构

```text
Personal-Health-Management-Tool/
├── health-management-system/       # Spring Boot 后端
│   ├── src/main/java/com/health/
│   │   ├── controller/             # REST API 控制器
│   │   ├── service/                # 业务服务
│   │   ├── repository/             # JPA 仓库
│   │   ├── entity/                 # 数据实体
│   │   ├── dto/                    # 请求与响应 DTO
│   │   ├── ai/                     # AI 服务适配层
│   │   ├── config/                 # 安全、数据源、缓存、消息队列等配置
│   │   └── utils/                  # JWT、缓存、限流、指标支持等工具
│   ├── src/main/resources/
│   │   ├── application.yml         # 后端主配置
│   │   └── schema.sql              # 数据库表结构
│   └── pom.xml
├── health-management-web/          # Vue 3 前端
│   ├── src/api/                    # 前端 API 封装
│   ├── src/components/             # 通用、健康数据、AI 组件
│   ├── src/views/                  # 页面视图
│   ├── src/router/                 # 路由与登录守卫
│   ├── package.json
│   ├── vite.config.ts
│   ├── Dockerfile
│   └── nginx.conf
├── docker-compose.yml              # 容器编排
├── mysql-master.cnf                # MySQL 主库容器配置
├── mysql-slave.cnf                 # MySQL 从库容器配置
├── prometheus.yml                  # Prometheus 抓取配置
├── 启动项目.bat                    # Windows 本地快速启动脚本
├── LOCAL_RUN_GUIDE.md              # 本地运行指南
└── README.md
```

## 核心功能

### 账号与认证

- 用户注册、密码登录、手机号验证码登录和退出登录。
- 登录验证码包含图像选择挑战，短信验证码本地默认以调试模式返回。
- 基于 Spring Security + JWT 的无状态认证，前端自动携带 `Authorization: Bearer <token>`。

### 健康数据管理

- 支持步数、心率、睡眠、体重、血压、血糖等健康指标。
- 支持新增、编辑、删除、列表查询、趋势查询。
- 支持 CSV 导入、导出和导入模板下载。
- 异常指标会生成站内通知；可通过配置启用短信提醒。
- 查询结果使用 Redis 缓存，数据变更后自动清理相关缓存。

### 目标、提醒与通知

- 支持健康目标创建、编辑、删除和启用状态管理。
- 支持提醒规则创建、编辑、启停和删除。
- 支持站内通知列表、标记已读和删除。
- 家庭邀请、异常健康指标等事件会写入通知记录。

### 家庭健康协作

- 支持创建家庭组、成员邀请、邀请码加入和邀请审批。
- 家庭角色包含 `parent` 和 `child`。
- 家长可查看家庭内儿童健康数据，并为儿童设置目标和提醒。
- 家庭创建者可管理成员角色和移除成员。

### 设备接入

- 支持注册健康设备并生成 `deviceId` 与 API Key。
- 支持设备状态管理、设备列表和设备详情。
- 支持设备通过接口写入健康数据并记录接入日志。
- 设备数据接口会校验 API Key、设备状态、数据类型和记录时间。
- 预留华为运动健康绑定与同步扩展点；真实同步需要配置华为开发者应用并完成授权。

### 健康报告

- 支持周报、月报生成。
- 报告包含指标概览、异常分析、目标达成情况和建议。
- 支持报告历史快照查询。

### AI 健康助手

- 支持同步聊天和 SSE 流式聊天。
- 会结合用户近 30 天健康数据、目标、提醒、通知和设备数据生成上下文。
- 支持会话历史、会话列表、推荐问题和清空历史。
- 支持 AI 生成目标/提醒草案，并可直接执行创建。
- 支持运行时查看和切换 AI 服务提供商。
- 可用提供商：`mock`、`openai`、`deepseek`、`qwen`、`baidu_wenxin`。未配置真实 API Key 时会降级到模拟模式。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.1.2
- Spring Web / Spring Data JPA / Spring Security / Spring Validation
- MySQL 8.0
- Redis 7
- RabbitMQ 3.12
- Springdoc OpenAPI
- Spring Boot Actuator + Micrometer Prometheus
- JWT、Lombok、HikariCP、AOP

### 前端

- Vue 3 + TypeScript
- Vite 5
- Vue Router
- Element Plus
- Tailwind CSS
- ECharts / vue-echarts
- axios
- lucide-vue-next
- marked

### 基础设施

- Docker + Docker Compose
- Nginx
- Prometheus
- Grafana

## 快速启动

### 方式一：Docker Compose 启动完整环境

```bash
docker-compose up -d --build
docker-compose ps
```

Docker 会启动前端、后端、MySQL 主/从容器、Redis、RabbitMQ、Prometheus 和 Grafana。

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| 前端界面 | http://localhost | Nginx 托管的前端生产构建 |
| 后端 API | http://localhost:8080/api | Spring Boot API 根路径 |
| API 文档 | http://localhost:8080/api/swagger-ui/index.html | Swagger UI |
| 健康检查 | http://localhost:8080/api/actuator/health | Actuator health |
| RabbitMQ 管理台 | http://localhost:15672 | 默认账号 `guest` / `guest` |
| Prometheus | http://localhost:9090 | 指标采集 |
| Grafana | http://localhost:3000 | 默认账号 `admin` / `admin` |
| MySQL 主库 | localhost:3306 | root / 123456 |
| MySQL 从库 | localhost:3307 | root / 123456 |
| Redis | localhost:6379 | 默认无密码 |

说明：Compose 会启动主库和从库容器，应用支持主从数据源与读写分离扩展；如需严格数据库复制链路，请根据实际部署环境补充复制用户和同步配置。

### 方式二：Windows 快速启动脚本

双击根目录下的 `启动项目.bat`，脚本会检查 Java、Maven、npm，随后分别启动后端和前端开发服务器。

该脚本不会自动启动 MySQL、Redis、RabbitMQ。使用脚本前请先准备这些依赖服务，或先通过 Docker 单独启动依赖服务。

### 方式三：手动本地开发启动

后端：

```bash
cd health-management-system
mvn spring-boot:run
```

前端：

```bash
cd health-management-web
npm install
npm run dev
```

本地开发访问地址：

| 服务 | 地址 |
| --- | --- |
| 前端开发服务器 | http://localhost:5173 |
| 后端 API | http://localhost:8080/api |
| API 文档 | http://localhost:8080/api/swagger-ui/index.html |
| 健康检查 | http://localhost:8080/api/actuator/health |

更完整的本地配置和排障步骤见 [LOCAL_RUN_GUIDE.md](./LOCAL_RUN_GUIDE.md)。

## 默认数据

后端启动时会通过 `DataInitializer` 自动检查并创建测试数据：

```text
用户名: testuser
密码:   Test123456
```

如果健康数据表为空，会自动为测试用户生成近 30 天健康数据，包含步数、心率、睡眠、体重、血压和血糖。

## 环境变量

后端配置位于 `health-management-system/src/main/resources/application.yml`，默认会额外加载可选文件 `./config/application-secret.yml`。常用环境变量如下：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MYSQL_HOST` | `localhost` | MySQL 主库地址 |
| `MYSQL_PORT` | `3306` | MySQL 主库端口 |
| `MYSQL_DB` | `health_management` | 数据库名 |
| `MYSQL_USERNAME` | `root` | MySQL 主库用户名 |
| `MYSQL_PASSWORD` | `123456` | MySQL 主库密码 |
| `MYSQL_SLAVE_HOST` | `localhost` | MySQL 从库地址 |
| `MYSQL_SLAVE_PORT` | `3306` | MySQL 从库端口，本地默认可指向同一库 |
| `MYSQL_SLAVE_USERNAME` | `root` | MySQL 从库用户名 |
| `MYSQL_SLAVE_PASSWORD` | `123456` | MySQL 从库密码 |
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ 地址 |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP 端口 |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ 用户名 |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ 密码 |
| `RABBITMQ_VHOST` | `/` | RabbitMQ vhost |
| `JWT_SECRET` | 内置开发密钥 | JWT 签名密钥 |
| `JWT_EXPIRATION` | `86400000` | JWT 有效期，单位毫秒 |
| `SMS_DEBUG_MODE` | `true` | 是否在响应中返回短信验证码 |
| `HEALTH_ALERT_SMS_ENABLED` | `false` | 是否启用健康异常短信提醒 |
| `AI_DEFAULT_PROVIDER` | `mock` | 默认 AI 提供商 |
| `OPENAI_API_KEY` | 空 | OpenAI API Key |
| `DEEPSEEK_API_KEY` | 空 | DeepSeek API Key |
| `QWEN_API_KEY` | 空 | 通义千问 API Key |
| `WENXIN_API_KEY` | 空 | 百度千帆/文心 API Key |
| `HUAWEI_CLIENT_ID` | 空 | 华为开发者应用 Client ID |
| `HUAWEI_CLIENT_SECRET` | 空 | 华为开发者应用 Client Secret |
| `HUAWEI_REDIRECT_URI` | 空 | 华为授权回调地址 |

生产环境请不要使用默认数据库密码、默认 JWT 密钥或调试短信模式。

## API 分组

所有后端接口默认带 `/api` 上下文路径。

| 分组 | 路径前缀 | 说明 |
| --- | --- | --- |
| 认证 | `/api/auth` | 验证码、注册、登录、手机号验证码登录、退出 |
| 健康数据 | `/api/health` | 数据增删改查、趋势、导入导出 |
| 目标 | `/api/goals` | 健康目标管理 |
| 提醒通知 | `/api/reminders` | 提醒规则和站内通知 |
| 家庭组 | `/api/family` | 家庭、成员、邀请、家长儿童关系 |
| 设备 | `/api/device` | 设备注册、管理、设备数据写入、华为绑定 |
| 报告 | `/api/reports` | 周报、月报和历史快照 |
| AI | `/api/ai` | 聊天、流式聊天、历史、推荐问题、行动草案、提供商管理 |
| 监控 | `/api/actuator` | health、info、metrics、prometheus |

设备接入说明也可以在前端登录后访问“接口使用指南”页面查看。

## 数据库表

`schema.sql` 当前包含以下核心表：

- `users`
- `login_logs`
- `health_data`
- `health_goals`
- `reminder_rules`
- `family_groups`
- `family_members`
- `family_invitations`
- `notification_records`
- `health_devices`
- `device_data_logs`
- `ai_chat_message`
- `health_report_snapshot`

## 质量与验证

后端测试：

```bash
cd health-management-system
mvn test
```

前端类型检查与构建：

```bash
cd health-management-web
npm run check
npm run build
```

前端代码检查：

```bash
cd health-management-web
npm run lint
```

## 运行注意事项

- 后端上下文路径是 `/api`，直接访问接口或 Swagger 时不要遗漏该前缀。
- 前端开发服务器通过 Vite 将 `/api` 代理到 `http://localhost:8080`。
- Docker 前端入口是 `http://localhost`，本地开发前端入口是 `http://localhost:5173`。
- 本地开发默认主库和从库都可以指向同一个 MySQL 实例。
- 未配置真实 AI Key 时，AI 模块会使用或降级到模拟模式。
- 华为运动健康当前保留授权绑定和同步扩展点，不会伪造真实设备数据。

## 相关文档

- [本地运行指南](./LOCAL_RUN_GUIDE.md)
- API 文档：运行后访问 http://localhost:8080/api/swagger-ui/index.html
- 设备接口指南：登录前端后访问“接口使用指南”页面
