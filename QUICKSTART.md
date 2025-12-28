# 🚀 Bookd 快速开始指南

## 一、部署服务

### 使用部署脚本（推荐）

```bash
./deploy.sh
```

### 手动部署

```bash
# 1. 构建项目
./gradlew clean shadowJar

# 2. 启动服务
docker-compose up -d

# 3. 查看日志
docker logs -f bookd-server
```

## 二、访问服务

### Web 管理界面
打开浏览器访问：**http://localhost:7919/**

### API 接口
- 健康检查: http://localhost:7919/health
- 书籍列表: http://localhost:7919/api/books
- 书籍源: http://localhost:7919/api/sources

## 三、添加书籍源

### 方式 1：通过 Web 界面

1. 打开 http://localhost:7919/
2. 填写源名称（例如：技术书籍）
3. 输入路径或点击"📂 选择文件夹"
4. 点击"➕ 添加书籍源"

### 方式 2：通过 API

```bash
curl -X POST http://localhost:7919/api/sources \
  -H "Content-Type: application/json" \
  -d '{
    "name": "技术书籍",
    "path": "/data/books/tech"
  }'
```

## 四、管理书籍源

### 查看所有书籍源

```bash
curl http://localhost:7919/api/sources
```

### 禁用书籍源

```bash
curl -X POST http://localhost:7919/api/sources/1/toggle
```

### 删除书籍源

```bash
curl -X DELETE http://localhost:7919/api/sources/1
```

## 五、查看书籍

### 获取书籍列表

```bash
curl http://localhost:7919/api/books
```

### 分页查询

```bash
curl "http://localhost:7919/api/books?limit=50&offset=0"
```

### 获取书籍详情

```bash
curl http://localhost:7919/api/books/1
```

## 六、Docker 命令

### 查看容器状态

```bash
docker-compose ps
```

### 查看日志

```bash
# 查看所有日志
docker-compose logs -f

# 只看服务器日志
docker logs -f bookd-server

# 只看数据库日志  
docker logs -f bookd-postgres
```

### 重启服务

```bash
# 重启所有服务
docker-compose restart

# 只重启服务器
docker-compose restart bookd-server
```

### 停止服务

```bash
docker-compose down
```

### 清理并重新部署

```bash
# 停止并删除容器
docker-compose down

# 删除数据卷（⚠️ 会清空数据库）
docker-compose down -v

# 重新部署
docker-compose up -d
```

## 七、配置文件路径

### 修改书籍存储路径

编辑 `docker-compose.yml`：

```yaml
volumes:
  - /your/books/path:/data/books:ro
```

### 修改数据库配置

编辑 `src/main/resources/application.yaml`：

```yaml
database:
  url: "jdbc:postgresql://postgres:5432/bookd"
  driver: "org.postgresql.Driver"
  user: "bookd"
  password: "bookd"
```

## 八、常见问题

### 无法访问 Web 界面

1. 检查容器状态：`docker ps`
2. 检查日志：`docker logs bookd-server`
3. 确认端口未被占用：`lsof -i :8080`

### API 返回错误

1. 检查数据库连接：`docker logs bookd-postgres`
2. 查看服务器日志：`docker logs bookd-server`
3. 测试健康检查：`curl http://localhost:7919/health`

### 数据库连接失败

```bash
# 确保 PostgreSQL 已启动
docker-compose up -d postgres

# 等待几秒后启动服务器
docker-compose up -d bookd-server
```

## 九、开发调试

### 本地运行（不使用 Docker）

```bash
# 1. 启动数据库
docker-compose up -d postgres

# 2. 本地运行服务
./gradlew run

# 3. 访问 http://localhost:7919/
```

### 重新构建

```bash
# 清理旧构建
./gradlew clean

# 重新构建
./gradlew shadowJar

# 重新构建 Docker 镜像
docker-compose build --no-cache
```

## 十、下一步

- 📚 阅读 [Web 界面使用指南](WEB_UI_GUIDE.md)
- 📖 查看 [项目状态文档](PROJECT_STATUS.md)
- 🔧 了解 [Routing 问题修复](ROUTING_FIX.md)
- 📝 参考 [技术需求文档](rulers.md)

## 支持

如遇问题，请查看：
- 项目日志：`docker logs bookd-server`
- 数据库日志：`docker logs bookd-postgres`
- 健康检查：`curl http://localhost:7919/health`

---

**祝使用愉快！** 📚✨
