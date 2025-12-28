# 📦 NAS 部署指南

本文档介绍如何将 Bookd 部署到 NAS 设备（群晖、威联通等）。

## 🎯 部署方案

### 方案一：Docker Hub（推荐）

#### 1. 构建并推送镜像

```bash
# 登录 Docker Hub
docker login

# 构建多架构镜像
docker buildx build --platform linux/amd64 -t yourusername/bookd:latest .

# 推送到 Docker Hub
docker push yourusername/bookd:latest
```

#### 2. NAS 上部署

在 NAS 创建 `docker-compose.yml`：

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: bookd-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: bookd
      POSTGRES_USER: bookd
      POSTGRES_PASSWORD: bookd123
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U bookd"]
      interval: 10s
      timeout: 5s
      retries: 5

  bookd-server:
    image: yourusername/bookd:latest
    container_name: bookd-server
    restart: unless-stopped
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/bookd
      DATABASE_USER: bookd
      DATABASE_PASSWORD: bookd123
      PORT: 7919
    ports:
      - "7919:7919"
    volumes:
      - /volume1:/nas:ro  # 挂载整个 NAS，在 Web 界面选择子目录
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
```

启动服务：

```bash
docker-compose up -d
```

### 方案二：离线部署（tar 文件）

#### 1. 导出镜像

```bash
# 构建镜像
docker buildx build --platform linux/amd64 -t bookd:amd64 --load .

# 保存为 tar
docker save bookd:amd64 -o bookd-amd64.tar
gzip bookd-amd64.tar
```

#### 2. 传输到 NAS

```bash
scp bookd-amd64.tar.gz admin@192.168.1.100:/volume1/docker/
```

#### 3. 导入并运行

```bash
# SSH 登录 NAS
ssh admin@192.168.1.100

# 导入镜像
gunzip bookd-amd64.tar.gz
docker load -i bookd-amd64.tar

# 使用上面的 docker-compose.yml，镜像名改为 bookd:amd64
docker-compose up -d
```

## ⚙️ 配置说明

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DATABASE_URL` | 数据库连接地址 | `jdbc:postgresql://postgres:5432/bookd` |
| `DATABASE_USER` | 数据库用户 | `bookd` |
| `DATABASE_PASSWORD` | 数据库密码 | `bookd123` |
| `PORT` | 服务端口 | `7919` |

### 卷挂载路径

不同 NAS 系统的路径：

| 系统 | 路径格式 | 示例 |
|------|---------|------|
| 群晖 | `/volume1/` | `/volume1:/nas:ro` |
| 威联通 | `/share/` | `/share:/nas:ro` |
| TrueNAS | `/mnt/pool/` | `/mnt/pool:/nas:ro` |

在 Web 界面添加书籍源时使用：`/nas/你的子目录`

## 🚀 快速部署

```bash
# 在 NAS 创建目录
mkdir -p /volume1/docker/bookd
cd /volume1/docker/bookd

# 创建 docker-compose.yml（粘贴上面的配置）
nano docker-compose.yml

# 启动
docker-compose up -d

# 查看日志
docker-compose logs -f
```

## 🔍 故障排查

```bash
# 查看容器状态
docker-compose ps

# 查看日志
docker-compose logs bookd-server
docker-compose logs postgres

# 重启服务
docker-compose restart

# 测试数据库连接
docker exec -it bookd-postgres psql -U bookd -d bookd -c "SELECT 1;"
```

## 🌐 访问地址

- **管理界面**: `http://nas-ip:7919/`
- **健康检查**: `http://nas-ip:7919/health`
- **书籍 API**: `http://nas-ip:7919/api/books`

## 📝 注意事项

1. **端口冲突**：如 7919 被占用，修改 `ports` 和 `PORT` 环境变量
2. **数据备份**：定期备份 `postgres_data` 卷
3. **安全性**：生产环境请修改默认数据库密码
4. **架构匹配**：确保镜像架构与 NAS CPU 架构一致（x86 用 amd64，ARM 用 arm64）

## 🔄 更新部署

```bash
# Docker Hub 方式
docker-compose pull
docker-compose up -d

# 离线方式
docker load -i bookd-new.tar
docker-compose up -d
```

---

**提示**：首次启动可能需要 10-30 秒等待数据库初始化完成。
