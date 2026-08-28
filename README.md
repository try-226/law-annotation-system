# 法律条文标注系统

法律条文标注系统 V1.5 是一个面向完整法律文件的在线结构化标注平台，覆盖法律维护、任务、标注、审核、修订、历史、检索和导出等流程。仓库同时提供本地开发方式和基于 Docker Compose 的完整部署入口。

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
│   ├── Dockerfile            # Vue 构建与 Nginx runtime
│   ├── nginx/                # SPA 与 /api 反向代理配置
│   └── vite.config.ts
├── backend/                  # Spring Boot 后端工程及容器构建文件
├── docker-compose.yml        # Mongo、Backend、Frontend/Nginx
├── .env.example              # Compose 环境变量示例
├── AGENTS.md                 # 后续开发协作约束
└── README.md
```

## Docker 一键部署

完整部署只要求 Git、Docker Engine/Desktop 和 Docker Compose v2，不依赖宿主机 JDK、Maven、Node.js 或 MongoDB。容器入口为 `http://localhost:8081`。

### 1. 准备环境变量

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

macOS/Linux：

```bash
cp .env.example .env
```

启动前编辑 `.env`，至少替换 MongoDB、首次管理员和演示标注员示例密码。示例值只适合课程演示，禁止直接用于生产环境；密码若含有 `@`、`:`、`/`、`?`、`#` 等 URI 保留字符，必须在 `MONGODB_URI` 中进行 URL 编码。`.env` 已被 Git 忽略。

| 变量 | 用途 | 默认/示例 |
| --- | --- | --- |
| `SERVER_PORT` | Backend 容器内部端口及 Nginx upstream | `8080` |
| `FRONTEND_PORT` | Nginx 宿主机入口 | `8081` |
| `MONGO_PORT` | MongoDB 宿主机 loopback 开发端口 | `27017` |
| `MONGO_INITDB_ROOT_USERNAME` | MongoDB 初始化账号 | 课程示例 `admin` |
| `MONGO_INITDB_ROOT_PASSWORD` | MongoDB 初始化密码 | 必须替换 |
| `MONGO_INITDB_DATABASE` | 业务数据库名 | `law_annotation` |
| `MONGODB_URI` | Backend 认证连接串，Docker host 必须为 `mongo` | 必填 |
| `APP_INIT_ADMIN_ENABLED` | 是否启用首次管理员初始化 | 示例为 `true` |
| `APP_INIT_ADMIN_USERNAME` | 首次管理员账号 | 仅初始化输入 |
| `APP_INIT_ADMIN_PASSWORD` | 首次管理员密码 | 必须替换演示值 |
| `APP_INIT_ADMIN_NAME` | 首次管理员姓名 | 仅初始化输入 |
| `APP_DEMO_DATA_ENABLED` | 是否创建可选 Demo 数据 | 默认 `false` |
| `APP_DEMO_ANNOTATOR_USERNAME` | Demo 标注员账号 | 启用 Demo 时必填 |
| `APP_DEMO_ANNOTATOR_PASSWORD` | Demo 标注员首次密码 | 启用 Demo 时必填且必须替换 |
| `APP_UPLOAD_DIR` | 未来上传业务使用的应用配置预留合同；当前容器路径固定 | `/app/data/uploads` |
| `APP_AUTH_SESSION_TIMEOUT` | 服务端 Session 超时 | 默认 `30m` |
| `APP_CORS_ALLOWED_ORIGINS` | 本地开发允许携带凭据的精确来源 | `http://localhost:5173` |

### 2. 构建并启动

```powershell
docker compose config
docker compose up -d --build
docker compose ps
```

启动顺序由健康检查确定：Mongo healthy 后启动 Backend，Backend health 为 `UP` 后启动 Frontend/Nginx。浏览器统一访问 `http://localhost:8081`；Backend 的 `8080` 不发布到宿主机。

查看日志：

```powershell
docker compose logs -f mongo backend frontend
```

### 3. API、SPA 与会话

浏览器请求 `/api/**`。Nginx 会去除 `/api` 前缀后转发到 Backend，例如：

```text
http://localhost:8081/api/auth/csrf  ->  backend:8080/auth/csrf
```

前端页面路由使用 SPA fallback，直接刷新 `/laws/**`、`/tasks/**` 等深层路径仍返回 `index.html`。API 与页面同源，`JSESSIONID` 和 CSRF 会话均通过 `localhost:8081` 使用；生产部署不需要开放 Backend 端口或配置通配 CORS。

### 4. First Admin

全新数据库首次启动时，如果不存在任何 `ADMIN` 且 `APP_INIT_ADMIN_ENABLED=true`，Backend 使用 `APP_INIT_ADMIN_*` 创建一个管理员。索引初始化完成后先执行 First Admin，再执行 Demo Seed。只要已存在任意管理员，重启就不会重复创建，也不会用环境密码覆盖已修改的密码。

### 5. 可选 Demo Seed

`APP_DEMO_DATA_ENABLED=false` 时完全不创建 Demo 数据。设为 `true` 时幂等创建：

- 一个启用的 `ANNOTATOR`；
- 一部名称明确标识“非真实法规”的两章八条演示法律；
- 对应的一个初始内容版本 C1。

Demo Seed 不创建 Task、Submission、ReviewRound、AnnotationVersion 或 Revision。系统使用 bootstrap 自有的稳定初始化标识记录演示法律 ID；重启不会重置演示账号密码、覆盖人工修改过的演示法律，也不会因法律改名而创建第二部法律。若稳定标识指向的法律记录异常缺失，Backend 会拒绝静默重建并终止启动，管理员应先核对数据库与备份中的初始化状态。若演示账号已存在但角色错误或已停用，Backend 同样会明确启动失败，避免静默覆盖人工状态。

