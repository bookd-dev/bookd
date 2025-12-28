# 📚 Bookd - 电子书管理系统

基于 Kotlin + Ktor 的现代化电子书管理后端系统，专为 NAS 环境设计。

## ✨ 功能特性

- 📖 **书籍管理** - 自动扫描导入电子书（支持 EPUB, PDF, TXT, MOBI, AZW3）
- 📁 **书籍源** - 灵活配置多个书籍目录
- 🎨 **Web 界面** - 现代化渐变紫色主题管理后台
- 🐳 **Docker 部署** - 一键部署，容器化运行
- 🗄️ **PostgreSQL** - 可靠的数据持久化
- 🔍 **智能扫描** - 自动提取书籍元数据和作者信息

## 🚀 快速开始

### 一键部署

```bash
./deploy.sh
```

### 手动部署

```bash
# 1. 构建项目
./gradlew clean shadowJar

# 2. 启动服务
docker-compose up -d

# 3. 访问
open http://localhost:7919/
```

## 🌐 访问地址

- **Web 管理界面**: http://localhost:7919/
- **API 接口**: http://localhost:7919/api/*
- **健康检查**: http://localhost:7919/health

## 📖 文档

- [快速开始指南](QUICKSTART.md) - 详细的部署和使用说明
- [Docker 卷挂载](DOCKER_VOLUMES.md) - 如何挂载本地书籍目录
- [端口配置](PORT_CONFIG.md) - 自定义端口配置

## 🛠️ 技术栈

- **Kotlin** 2.2.21 - 现代化 JVM 语言
- **Ktor** 3.3.2 - 轻量级异步 Web 框架
- **PostgreSQL** 16 - 关系型数据库
- **Exposed** 0.47.0 - Kotlin SQL 框架
- **Koin** 3.5.3 - 依赖注入
- **Docker** - 容器化部署

## 📦 主要功能

### 1. 书籍源管理
- 添加/删除书籍源
- 启用/禁用书籍源
- 多目录支持

### 2. 书籍扫描
- 递归扫描目录
- 自动去重
- 智能识别作者（文件名规则）
- 支持格式：txt, epub, mobi, azw3, pdf

### 3. Web 管理界面
- 实时统计（书籍数、书籍源数）
- 可视化管理书籍源
- 一键扫描导入
- 响应式设计

## 🔧 配置

### 端口配置

默认端口：**7919**

修改 `docker-compose.yml`：

```yaml
environment:
  PORT: "你的端口"
ports:
  - "你的端口:你的端口"
```

### 挂载书籍目录

编辑 `docker-compose.yml`：

```yaml
volumes:
  - /your/books/path:/data/books:ro
```

Web 界面使用 `/data/books` 作为路径。

## 📊 项目状态

- ✅ 基础架构搭建完成
- ✅ 书籍源管理 CRUD
- ✅ 书籍扫描导入
- ✅ Web 管理界面
- ✅ Docker 部署
- 🚧 元数据深度解析（计划中）
- 🚧 JWT 认证（计划中）
- 🚧 阅读进度跟踪（计划中）

## 🎯 使用示例

### 添加书籍源

```bash
curl -X POST http://localhost:7919/api/sources \
  -H "Content-Type: application/json" \
  -d '{"name":"我的书库","path":"/data/books"}'
```

### 扫描书籍

```bash
curl -X POST http://localhost:7919/api/scan/all
```

### 查看书籍列表

```bash
curl http://localhost:7919/api/books
```

## 📝 常用命令

```bash
# 查看日志
docker-compose logs -f

# 重启服务
docker-compose restart

# 停止服务
docker-compose down

# 进入容器
docker exec -it bookd-server sh
```

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 License

MIT License

## 📮 联系方式

如有问题，请提交 Issue。

---

**最后更新**: 2025-12-28  
**当前版本**: 0.0.1  
**开发状态**: 🚧 开发中
