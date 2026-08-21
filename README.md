# 法律条文标注系统

法律条文标注系统 V1.5 是一个面向完整法律文件的在线结构化标注平台。本仓库已提供前后端工程骨架、共享契约，以及基于服务端 Session 的认证、用户管理和首次管理员初始化能力。法律、任务、标注、审核、修订等业务仍由后续 PR 实现。

## 技术栈

- 前端：Vue 3 + TypeScript + Vite 7 + Vue Router 4 + Axios
- 后端：JDK 21 + Spring Boot 3.5.16
- 构建：npm、Maven 3.9.16（Maven Wrapper 3.3.4）
- 数据库：MongoDB 8.0
- 基础设施：Docker Compose

## 目录

```text
law-annotation-system/
├── frontend/                 # Vue 3 前端工程
│   ├── src/
│   │   ├── api/              # Axios 请求封装
│   │   ├── assets/           # 静态资源
│   │   ├── components/       # 公共组件
│   │   ├── layouts/          # 页面布局
│   │   ├── router/           # 路由配置
│   │   ├── views/            # 后续业务页面
│   │   ├── App.vue
│   │   └── main.ts
│   ├── .env.example          # 前端环境变量示例
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
├── backend/                  # Spring Boot 后端工程
├── docker-compose.yml        # PR01 最小 MongoDB 服务
├── .env.example              # 后端与 Compose 环境变量示例
├── AGENTS.md                 # 后续开发协作约束
└── README.md
```

## 环境要求

- Eclipse Temurin JDK 21 或其他兼容 JDK 21
- Node.js 20.19 或更高版本及 npm
- Docker Desktop 与 Docker Compose
- 无需安装全局 Maven，统一使用仓库中的 Maven Wrapper

## 环境变量

复制 `.env.example` 为 `.env` 后修改示例凭据。`.env` 不会提交到 Git。

| 变量 | 用途 | PR01 约定 |
| --- | --- | --- |
| `SERVER_PORT` | Spring Boot 端口 | 默认 `8080` |
| `FRONTEND_PORT` | 后续 Docker 用户入口 | 预留 `8081`，PR01 不监听 |
| `MONGO_PORT` | MongoDB 宿主机开发端口 | 默认 `27017` |
| `MONGO_INITDB_ROOT_USERNAME` | Compose MongoDB 初始化账号 | 仅示例值 |
| `MONGO_INITDB_ROOT_PASSWORD` | Compose MongoDB 初始化密码 | 必须替换示例值 |
| `MONGO_INITDB_DATABASE` | MongoDB 数据库名 | 示例为 `law_annotation` |
| `MONGODB_URI` | Spring Data MongoDB 连接串 | 必须由环境变量提供 |
| `APP_INIT_ADMIN_ENABLED` | 是否启用首次管理员初始化 | 示例为 `true` |
| `APP_INIT_ADMIN_USERNAME` | 首次管理员账号 | 仅初始化输入 |
| `APP_INIT_ADMIN_PASSWORD` | 首次管理员密码 | 必须替换演示值 |
| `APP_INIT_ADMIN_NAME` | 首次管理员姓名 | 仅初始化输入 |
| `APP_CORS_ALLOWED_ORIGINS` | 允许携带凭据的前端来源 | 本地示例为 `http://localhost:5173` |
| `APP_AUTH_SESSION_TIMEOUT` | 服务端 Session 超时 | 默认 `30m` |

Spring Boot 不会自动把 `.env` 导入当前终端。Windows PowerShell 本地启动前可执行：

```powershell
$env:MONGODB_URI = "mongodb://law_admin:change-me@localhost:27017/law_annotation?authSource=admin"
$env:SERVER_PORT = "8080"
```

真实部署不得沿用 `change-me` 等示例凭据。

## 启动 MongoDB

PR01 的 Compose 只启动 MongoDB，不包含后端容器、前端容器或 Nginx：

```powershell
Copy-Item .env.example .env
docker compose config
docker compose up -d mongo
docker compose ps
```

停止服务但保留数据卷：

```powershell
docker compose down
```

完整 Docker 部署、8081 用户入口和重置/恢复闭环属于 PR24。

## 安装与启动前端

```bash
cd frontend
npm install
npm run dev
```

开发服务器默认访问地址为 `http://localhost:5173`。前端 API 地址默认使用 `/api`；本地开发时，Vite 会将 `/api/*` 请求去除 `/api` 前缀后代理到 `http://localhost:8080`，例如 `/api/auth/login` 会转发到 `http://localhost:8080/auth/login`。生产或 Docker 环境同样统一请求 `/api/*`，由部署层反向代理到后端。`VITE_API_BASE_URL` 仅用于特殊部署场景覆盖默认地址。

## 构建、测试与启动后端

Windows：

```powershell
cd backend
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

macOS/Linux：

```bash
cd backend
./mvnw clean verify
./mvnw spring-boot:run
```

后端默认监听 `8080`。PR03 新增的业务 API 仅限认证与用户管理。

## 认证与用户管理

PR03 使用 Spring Security 服务端 Session，不使用 JWT、Refresh Token 或第三方登录。浏览器先请求 `GET /auth/csrf`，保存返回的 Session Cookie，并在后续 `POST`、`PATCH`、`DELETE` 请求中通过返回的 `headerName` 携带 CSRF Token。前端 Axios 公共实例已启用 Cookie 凭据。

认证接口包括登录、退出、当前用户、修改姓名和修改密码；`/users/**` 仅允许 `ADMIN` 访问，提供分页查询、创建、改名、重置密码、启停和有限删除。登录账号通过 `normalizedAccount` 实现大小写不敏感唯一，密码只保存 BCrypt 哈希。

全新数据库首次启动时，如果不存在任何 `ADMIN` 且 `APP_INIT_ADMIN_ENABLED=true`，后端会使用环境变量创建一个管理员。初始化配置缺失或不合法会导致启动失败；已有管理员时重启不会重复创建，也不会用环境变量重置密码。`.env.example` 中的 `admin/admin123` 仅为课程演示值，真实环境必须替换。

## 开发基线

当前业务 Source of Truth 为两份 2026-08-19 V1.5 文档：

1. 《法律条文标注系统 Codex 项目上下文与开发边界》
2. 《法律条文标注系统 需求规格说明书与团队开发对接文档》

若两份 V1.5 文档存在业务语义冲突，以《需求规格说明书与团队开发对接文档》为准。每个后续任务必须从当时最新 `main` 创建短期功能分支，并严格限制在对应 Issue/PR 范围内。
