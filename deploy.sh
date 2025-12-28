#!/bin/bash
# BookD 快速部署脚本

set -e

echo "📦 BookD 部署助手"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 选择操作
echo "请选择操作:"
echo "  1) 直接启动本地服务 (使用 docker-compose)"
echo "  2) 构建镜像并推送到 Docker Hub"
echo ""
read -p "请输入选项 (1/2): " -n 1 -r
echo ""
echo ""

if [[ $REPLY == "1" ]]; then
    # 直接启动
    echo "🚀 正在启动本地服务..."
    echo ""
    
    # 构建应用
    echo "📦 构建应用..."
    ./gradlew clean build -x test
    
    # 构建 Docker 镜像
    echo "🐳 构建 Docker 镜像..."
    docker build -t bookd:local .
    
    # 更新 docker-compose 使用本地镜像
    if [ -f "docker-compose.yml" ]; then
        sed -i.bak 's|image: .*bookd.*|image: bookd:local|g' docker-compose.yml
    fi
    
    # 启动服务
    echo "🎯 启动服务..."
    docker-compose up -d
    
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "✅ 服务启动成功！"
    echo ""
    echo "📋 访问地址:"
    echo "   管理界面: http://localhost:7919/admin"
    echo "   API 文档: http://localhost:7919/api/health"
    echo ""
    echo "📊 查看日志:"
    echo "   docker-compose logs -f bookd-server"
    echo ""
    echo "🛑 停止服务:"
    echo "   docker-compose down"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    exit 0
fi

# 检查 Docker Hub 用户名
if [ -z "$2" ]; then
    read -p "🔑 请输入 Docker Hub 用户名: " DOCKER_USER
else
    DOCKER_USER=$2
fi

IMAGE_NAME="${DOCKER_USER}/bookd:latest"

echo "🏷️  镜像名称: $IMAGE_NAME"
echo ""

# 构建镜像
echo "🏗️  正在构建镜像..."
./gradlew clean build -x test
docker build -t "$IMAGE_NAME" .

echo ""
echo "✅ 镜像构建完成"
echo ""

# 询问是否推送
read -p "📤 是否推送到 Docker Hub? (y/N): " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🔐 请先登录 Docker Hub:"
    docker login
    
    echo ""
    echo "📤 正在推送镜像..."
    docker push "$IMAGE_NAME"
    
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "✅ 部署成功！"
    echo ""
    echo "📋 NAS 部署步骤:"
    echo ""
    echo "1. 在 NAS 上创建目录:"
    echo "   mkdir -p /volume1/docker/bookd"
    echo ""
    echo "2. 创建 docker-compose.yml:"
    echo "   cat > docker-compose.yml << 'COMPOSE'"
    echo "version: '3.8'"
    echo "services:"
    echo "  postgres:"
    echo "    image: postgres:16-alpine"
    echo "    container_name: bookd-postgres"
    echo "    restart: unless-stopped"
    echo "    environment:"
    echo "      POSTGRES_DB: bookd"
    echo "      POSTGRES_USER: bookd"
    echo "      POSTGRES_PASSWORD: bookd123"
    echo "    volumes:"
    echo "      - /volume1/docker/bookd/postgres:/var/lib/postgresql/data"
    echo ""
    echo "  bookd-server:"
    echo "    image: $IMAGE_NAME"
    echo "    container_name: bookd-server"
    echo "    restart: unless-stopped"
    echo "    environment:"
    echo "      DATABASE_URL: jdbc:postgresql://postgres:5432/bookd"
    echo "      DATABASE_USER: bookd"
    echo "      DATABASE_PASSWORD: bookd123"
    echo "      SERVER_PORT: 7919"
    echo "    ports:"
    echo "      - \"7919:7919\""
    echo "    volumes:"
    echo "      - /volume1:/nas"
    echo "    depends_on:"
    echo "      - postgres"
    echo "COMPOSE"
    echo ""
    echo "3. 启动服务:"
    echo "   docker-compose up -d"
    echo ""
    echo "4. 访问管理界面:"
    echo "   http://nas-ip:7919/admin"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    echo ""
    echo "ℹ️  镜像已构建但未推送"
    echo ""
    echo "💾 离线部署方式:"
    echo "   docker save $IMAGE_NAME -o bookd-image.tar"
    echo "   gzip bookd-image.tar"
    echo "   scp bookd-image.tar.gz admin@nas-ip:/volume1/docker/"
    echo ""
fi