### 6. 停止、重启与数据卷

普通停止会保留数据：

```powershell
docker compose down
```

重新启动：

```powershell
docker compose up -d
```

`mongo_data` 保存 MongoDB 数据，`upload_data` 保存上传目录。当前容器上传持久化目录固定为 `/app/data/uploads`，Compose volume target 与该路径一致；`APP_UPLOAD_DIR` 是未来上传业务的应用配置预留，若未来允许动态修改目录，必须同步调整 Compose volume mount。普通 `down/up` 不删除 named volume。

> **破坏性操作警告**：`docker compose down -v` 会删除 `mongo_data` 和 `upload_data`，从而清空数据库及上传数据。它只能在已明确确认的开发/课程演示环境中用于彻底清空，绝不是普通重启、生产恢复或安全重置命令。

纯净演示环境只能在确认数据可丢弃后执行上述破坏性清空，再配置 Demo 开关并重新 `up -d --build`。生产数据恢复应使用备份，而不是删除 volume。

### 7. MongoDB 备份与恢复

备份目录 `backups/` 已被 Git 忽略。以下命令使用 Mongo 容器内自带的 `mongodump`，宿主机无需安装 MongoDB Tools。

Windows PowerShell：

```powershell
New-Item -ItemType Directory -Force backups
docker compose exec -T mongo sh -c 'mongodump --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --db "$MONGO_INITDB_DATABASE" --archive=/tmp/law_annotation.archive.gz --gzip'
docker compose cp mongo:/tmp/law_annotation.archive.gz ./backups/law_annotation.archive.gz
```

macOS/Linux：

```bash
mkdir -p backups
docker compose exec -T mongo sh -c 'mongodump --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --db "$MONGO_INITDB_DATABASE" --archive=/tmp/law_annotation.archive.gz --gzip'
docker compose cp mongo:/tmp/law_annotation.archive.gz ./backups/law_annotation.archive.gz
```

默认恢复到空数据库：

```powershell
docker compose cp ./backups/law_annotation.archive.gz mongo:/tmp/law_annotation.archive.gz
docker compose exec -T mongo sh -c 'mongorestore --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive=/tmp/law_annotation.archive.gz --gzip --stopOnError'
```

`mongorestore --drop` 会在恢复前删除目标集合，是破坏性替换操作。只有确认目标数据库可被覆盖且备份已验证后才能人工加入 `--drop`；不要在普通恢复或生产排障中默认使用。

### 8. 常见问题

- 端口占用：修改 `.env` 中的 `FRONTEND_PORT` 或 `MONGO_PORT`，Backend 内部端口通常保持 `8080`。
- Mongo 认证失败：确认初始化账号、密码、数据库与 `MONGODB_URI` 一致；已有 volume 不会因修改初始化变量而重建账号。
- 服务 unhealthy：运行 `docker compose ps` 和 `docker compose logs backend`，Backend `/actuator/health` 会在 Mongo 不可用时报告非 `UP`。
- 镜像或依赖下载失败：检查 Docker daemon 的网络和 registry 配置；仓库不包含个人代理或私有镜像地址。
- API 404：确认浏览器使用 `/api/...`，且 Nginx `proxy_pass` 的尾部 `/` 未被移除。
- 深层页面 404：确认请求进入 Nginx 的 SPA fallback，而不是直接访问 Backend。
- Demo Seed 启动失败并提示稳定初始化状态指向的法律不存在：不要删除初始化状态或自动新建演示法律；先核对 MongoDB 数据和有效备份，确认原演示 Law 的去向后再人工处理数据一致性。

## 本地开发

本地开发需要 JDK 21、Node.js 20.19+ 和 Docker Desktop/Compose；无需全局 Maven。可以只启动 Mongo：

```powershell
docker compose up -d mongo
```

Docker `.env.example` 使用服务名 `mongo`。后端在宿主机运行时，需在当前终端提供使用 `localhost` 的连接串：

```powershell
$env:MONGODB_URI = "mongodb://admin:admin123@localhost:27017/law_annotation?authSource=admin"
$env:SERVER_PORT = "8080"
```

## 安装与启动前端

```bash
cd frontend
npm ci
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

后端默认监听 `8080`。

## 认证与用户管理

系统使用 Spring Security 服务端 Session，不使用 JWT、Refresh Token 或第三方登录。浏览器先请求 `GET /auth/csrf`，保存返回的 Session Cookie，并在后续 `POST`、`PATCH`、`DELETE` 请求中通过返回的 `headerName` 携带 CSRF Token。前端 Axios 公共实例已启用 Cookie 凭据。

认证接口包括登录、退出、当前用户、修改姓名和修改密码；`/users/**` 仅允许 `ADMIN` 访问，提供分页查询、创建、改名、重置密码、启停和有限删除。登录账号通过 `normalizedAccount` 实现大小写不敏感唯一，密码只保存 BCrypt 哈希。

本地 Vite 默认访问 `http://localhost:5173`，并将 `/api/*` 转发至宿主机 Backend `http://localhost:8080`；完整 Docker 部署则统一使用 `http://localhost:8081`。

## 开发基线

当前业务 Source of Truth 为两份 2026-08-19 V1.5 文档：

1. 《法律条文标注系统 Codex 项目上下文与开发边界》
2. 《法律条文标注系统 需求规格说明书与团队开发对接文档》

若两份 V1.5 文档存在业务语义冲突，以《需求规格说明书与团队开发对接文档》为准。每个后续任务必须从当时最新 `main` 创建短期功能分支，并严格限制在对应 Issue/PR 范围内。
