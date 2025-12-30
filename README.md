# 📚 Bookd

基于 Kotlin + Ktor 构建的现代化电子书管理系统，专为 NAS 和私有云环境设计，支持跨平台阅读进度同步。

## ✨ 功能特性

### 📖 书籍管理
- **智能扫描** - 自动扫描导入电子书（EPUB, PDF, TXT, MOBI, AZW3）
- **多源管理** - 支持配置多个书籍目录，独立控制启用/禁用
- **元数据提取** - 自动读取书籍标题、作者、封面等信息
- **封面管理** - 支持上传自定义封面或自动生成封面
- **标签系统** - 多标签分类管理，支持标签合并

### 📱 阅读功能
- **阅读进度同步** - 跨设备同步阅读位置（支持 EPUB CFI 定位）
- **书签管理** - 添加、编辑、删除书签，支持备注
- **阅读器设置** - 字体、字号、主题、行距等个性化配置
- **阅读历史** - 记录最近阅读书籍

### 👥 用户系统
- **三种角色** - 管理员、普通用户、访客
- **邀请码注册** - 管理员生成邀请码供用户注册
- **安全认证** - BCrypt 密码加密，Token 会话管理

### 🎨 管理界面
- **响应式 Web UI** - 支持桌面和移动端
- **文件浏览器** - 可视化浏览 NAS 目录
- **用户管理** - 查看用户列表、管理邀请码

## 🛠️ 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Kotlin 2.x + Ktor 3.x |
| 数据库 | PostgreSQL 16 + Exposed ORM |
| 依赖注入 | Koin |
| 容器化 | Docker + Docker Compose |
| 运行时 | Eclipse Temurin JRE 21 |

## 📄 支持格式

| 格式 | 扩展名 | 元数据提取 |
|------|--------|------------|
| EPUB | .epub | ✅ 完整支持 |
| PDF | .pdf | ✅ 完整支持 |
| TXT | .txt | ⚠️ 基础支持 |
| MOBI | .mobi | ⚠️ 基础支持 |
| AZW3 | .azw3 | ⚠️ 基础支持 |

---

## 🚀 快速开始

### 使用部署脚本（推荐）

```bash
./deploy.sh
```

交互式菜单选项：
1. 本地部署（首次使用）
2. 更新本地容器
3. 构建并推送到 Docker Hub
4. 清理多余镜像
5. 查看运行状态
6. 查看日志

### 手动部署

```bash
# 构建项目
./gradlew clean build -x test

# 构建镜像并启动
docker build -t bookd:local .
docker-compose up -d
```

### 首次设置

1. 访问 `http://localhost:7919`
2. 自动跳转到 `/setup` 创建管理员账号
3. 完成后登录系统

---

## 🐳 NAS 部署指南

### 群晖 DSM 部署

#### 方式一：Docker Compose（推荐）

1. **安装 Container Manager**（DSM 7.2+）或 Docker 套件

2. **创建项目目录**
   ```bash
   mkdir -p /volume1/docker/bookd
   cd /volume1/docker/bookd
   ```

3. **创建 docker-compose.yml**
   ```yaml
   services:
     postgres:
       image: postgres:16-alpine
       container_name: bookd-postgres
       environment:
         POSTGRES_DB: bookd
         POSTGRES_USER: bookd
         POSTGRES_PASSWORD: your_secure_password
       volumes:
         - ./postgres_data:/var/lib/postgresql/data
       restart: unless-stopped

     bookd-server:
       image: yourusername/bookd:latest  # 或使用本地构建的镜像
       container_name: bookd-server
       ports:
         - "7919:7919"
       environment:
         PORT: "7919"
         DATABASE_URL: "jdbc:postgresql://postgres:5432/bookd"
         DATABASE_USER: "bookd"
         DATABASE_PASSWORD: "your_secure_password"
       volumes:
         - ./covers:/app/covers
         - /volume1:/volume1:ro          # 挂载书籍目录（只读）
         - /volume2:/volume2:ro          # 可选：挂载其他卷
       depends_on:
         - postgres
       restart: unless-stopped
   ```

4. **启动服务**
   ```bash
   docker-compose up -d
   ```

5. **配置反向代理**（可选）
   - 在 DSM 控制面板 → 登录门户 → 高级 → 反向代理
   - 添加规则：外部 HTTPS → 内部 http://localhost:7919

#### 方式二：离线镜像部署

```bash
# 在开发机上
docker save bookd:local | gzip > bookd.tar.gz
scp bookd.tar.gz admin@nas-ip:/volume1/docker/

# 在 NAS 上
cd /volume1/docker
gunzip bookd.tar.gz
docker load -i bookd.tar
```

### 威联通 QTS 部署

1. **安装 Container Station**

2. **创建项目目录**
   ```bash
   mkdir -p /share/Container/bookd
   ```

3. **修改 docker-compose.yml 挂载路径**
   ```yaml
   volumes:
     - /share/Multimedia:/share/Multimedia:ro
     - /share/Books:/share/Books:ro
   ```

### UNRAID 部署

1. 在 Apps 中搜索 PostgreSQL 并安装
2. 创建 bookd 容器，配置：
   - 镜像：`yourusername/bookd:latest`
   - 端口：`7919:7919`
   - 环境变量：DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD
   - 路径映射：`/mnt/user/books:/books:ro`

---

## 🏗️ 多架构镜像构建

### 构建 AMD64/ARM64 双架构镜像

#### 前置条件

```bash
# 创建并使用 buildx 构建器
docker buildx create --name multiarch --use
docker buildx inspect --bootstrap
```

#### 构建并推送

