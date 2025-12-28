# 📚 Bookd - 电子书管理系统

基于 Kotlin + Ktor 的现代化电子书管理系统，专为 NAS 环境设计。

## ✨ 功能特性

- 📖 **智能扫描** - 自动扫描导入电子书（EPUB, PDF, TXT, MOBI, AZW3）
- 📁 **多源管理** - 支持配置多个书籍目录，独立控制
- 🎨 **Web 管理** - 现代化管理界面，实时查看书籍统计
- 🐳 **容器化部署** - Docker 一键部署，适配 NAS 环境
- 🗄️ **数据持久化** - PostgreSQL 数据库可靠存储

## 🚀 快速开始

### Docker 部署（推荐）

```bash
# 一键部署
./deploy.sh

# 访问管理界面
open http://localhost:7919/
```

### 手动部署

```bash
# 1. 构建项目
./gradlew clean shadowJar

# 2. 启动服务
docker-compose up -d

# 3. 查看日志
docker-compose logs -f
```

## 📍 访问地址

- 🎨 **管理界面**: http://localhost:7919/
- 📚 **书籍列表**: http://localhost:7919/api/books
- ❤️ **健康检查**: http://localhost:7919/health

## 🛠️ 技术栈

- **Kotlin** 2.2.21 + **Ktor** 3.3.2
- **PostgreSQL** 16 + **Exposed** ORM
- **Koin** 依赖注入
- **Docker** 容器化

## ⚙️ 配置说明

### NAS 部署配置

编辑 `docker-compose.yml`，挂载你的书籍目录：

```yaml
volumes:
  # 挂载整个 NAS 目录，在 Web 界面选择子目录
  - /volume1:/nas:ro
```

在 Web 界面添加书籍源时，使用容器内路径如：`/nas/books`

### 端口配置

默认端口 `7919`，修改方式：

```yaml
environment:
  PORT: "8080"
ports:
  - "8080:8080"
```

## 📖 详细文档

- [NAS 部署指南](NAS_DEPLOYMENT.md) - Docker Hub 和离线部署

## 📦 Docker 镜像

### 构建多架构镜像

```bash
# 构建 AMD64 架构（x86 服务器）
docker buildx build --platform linux/amd64 -t bookd:amd64 .

# 构建 ARM64 架构（Mac M1/M2）
docker buildx build --platform linux/arm64 -t bookd:arm64 .
```

### 推送到 Docker Hub

```bash
docker login
docker tag bookd:amd64 yourusername/bookd:latest
docker push yourusername/bookd:latest
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
```

## 🎯 使用示例

```bash
# 添加书籍源
curl -X POST http://localhost:7919/api/sources \
  -H "Content-Type: application/json" \
  -d '{"name":"我的书库","path":"/nas/books"}'

# 扫描书籍
curl -X POST http://localhost:7919/api/scan/all

# 查看书籍
curl http://localhost:7919/api/books
```

## 📊 项目状态

✅ **已完成**
- 书籍源 CRUD、书籍扫描导入
- Web 管理界面、Docker 部署
- 多架构支持、级联删除

🚧 **计划中**
- EPUB 元数据深度解析
- 用户认证、阅读进度跟踪

## 📄 License

MIT License

---

**版本**: 0.1.0 | **更新**: 2025-12-28
