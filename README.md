# 法律条文标注系统

法律条文标注系统 V1.5 是一个面向完整法律文件的在线结构化标注平台。本仓库当前处于工程基线建设阶段，已提供后端骨架、前端基础工程、共享枚举、统一响应/异常结构和最小 MongoDB 配置，尚未实现登录、法律、任务、标注、审核、修订等业务。

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

开发服务器默认访问地址为 `http://localhost:5173`。`frontend/.env.example` 提供 `VITE_API_BASE_URL` 示例；未创建本地环境文件时，前端 API 地址默认使用 `http://localhost:8080`。

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

后端默认监听 `8080`。由于 PR01 不提供业务 Controller，因此不会新增业务 API。

## 开发基线

当前业务 Source of Truth 为两份 2026-08-19 V1.5 文档：

1. 《法律条文标注系统 Codex 项目上下文与开发边界》
2. 《法律条文标注系统 需求规格说明书与团队开发对接文档》

若两份 V1.5 文档存在业务语义冲突，以《需求规格说明书与团队开发对接文档》为准。每个后续任务必须从当时最新 `main` 创建短期功能分支，并严格限制在对应 Issue/PR 范围内。
