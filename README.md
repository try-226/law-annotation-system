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

两个 volume 必须分别备份：

- `mongo_data` 中的数据库数据使用 `mongodump` / `mongorestore` 做逻辑备份与恢复；
- `upload_data` 中的文件不属于 MongoDB，`mongodump` / `mongorestore` 不会处理它们，必须单独纳入宿主机或部署平台的文件备份；
- MongoDB 恢复成功不代表 `upload_data` 已恢复。完整系统恢复必须同时核对数据库备份和上传文件备份。

> **破坏性操作警告**：`docker compose down -v` 会同时删除 `mongo_data` 和 `upload_data`，从而清空数据库及上传数据。它只能在已明确确认的开发/课程演示环境中用于彻底重置，或在已经确认数据可丢弃、数据库与上传文件均已完成有效备份的场景中使用；它绝不是普通重启、生产恢复或安全重置命令。

纯净演示环境只能在确认数据可丢弃后执行上述破坏性清空，再配置 Demo 开关并重新 `up -d --build`。生产数据恢复应使用备份，而不是删除 volume。

### 7. 备份与恢复

#### 7.1 MongoDB 数据备份

备份目录 `backups/` 已被 Git 忽略。以下命令使用 Mongo 容器内自带的 `mongodump`，宿主机无需安装 MongoDB Tools。当前 MongoDB 为 standalone；为了获得更可靠的跨业务集合一致性备份，必须先进入维护窗口并停止应用写入，不应把应用持续写入期间的 `mongodump` 描述为事务级 point-in-time backup。

Windows、macOS 和 Linux 均先停止应用，只保留 Mongo，并确认其为 healthy：

```powershell
docker compose stop frontend backend
docker compose up -d mongo
docker compose ps
```

Windows PowerShell：

```powershell
New-Item -ItemType Directory -Force backups
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupFile = "./backups/law_annotation-$stamp.archive.gz"
if (Test-Path -LiteralPath $backupFile) {
    throw "Backup file already exists: $backupFile"
}
docker compose exec -T mongo sh -c 'mongodump --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --db "$MONGO_INITDB_DATABASE" --archive=/tmp/law_annotation.archive.gz --gzip'
docker compose cp mongo:/tmp/law_annotation.archive.gz "$backupFile"
```

macOS/Linux：

```bash
mkdir -p backups
stamp="$(date +%Y%m%d-%H%M%S)"
backup_file="./backups/law_annotation-${stamp}.archive.gz"
if [ -e "$backup_file" ]; then
  echo "Backup file already exists: $backup_file" >&2
  exit 1
fi
docker compose exec -T mongo sh -c 'mongodump --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --db "$MONGO_INITDB_DATABASE" --archive=/tmp/law_annotation.archive.gz --gzip'
docker compose cp mongo:/tmp/law_annotation.archive.gz "$backup_file"
```

备份完成后先启动 Backend：

```powershell
docker compose up -d backend
docker compose ps
docker compose logs backend
```

必须等待并确认 `backend` 显示为 healthy；若尚未 healthy，继续检查状态和日志，不要启动 Frontend。确认 Backend healthy 后再执行：

```powershell
docker compose up -d frontend
docker compose ps
```

#### 7.2 恢复前的安全停机顺序

First Admin 和可选 Demo Seed 都在 Backend 启动时执行。恢复期间如果先运行完整的 `docker compose up -d`，Backend 可能在目标库中提前写入管理员和 Demo 初始化数据，随后恢复就可能发生 `_id`、唯一索引冲突或新旧数据混合。

恢复前先停止应用写入，并且在恢复完成以前只启动 Mongo：

```powershell
docker compose stop frontend backend
docker compose up -d mongo
docker compose ps
```

必须等 `mongo` 显示为 healthy。不要在 Mongo 恢复完成前启动 `backend` 或 `frontend`，也不要依赖临时修改 `APP_INIT_ADMIN_ENABLED` 或业务代码来规避 Bootstrap。

#### 7.3 恢复到真正的空数据库