```bash
# 构建项目
./gradlew clean build -x test

# 构建多架构镜像并推送到 Docker Hub
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t yourusername/bookd:latest \
  -t yourusername/bookd:1.0.0 \
  --push \
  .
```

#### 仅构建本地架构测试

```bash
# 构建当前架构镜像
docker buildx build --load -t bookd:local .
```

### 为不同 NAS 构建

| NAS 型号 | 架构 | 构建参数 |
|----------|------|----------|
| 群晖 x86 (DS920+, DS1621+) | amd64 | `--platform linux/amd64` |
| 群晖 ARM (DS220j, DS420j) | arm64 | `--platform linux/arm64` |
| 威联通 x86 | amd64 | `--platform linux/amd64` |
| 威联通 ARM | arm64 | `--platform linux/arm64` |

### 验证镜像架构

```bash
docker manifest inspect yourusername/bookd:latest
```

---

## 📡 API 参考

### 认证 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/auth/has-admin` | 检查是否存在管理员 |
| POST | `/api/auth/setup` | 首次设置创建管理员 |
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/logout` | 用户登出 |
| GET | `/api/auth/me` | 获取当前用户信息 |
| POST | `/api/auth/register/guest` | 访客注册 |
| POST | `/api/auth/register/user` | 邀请码注册 |

### 用户管理 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/users` | 获取用户列表（管理员） |
| DELETE | `/api/users/{id}` | 删除用户 |
| POST | `/api/users/invite-tokens` | 创建邀请码 |
| GET | `/api/users/invite-tokens` | 获取邀请码列表 |

### 书籍 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/books` | 获取书籍列表 |
| GET | `/api/books/{id}` | 获取书籍详情 |
| GET | `/api/books/count` | 获取书籍总数 |
| PUT | `/api/books/{id}/metadata` | 更新书籍元数据 |
| POST | `/api/books/{id}/cover` | 上传书籍封面 |
| POST | `/api/books/{id}/generate-cover` | 生成书籍封面 |

### 书籍源 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/sources` | 获取书籍源列表 |
| POST | `/api/sources` | 添加书籍源 |
| DELETE | `/api/sources/{id}` | 删除书籍源 |
| POST | `/api/sources/{id}/toggle` | 切换启用状态 |

### 扫描 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/scan/status` | 获取扫描状态 |
| POST | `/api/scan/all` | 扫描所有书籍源 |
| POST | `/api/scan/source/{id}` | 扫描指定书籍源 |

### 标签 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/tags` | 获取所有标签 |
| POST | `/api/tags/merge` | 合并标签 |
| GET | `/api/tags/book/{bookId}` | 获取书籍标签 |
| POST | `/api/tags/book/{bookId}` | 添加书籍标签 |
| DELETE | `/api/tags/book/{bookId}/{tagId}` | 移除书籍标签 |
| DELETE | `/api/tags/{tagId}` | 删除标签 |
| GET | `/api/tags/{tagId}/books` | 获取标签下的书籍 |
| POST | `/api/tags/auto-tag/book/{bookId}` | 自动标签单本书 |
| POST | `/api/tags/auto-tag/all` | 自动标签所有书籍 |

### 阅读进度 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/books/{bookId}/progress` | 获取阅读进度 |
| PUT | `/api/books/{bookId}/progress` | 更新阅读进度 |
| GET | `/api/user/reading-history` | 获取阅读历史 |

### 书签 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/books/{bookId}/bookmarks` | 获取书籍书签 |
| POST | `/api/books/{bookId}/bookmarks` | 添加书签 |
| PUT | `/api/bookmarks/{id}` | 更新书签 |
| DELETE | `/api/bookmarks/{id}` | 删除书签 |
| GET | `/api/user/bookmarks` | 获取用户所有书签 |

### 阅读器设置 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/user/reader-settings` | 获取阅读器设置 |
| PUT | `/api/user/reader-settings` | 更新阅读器设置 |
| PATCH | `/api/user/reader-settings` | 部分更新设置 |

### 文件系统 API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/filesystem/list` | 列出目录内容 |
| GET | `/api/filesystem/validate` | 验证路径是否有效 |
| GET | `/api/filesystem/roots` | 获取根目录列表 |

### 健康检查

| Method | Path | 说明 |
|--------|------|------|
| GET | `/health` | 健康检查 |
| GET | `/api/health` | API 健康检查 |

---

## ⚙️ 配置参考

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `PORT` | 7919 | 服务端口 |
| `DATABASE_URL` | jdbc:postgresql://localhost:5432/bookd | 数据库连接 |
| `DATABASE_USER` | bookd | 数据库用户 |
| `DATABASE_PASSWORD` | bookd | 数据库密码 |
| `TZ` | Asia/Shanghai | 时区 |

### 目录挂载建议

```yaml
volumes:
  # 数据持久化（必须）
  - ./covers:/app/covers
  
  # 书籍目录（只读挂载）
  - /your/books/path:/books:ro
```

---

## 🔧 故障排查

### 常见问题

**容器无法启动**
```bash
docker-compose logs bookd-server
```

**数据库连接失败**
```bash
docker exec -it bookd-postgres psql -U bookd -d bookd -c "SELECT 1;"
```

**扫描不到书籍**
1. 检查目录挂载是否正确
2. 确认容器内可访问书籍目录：`docker exec -it bookd-server ls /books`
3. 检查文件格式是否支持

**新增字段报错**
- 确保使用 `SchemaUtils.createMissingTablesAndColumns` 自动迁移

---

## 📄 License

MIT License

---

**版本**: 0.1.1 | **更新**: 2025-12-30
