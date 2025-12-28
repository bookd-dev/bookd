#!/bin/bash

# Bookd 项目部署脚本

echo "🚀 开始构建和部署 Bookd 服务..."

# 1. 构建项目
echo "📦 构建项目..."
./gradlew clean buildFatJar

if [ $? -ne 0 ]; then
    echo "❌ 构建失败！"
    exit 1
fi

# 2. 停止并移除旧容器
echo "🛑 停止旧容器..."
docker-compose down

# 3. 构建 Docker 镜像
echo "🐳 构建 Docker 镜像..."
docker-compose build

# 4. 启动服务
echo "▶️  启动服务..."
docker-compose up -d

# 5. 查看日志
echo "📋 服务状态："
docker-compose ps

echo ""
echo "✅ 部署完成！"
echo "🌐 服务地址: http://localhost:7919"
echo "💾 数据库: PostgreSQL on localhost:5432"
echo ""
echo "查看日志: docker-compose logs -f"
echo "停止服务: docker-compose down"
