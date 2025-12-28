# ⚙️ 端口配置说明

## 当前配置

**服务端口**: `7919`

## 配置方式

### 1. Docker Compose 环境变量

编辑 `docker-compose.yml`：

```yaml
services:
  bookd-server:
    ports:
      - "7919:7919"  # 宿主机:容器内
    environment:
      PORT: "7919"   # 设置应用端口
```

### 2. Application 配置

`src/main/resources/application.yaml` 支持环境变量：

```yaml
ktor:
  deployment:
    port: ${PORT:8080}  # 从环境变量读取，默认 8080
    host: 0.0.0.0
```

### 3. 端口映射关系

```
宿主机端口  →  容器端口  →  应用端口
  7919     →    7919    →    7919
   ↓           ↓            ↓
 外部访问    Docker映射   Ktor应用
```

## 修改端口

### 方法 1：修改 docker-compose.yml（推荐）

```yaml
services:
  bookd-server:
    ports:
      - "你的端口:你的端口"  # 例如 "3000:3000"
    environment:
      PORT: "你的端口"       # 例如 "3000"
```

然后重启：

```bash
docker-compose down
docker-compose up -d
```

### 方法 2：只修改宿主机端口

如果只想改变外部访问端口，而不修改容器内端口：

```yaml
services:
  bookd-server:
    ports:
      - "3000:7919"  # 外部3000，内部还是7919
    environment:
      PORT: "7919"   # 保持不变
```

访问时使用：`http://localhost:3000/`

### 方法 3：本地运行时

```bash
# 使用环境变量
PORT=3000 ./gradlew run

# 或修改 application.yaml
ktor:
  deployment:
    port: 3000
```

## 常用端口示例

### 开发环境
```yaml
PORT: "8080"  # 标准开发端口
```

### 生产环境
```yaml
PORT: "80"    # HTTP 标准端口（需要 root 权限）
PORT: "443"   # HTTPS 标准端口
PORT: "8000"  # 常用备选端口
```

### 个性化端口
```yaml
PORT: "7919"  # 当前配置
PORT: "9000"
PORT: "3000"
```

## 端口冲突解决

### 检查端口占用

```bash
# Mac/Linux
lsof -i :7919

# 查看所有监听端口
netstat -an | grep LISTEN
```

### 释放端口

```bash
# 停止占用端口的服务
docker-compose down

# 或杀死占用进程
kill -9 <PID>
```

## 访问地址

### 当前配置（端口 7919）

- **Web 管理界面**: http://localhost:7919/
- **健康检查**: http://localhost:7919/health
- **书籍列表**: http://localhost:7919/api/books
- **书籍源**: http://localhost:7919/api/sources
- **扫描 API**: http://localhost:7919/api/scan/all

### 更改端口后

将上述 URL 中的 `7919` 替换为你的新端口即可。

## 防火墙配置

### Mac

```bash
# Mac 一般不需要额外配置
# Docker Desktop 会自动处理端口映射
```

### Linux 服务器

```bash
# 允许端口访问
sudo ufw allow 7919

# 或使用 iptables
sudo iptables -A INPUT -p tcp --dport 7919 -j ACCEPT
```

### Docker 网络

容器间通信不需要暴露端口：

```yaml
services:
  bookd-server:
    # 容器内访问使用: http://bookd-server:7919
    # 不需要 ports 配置
    networks:
      - bookd-network
```

## 多实例部署

运行多个实例时，每个实例使用不同端口：

```yaml
# docker-compose.prod1.yml
services:
  bookd-server:
    ports:
      - "7919:7919"
    environment:
      PORT: "7919"

# docker-compose.prod2.yml
services:
  bookd-server:
    ports:
      - "7920:7920"
    environment:
      PORT: "7920"
```

启动：

```bash
docker-compose -f docker-compose.prod1.yml up -d
docker-compose -f docker-compose.prod2.yml up -d
```

## 环境变量优先级

1. **docker-compose.yml** 中的 `environment`（最高）
2. **application.yaml** 中的默认值
3. **代码中的硬编码**（最低，已移除）

## 验证配置

```bash
# 检查容器端口映射
docker ps

# 检查应用日志
docker logs bookd-server | grep "Responding at"

# 测试连接
curl http://localhost:7919/health

# 查看环境变量
docker exec bookd-server env | grep PORT
```

## 配置示例

### 开发环境（默认 8080）

```yaml
environment:
  PORT: "8080"
ports:
  - "8080:8080"
```

### 生产环境（7919）

```yaml
environment:
  PORT: "7919"
ports:
  - "7919:7919"
```

### Nginx 反向代理（内部 8080）

```yaml
environment:
  PORT: "8080"
# 不暴露端口，通过 Nginx 代理
```

```nginx
# nginx.conf
server {
    listen 80;
    location / {
        proxy_pass http://bookd-server:8080;
    }
}
```

## 故障排查

### 服务无法访问

1. 检查容器状态：`docker ps`
2. 检查日志：`docker logs bookd-server`
3. 检查端口：`lsof -i :7919`
4. 测试健康检查：`curl localhost:7919/health`

### 端口冲突

```bash
# 查找占用进程
lsof -i :7919

# 更改为其他端口
# 修改 docker-compose.yml 后重启
docker-compose down
docker-compose up -d
```

## 相关文档

- [快速开始](QUICKSTART.md)
- [Docker 卷挂载](DOCKER_VOLUMES.md)
- [ESJZone 配置](ESJZONE_SETUP.md)

---

**当前端口**: 7919
**配置时间**: 2025-12-28
**配置方式**: Docker Compose 环境变量
