# 📚 Bookd - 电子书管理系统

基于 Kotlin + Ktor 的现代化电子书管理系统，专为 NAS 环境设计。

## ✨ 功能特性

### 📖 核心功能
- **智能扫描** - 自动扫描导入电子书（EPUB, PDF, TXT, MOBI, AZW3）
- **多源管理** - 支持配置多个书籍目录，独立控制启用/禁用
- **元数据提取** - 自动读取书籍标题、作者、封面等信息
- **文件浏览器** - 可视化浏览 NAS 目录，快速添加书籍源

### 👥 用户系统
- **三种角色** - 管理员、普通用户、访客
- **邀请码机制** - 管理员生成邀请码供用户注册
- **权限分离** - 不同角色访问不同功能模块
- **安全认证** - BCrypt 密码加密，Token 会话管理

### 🎨 管理界面
- **现代化 UI** - 响应式设计，支持移动端
- **实时统计** - 书籍总数、书籍源数量
- **批量操作** - 扫描所有书籍源、批量导入
- **用户管理** - 查看用户列表、管理邀请码

## 🚀 快速开始

### 使用部署脚本（推荐）

```bash
# 运行交互式部署工具
./deploy.sh

# 选择操作：
# 1) 本地部署（首次使用）
# 2) 更新本地容器（代码修改后）
# 3) 构建并推送到 Docker Hub
# 4) 清理多余镜像
# 5) 查看运行状态
# 6) 查看日志
```

### 手动部署

```bash
# 1. 构建项目
./gradlew clean build -x test

# 2. 构建镜像
docker build -t bookd:local .

# 3. 启动服务
docker-compose up -d

# 4. 查看日志
docker-compose logs -f bookd-server
```

## 🔑 默认账号

首次部署后使用默认管理员账号登录：

- **用户名**: `zuiren233`
- **密码**: `Sy5201314`

> ⚠️ 生产环境请及时修改默认密码

## 📍 访问地址

- 🔐 **登录页面**: http://localhost:7919/login
- 🎨 **管理后台**: http://localhost:7919/admin
- 📚 **书籍 API**: http://localhost:7919/api/books
- ❤️ **健康检查**: http://localhost:7919/api/health

## 🛠️ 技术栈

- **后端**: Kotlin 2.2.21 + Ktor 3.3.2
- **数据库**: PostgreSQL 16 + Exposed ORM
- **依赖注入**: Koin
- **密码加密**: BCrypt
- **容器化**: Docker + Docker Compose

## ⚙️ 配置说明

### 环境变量

在 `docker-compose.yml` 中配置：

```yaml
environment:
  PORT: "7919"                    # 服务端口
  DATABASE_URL: "jdbc:..."        # 数据库连接地址
  DATABASE_USER: "bookd"          # 数据库用户
  DATABASE_PASSWORD: "bookd"      # 数据库密码
```

### NAS 目录挂载

编辑 `docker-compose.yml` 中的 volumes 部分：

```yaml
volumes:
  # 群晖 NAS
  - /volume1:/volume1:ro
  
  # 威联通 NAS
  - /share:/share:ro
  
  # Mac/Linux 开发环境
  - /Users:/Users:ro
```

> 💡 挂载整个 NAS 目录后，在 Web 界面添加书籍源时选择具体子目录

### 端口修改

如果 7919 端口被占用：

```yaml
ports:
  - "8080:7919"  # 外部8080映射到容器7919
```

## 📖 使用指南

### 1. 登录系统

访问 http://localhost:7919/login 使用默认管理员账号登录

### 2. 添加书籍源

1. 进入管理后台 "📚 书籍管理" 标签
2. 输入源名称（如：个人收藏、技术书籍）
3. 点击 "📂 浏览文件夹" 选择 NAS 目录
4. 点击 "➕ 添加书籍源"

### 3. 扫描导入书籍

- **单个源扫描**: 点击书籍源右侧的 "🔍 扫描" 按钮
- **批量扫描**: 点击 "🔍 扫描所有书籍源" 按钮

### 4. 用户管理

1. 切换到 "👥 用户管理" 标签
2. 生成邀请码供他人注册
3. 查看用户列表，删除不需要的用户

## 🐳 Docker 部署

### 本地开发

```bash
# 启动
./deploy.sh  # 选择 1

# 更新代码后重新部署
./deploy.sh  # 选择 2
```

### NAS 部署

#### 方式一：Docker Hub

```bash
# 开发机构建并推送
./deploy.sh  # 选择 3

# NAS 上拉取
docker pull yourusername/bookd:latest
docker-compose up -d
```

#### 方式二：离线部署

```bash
# 开发机导出
docker save bookd:local -o bookd.tar
gzip bookd.tar

# 传输到 NAS
scp bookd.tar.gz admin@nas-ip:/volume1/docker/

# NAS 导入
gunzip bookd.tar.gz
docker load -i bookd.tar
docker-compose up -d
```

## 📝 常用命令

```bash
# 查看运行状态
docker-compose ps

# 查看实时日志
docker-compose logs -f bookd-server

# 重启服务
docker-compose restart

# 停止服务
docker-compose down

# 进入容器调试
docker exec -it bookd-server sh

# 清理镜像
./deploy.sh  # 选择 4
```

## 🎯 API 示例

### 认证相关

```bash
# 登录
curl -X POST http://localhost:7919/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"zuiren233","password":"Sy5201314"}'

# 获取当前用户信息
curl http://localhost:7919/api/auth/me \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 书籍管理

```bash
# 添加书籍源
curl -X POST http://localhost:7919/api/sources \
  -H "Content-Type: application/json" \
  -d '{"name":"我的书库","path":"/volume1/books"}'

# 扫描书籍源
curl -X POST http://localhost:7919/api/scan/source/1

# 查看所有书籍
curl http://localhost:7919/api/books?page=1&size=20
```

## 🔧 故障排查

### 容器无法启动

```bash
# 查看日志
docker-compose logs bookd-server

# 检查端口占用
lsof -i :7919

# 重新构建
./deploy.sh  # 选择 2
```

### 数据库连接失败

```bash
# 检查数据库状态
docker-compose ps postgres

# 查看数据库日志
docker-compose logs postgres

# 测试数据库连接
docker exec -it bookd-postgres psql -U bookd -d bookd -c "SELECT 1;"
```

### 扫描不到书籍

1. 检查 docker-compose.yml 中的目录挂载是否正确
2. 确认容器内能访问到书籍目录
3. 查看书籍文件格式是否支持（EPUB/PDF/TXT/MOBI/AZW3）

## 📊 项目状态

### ✅ 已完成
- 书籍源管理（CRUD、启用/禁用）
- 书籍扫描导入（多格式支持）
- 用户认证系统（三种角色）
- Web 管理界面
- Docker 容器化部署
- 文件浏览器

### 🚧 计划中
- Compose 跨平台阅读器
- EPUB 在线阅读
- 阅读进度同步
- 书籍封面管理
- 高级元数据编辑

## 📄 支持格式

| 格式 | 扩展名 | 元数据提取 | 状态 |
|------|--------|------------|------|
| EPUB | .epub | ✅ 支持 | ✅ 完成 |
| PDF | .pdf | ✅ 支持 | ✅ 完成 |
| TXT | .txt | ⚠️ 有限 | ✅ 完成 |
| MOBI | .mobi | ⚠️ 有限 | ✅ 完成 |
| AZW3 | .azw3 | ⚠️ 有限 | ✅ 完成 |

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 License

MIT License

---

**版本**: 1.0.0 | **更新**: 2025-12-29