普通 `mongorestore` 适用于已经确认没有业务集合的目标数据库。先执行以下只读检查；输出必须为 `0` 才能把目标业务数据库视为空库：

```powershell
docker compose exec -T mongo sh -c 'mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "const target = db.getSiblingDB(\"$MONGO_INITDB_DATABASE\"); print(target.getCollectionNames().length)"'
```

如果输出不为 `0`，立即停止，不要把普通 `mongorestore` 当作覆盖恢复。应选择新的空 Mongo volume / 隔离 Compose project，或按下一节在人工确认后执行破坏性替换。

确认空库后，指定要恢复的备份文件并执行：

```powershell
$archive = "law_annotation-20260829-120000.archive.gz" # 替换为实际文件名
docker compose cp "./backups/$archive" mongo:/tmp/law_annotation.restore.archive.gz
docker compose exec -T mongo sh -c 'mongorestore --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive=/tmp/law_annotation.restore.archive.gz --gzip --stopOnError'
```

macOS/Linux 使用同样的 Compose 命令，仅将变量赋值改为：

```bash
archive="law_annotation-20260829-120000.archive.gz" # 替换为实际文件名
docker compose cp "./backups/${archive}" mongo:/tmp/law_annotation.restore.archive.gz
docker compose exec -T mongo sh -c 'mongorestore --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive=/tmp/law_annotation.restore.archive.gz --gzip --stopOnError'
```

#### 7.4 覆盖已有数据库与隔离恢复验证

普通 `mongorestore` 不会先清空已有数据，因此不等于完整覆盖恢复。优先使用新的 Compose project 创建一套空的 `mongo_data`，在不破坏原 volume 的情况下验证备份能否成功恢复；后续验证命令必须始终使用同一个 project 名：

```powershell
$archive = ".\backups\law_annotation-YYYYMMDD-HHMMSS.archive.gz" # 替换为准备验证的真实备份文件
if (-not (Test-Path -LiteralPath $archive)) {
    throw "Backup file does not exist: $archive"
}
docker compose down
$restoreProject = "law-annotation-restore"
docker compose -p $restoreProject up -d mongo
docker compose -p $restoreProject ps
docker compose -p $restoreProject cp "$archive" mongo:/tmp/law_annotation.restore.archive.gz
docker compose -p $restoreProject exec -T mongo sh -c 'mongorestore --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive=/tmp/law_annotation.restore.archive.gz --gzip --stopOnError'
```

上述 `docker compose down` 不带 `-v`，原项目的 named volumes 会保留。新 project 使用独立的 `law-annotation-restore_mongo_data`；隔离 project 恢复成功只证明备份可恢复，不会自动替换正式 Compose project 的 `mongo_data` 或 `upload_data`，不会切换正式访问入口，也不代表正式环境恢复已经完成。

如果决定把隔离恢复结果用于正式环境，部署人员仍必须人工确认最终 Compose project、Mongo volume、`upload_data` 对应关系、停机窗口、部署入口和数据切换方案。本项目不提供自动 volume 重命名、替换或复制流程。在隔离环境继续验证时，Backend、Frontend、`upload_data` 恢复及后续维护命令都必须继续带 `-p law-annotation-restore`。

> **【破坏性操作】覆盖当前数据库**：只有在已验证备份、已另行备份当前数据库并经人工确认允许替换后，才可在上面的 `mongorestore` 命令中加入 `--drop`。它会删除备份中涉及的目标集合后再恢复，但不会自动删除备份中不存在的额外集合，因此仍不等同于无条件清空整个数据库。禁止把 `--drop` 放入普通默认恢复命令，也不要在生产排障时静默执行。

#### 7.5 恢复后验证和启动

在 Backend 仍停止时，先只读检查集合及关键记录数量：

```powershell
docker compose exec -T mongo sh -c 'mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "const target = db.getSiblingDB(\"$MONGO_INITDB_DATABASE\"); printjson({collections: target.getCollectionNames().sort(), users: target.users.countDocuments({}), laws: target.laws.countDocuments({})})"'
```

