# 🐳 Docker 路径挂载问题说明

## 问题原因

### 为什么 Web 界面选择的本地文件夹无法使用？

当你在 Web 界面点击"📂 选择文件夹"时：

1. **浏览器选择**：你在 Mac 本地选择了文件夹（例如：`/Users/yourname/Books`）
2. **路径传递**：这个路径被发送到 Docker 容器内的服务器
3. **❌ 容器无法访问**：Docker 容器是隔离环境，看不到宿主机的文件系统
4. **扫描失败**：容器内访问该路径时返回"Directory does not exist"

### Docker 容器隔离示意图

```
宿主机 (Mac)                    Docker 容器
┌─────────────────────┐        ┌─────────────────────┐
│ /Users/you/Books/   │   ✗    │ 容器内部文件系统      │
│   ├── book1.epub    │        │ - 看不到宿主机路径    │
│   └── book2.pdf     │        │ - 只能访问挂载的目录  │
└─────────────────────┘        └─────────────────────┘
```

## 解决方案

### 方案 1：配置 Docker 卷挂载（推荐）

#### 步骤 1：修改 docker-compose.yml

编辑 `docker-compose.yml` 文件：

```yaml
services:
  bookd-server:
    build: .
    container_name: bookd-server
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: "jdbc:postgresql://postgres:5432/bookd"
      DATABASE_USER: "bookd"
      DATABASE_PASSWORD: "bookd"
    volumes:
      # 挂载你的书籍目录
      - /Users/yourname/Books:/data/books:ro
      # 可以挂载多个目录
      - /Users/yourname/Documents/Ebooks:/data/ebooks:ro
      - /Volumes/ExternalDrive/Library:/data/library:ro
    depends_on:
      - postgres
    networks:
      - bookd-network
```

**说明：**
- `/Users/yourname/Books` - Mac 上的实际路径（宿主机路径）
- `/data/books` - 容器内的路径（容器内路径）
- `:ro` - 只读权限（read-only），保护原文件

#### 步骤 2：重启服务

```bash
# 停止服务
docker-compose down

# 重新启动
docker-compose up -d
```

#### 步骤 3：使用容器内路径

在 Web 界面添加书籍源时，使用**容器内路径**：
- ✅ 正确：`/data/books`
- ❌ 错误：`/Users/yourname/Books`

### 方案 2：使用 Docker Desktop 文件共享

#### 对于 Mac 用户

1. 打开 **Docker Desktop**
2. 进入 **Preferences (设置)**
3. 选择 **Resources → File Sharing**
4. 添加你的书籍目录路径
5. 点击 **Apply & Restart**

![Docker Desktop File Sharing](https://docs.docker.com/desktop/images/file-sharing.png)

### 方案 3：本地运行（开发调试）

如果只是测试，可以不使用 Docker：

```bash
# 1. 启动数据库
docker-compose up -d postgres

# 2. 本地运行服务
./gradlew run

# 3. 现在可以直接访问 Mac 文件系统
```

这样你就可以使用任何本地路径了。

## 推荐配置示例

### 单个书籍目录

```yaml
volumes:
  - /Users/shenchao/Books:/data/books:ro
```

**使用时填写：** `/data/books`

### 多个书籍目录

```yaml
volumes:
  - /Users/shenchao/Books/Tech:/data/books/tech:ro
  - /Users/shenchao/Books/Novels:/data/books/novels:ro
  - /Users/shenchao/Documents/Ebooks:/data/ebooks:ro
```

**使用时填写：**
- `/data/books/tech`
- `/data/books/novels`
- `/data/ebooks`

### 外部硬盘

```yaml
volumes:
  - /Volumes/MyDisk/Library:/data/library:ro
```

**使用时填写：** `/data/library`

## 路径映射对照表

| 宿主机 (Mac) | 容器内 | Web 界面填写 |
|-------------|--------|------------|
| /Users/you/Books | /data/books | /data/books |
| /Users/you/Documents/Ebooks | /data/ebooks | /data/ebooks |
| /Volumes/ExternalDrive/Library | /data/library | /data/library |

## 实际操作步骤

### 完整示例

假设你的书籍在：`/Users/shenchao/Documents/MyBooks`

**1. 修改 docker-compose.yml：**

```yaml
services:
  bookd-server:
    volumes:
      - /Users/shenchao/Documents/MyBooks:/data/mybooks:ro
```

**2. 重启服务：**

```bash
docker-compose down
docker-compose up -d
```

**3. 在 Web 界面添加书籍源：**

- 源名称：`我的书籍`
- 文件路径：`/data/mybooks`  ← **使用容器内路径**

**4. 点击扫描：**

系统会扫描 `/data/mybooks`（实际对应 Mac 的 `/Users/shenchao/Documents/MyBooks`）

## 验证挂载是否成功

```bash
# 进入容器查看
docker exec -it bookd-server ls -lh /data/mybooks

# 如果能看到文件列表，说明挂载成功
```

## 注意事项

### 1. 权限问题

Mac 上的目录需要有读取权限：

```bash
chmod -R 755 /Users/yourname/Books
```

### 2. 路径格式

- ✅ 使用绝对路径：`/Users/yourname/Books`
- ❌ 不要用波浪号：`~/Books`
- ✅ 注意大小写

### 3. 性能

- 只读挂载 (`:ro`) 更安全，防止误删除
- 如果需要写入（如保存封面），去掉 `:ro`

### 4. Docker Desktop 限制

Mac 上 Docker Desktop 默认只共享：
- `/Users`
- `/Volumes`
- `/tmp`
- `/private`

如果你的文件在其他位置，需要在 Docker Desktop 设置中添加。

## 为什么 Web 界面的文件选择器"无效"？

浏览器的文件选择器让你选择**宿主机的路径**，但这个路径在 Docker 容器内无法访问。

**解决方案：**
1. 使用文件选择器只是为了**方便获取路径字符串**
2. 手动修改路径为**容器内对应的路径**
3. 或者直接手动输入容器内路径

**示例：**
- 浏览器选择：`/Users/shenchao/Books`
- 手动修改为：`/data/books`（根据 docker-compose.yml 的挂载配置）

## 更新后的 Web 界面提示

建议在 Web 界面添加提示：

```
⚠️ Docker 用户注意：
如果使用 Docker 部署，请填写容器内的挂载路径（如 /data/books），
而不是宿主机路径。请查看 docker-compose.yml 中的 volumes 配置。
```

## 快速参考

```bash
# 查看当前挂载
docker inspect bookd-server | grep -A 10 Mounts

# 测试路径是否可访问
docker exec bookd-server ls /data/books

# 修改配置后重启
docker-compose down && docker-compose up -d

# 查看日志
docker logs -f bookd-server
```

## 相关文档

- [Docker Volumes 官方文档](https://docs.docker.com/storage/volumes/)
- [Docker Desktop for Mac](https://docs.docker.com/desktop/mac/)
- [快速开始指南](QUICKSTART.md)
- [扫描功能说明](SCAN_GUIDE.md)
