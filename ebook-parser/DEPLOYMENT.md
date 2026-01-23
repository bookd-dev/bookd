# EPUB Parser 微服务集成指南

## ✅ 已完成的工作

### 1. Python 微服务 (epub-parser-service/)
- ✅ 使用 FastAPI 和 ebooklib 实现 EPUB 解析
- ✅ 支持元数据提取（标题、作者、出版社、描述、ISBN、标签）
- ✅ 支持封面提取（保存到共享卷）
- ✅ 提供健康检查端点
- ✅ Docker 容器化

### 2. Kotlin 后端集成
- ✅ 添加 Ktor HTTP 客户端依赖
- ✅ 创建 `EpubParserConfig` 配置类
- ✅ 创建 `EpubParserClient` HTTP 客户端
- ✅ 修改 `EpubMetadataExtractor` 支持降级机制
- ✅ 更新 Koin 依赖注入配置

### 3. Docker 部署配置
- ✅ 更新 `docker-compose.yml` 添加 epub-parser 服务
- ✅ 配置服务间网络通信
- ✅ 共享卷配置（covers、book_images、书籍文件）

## 🚀 部署说明

### 本地开发环境测试

#### 1. 启动 Python 微服务（独立测试）

```bash
cd epub-parser-service

# 方式 1: 使用 Poetry
poetry install
poetry run uvicorn app.main:app --host 127.0.0.1 --port 7920

# 方式 2: 使用 pip (已测试通过)
pip install fastapi uvicorn ebooklib Pillow pydantic
COVERS_DIR="/Users/shenchao/workspace/bookd/covers" \
BOOK_IMAGES_DIR="/Users/shenchao/workspace/bookd/book_images" \
PORT="7920" \
uvicorn app.main:app --host 127.0.0.1 --port 7920
```

#### 2. 测试 API

```bash
# 健康检查
curl http://127.0.0.1:7920/health

# 提取元数据
curl -X POST http://127.0.0.1:7920/api/parse/metadata \
  -H "Content-Type: application/json" \
  -d '{"file_path": "/path/to/your.epub", "book_id": 1}'

# 提取封面
curl -X POST http://127.0.0.1:7920/api/parse/cover \
  -H "Content-Type: application/json" \
  -d '{"file_path": "/path/to/your.epub", "book_id": 1}'
```

### Docker 部署

#### 1. 构建并启动所有服务

```bash
cd bookd

# 构建 Kotlin 后端镜像
./gradlew clean build -x test
docker build -t bookd:local .

# 启动所有服务（包括 epub-parser）
docker-compose up -d

# 查看日志
docker-compose logs -f epub-parser
docker-compose logs -f bookd-server
```

#### 2. 验证服务状态

```bash
# 检查所有容器状态
docker-compose ps

# 测试 epub-parser 健康检查
curl http://localhost:7920/health

# 测试 bookd-server
curl http://localhost:7919/api/books
```

## 🔧 配置说明

### 环境变量

#### Python 微服务 (epub-parser)
```yaml
HOST: "0.0.0.0"                    # 服务监听地址
PORT: "7920"                        # 服务端口
LOG_LEVEL: "info"                   # 日志级别
COVERS_DIR: "/app/covers"           # 封面保存目录
BOOK_IMAGES_DIR: "/app/book_images" # 书籍图片目录
TZ: "Asia/Shanghai"                 # 时区
```

#### Kotlin 后端 (bookd-server)
```yaml
EPUB_PARSER_ENABLED: "true"                      # 启用微服务
EPUB_PARSER_SERVICE_URL: "http://epub-parser:7920" # 微服务地址
EPUB_PARSER_TIMEOUT_MS: "30000"                  # 请求超时（毫秒）
```

### 降级机制

1. **优先使用微服务**: `EPUB_PARSER_ENABLED=true` 时，优先调用 Python 微服务
2. **自动降级**: 微服务不可用或失败时，自动降级到 Kotlin 本地解析器
3. **日志记录**: 所有降级事件都会记录到日志中

## 📊 测试结果

### 测试用例
- ✅ 健康检查: `{"status":"healthy","version":"0.1.0"}`
- ✅ 元数据提取: 成功提取标题、作者、出版社、标签
  ```json
  {
    "title": "我怎么可能成为你的恋人，不行不行！(※不是不可能！？) 第二卷",
    "author": "みかみてれん",
    "publisher": "集英社",
    "tags": ["校园", "欢乐向", "恋爱", "百合", "女性视角", "青梅竹马"]
  }
  ```
- ⚠️ 封面提取: 部分 EPUB 文件无内嵌封面（功能正常，只是测试书籍没有封面）

## 🔍 故障排查

### Python 微服务无法启动

1. **检查端口占用**
   ```bash
   lsof -i :7920
   ```

2. **查看日志**
   ```bash
   docker-compose logs epub-parser
   ```

3. **检查目录权限**
   ```bash
   ls -la /app/covers
   ```

### Kotlin 后端无法连接微服务

1. **检查网络连接**
   ```bash
   docker exec -it bookd-server curl http://epub-parser:7920/health
   ```

2. **验证环境变量**
   ```bash
   docker exec -it bookd-server env | grep EPUB_PARSER
   ```

3. **查看降级日志**
   ```bash
   docker-compose logs bookd-server | grep "EPUB Parser"
   ```

## 📝 下一步优化建议

### 性能优化
- [ ] 添加 Redis 缓存元数据结果
- [ ] 实现批量解析 API
- [ ] 优化大文件处理（流式传输）

### 功能增强
- [ ] 支持更多格式（PDF, MOBI, AZW3）
- [ ] 提取目录信息
- [ ] 提取内嵌字体信息

### 监控和运维
- [ ] 添加 Prometheus metrics
- [ ] 实现分布式追踪（OpenTelemetry）
- [ ] 添加速率限制

## 🎉 总结

微服务已成功集成！主要特点：

1. **解耦架构**: Python 专注 EPUB 解析，Kotlin 专注业务逻辑
2. **可靠降级**: 微服务失败不影响主服务
3. **易于扩展**: 可独立升级和水平扩展
4. **保持兼容**: 与现有 `BookMetadata` 数据结构完全兼容

测试路径: `/Users/shenchao/ebook/EBook/我怎么可能成为你的恋人 小说/`