如果使用隔离 project，将本节所有命令改为 `docker compose -p law-annotation-restore ...`。数据核对无误后按顺序启动：

```powershell
docker compose up -d backend
docker compose ps
docker compose logs backend
```

运行 `docker compose ps`，确认 Backend 已显示为 healthy；若尚未 healthy，等待后复查并使用 `docker compose logs backend` 排查，不要提前启动 Frontend。确认 Backend healthy 后，再单独执行：

```powershell
docker compose up -d frontend
docker compose ps
```

最终确认三个服务均为 healthy，并检查：

```powershell
Invoke-WebRequest http://localhost:8081/health
Invoke-RestMethod http://localhost:8081/api/actuator/health
Invoke-RestMethod http://localhost:8081/api/auth/csrf
```

如果备份本身已经包含 ADMIN 或 Demo 数据，现有幂等 Bootstrap 会识别已有数据，不会覆盖已恢复的管理员密码或人工修改过的 Demo Law；无需手工删除 bootstrap 初始化状态。

#### 7.6 `upload_data` 文件备份

`mongodump` 不包含 `upload_data`。部署方应优先把该 named volume 纳入宿主机或平台备份体系。也可以在停止 Backend 写入后，使用一次性 Backend 容器仅执行读取和打包；以下命令不会启动应用，也不会启动依赖服务：

Windows PowerShell：

```powershell
New-Item -ItemType Directory -Force backups
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$uploadArchive = "upload_data-$stamp.tar.gz"
$uploadBackupFile = "./backups/$uploadArchive"
if (Test-Path -LiteralPath $uploadBackupFile) {
    throw "Backup file already exists: $uploadBackupFile"
}
docker compose stop backend
docker compose run --rm --no-deps -e ARCHIVE_NAME="$uploadArchive" -v "${PWD}/backups:/backup" --entrypoint sh backend -c 'tar -czf "/backup/$ARCHIVE_NAME" -C /app/data/uploads .'
```

macOS/Linux：

```bash
mkdir -p backups
stamp="$(date +%Y%m%d-%H%M%S)"
upload_archive="upload_data-${stamp}.tar.gz"
upload_backup_file="./backups/${upload_archive}"
if [ -e "$upload_backup_file" ]; then
  echo "Backup file already exists: $upload_backup_file" >&2
  exit 1
fi
docker compose stop backend
docker compose run --rm --no-deps -e ARCHIVE_NAME="${upload_archive}" -v "$(pwd)/backups:/backup" --entrypoint sh backend -c 'tar -czf "/backup/$ARCHIVE_NAME" -C /app/data/uploads .'
```

恢复 `upload_data` 时应使用经过验证的对应文件备份，并在 Backend 停止的情况下写入目标 volume。以下示例只适用于已经确认是空的新目标 volume；文件名替换为实际备份：

Windows PowerShell：

```powershell
$uploadArchive = "upload_data-20260829-120000.tar.gz"
docker compose run --rm --no-deps -e ARCHIVE_NAME="$uploadArchive" -v "${PWD}/backups:/backup:ro" --entrypoint sh backend -c 'tar -xzf "/backup/$ARCHIVE_NAME" -C /app/data/uploads'
```

macOS/Linux：

```bash
upload_archive="upload_data-20260829-120000.tar.gz"
docker compose run --rm --no-deps -e ARCHIVE_NAME="${upload_archive}" -v "$(pwd)/backups:/backup:ro" --entrypoint sh backend -c 'tar -xzf "/backup/$ARCHIVE_NAME" -C /app/data/uploads'
```

如果使用隔离 project，也必须为上述命令增加同一个 `-p law-annotation-restore`。向非空 volume 解包可能覆盖同名文件并保留备份中不存在的额外文件，因此完整替换应优先恢复到新的空 `upload_data`，或交由部署平台执行经过人工确认的文件级恢复。本项目不提供自动清空上传目录的命令。

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
