#!/bin/bash
# BookD 统一部署管理脚本

set -e

show_menu() {
    clear
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "📚 Bookd 部署管理工具"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "请选择操作："
    echo ""
    echo "  1) 🚀 本地部署（开发/测试）"
    echo "  2) 🔄 更新本地容器"
    echo "  3) 📤 构建并推送到 Docker Hub"
    echo "  4) 🧹 清理多余镜像"
    echo "  5) 📊 查看运行状态"
    echo "  6) 📝 查看日志"
    echo "  0) ❌ 退出"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

local_deploy() {
    echo ""
    echo "🚀 开始本地部署..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    echo ""
    echo "📦 1/4 构建 Kotlin 应用..."
    ./gradlew clean build -x test
    
    echo ""
    echo "🐍 2/4 构建 eBook Parser 微服务..."
    docker-compose build ebook-parser
    
    echo ""
    echo "🐳 3/4 构建 Bookd 服务镜像..."
    docker build -t bookd:local .
    
    echo ""
    echo "🎯 4/4 启动所有服务..."
    docker-compose up -d
    
    show_success_info
}

update_local() {
    echo ""
    echo "🔄 开始更新本地容器..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    echo ""
    echo "📦 1/5 构建 Kotlin 应用..."
    ./gradlew clean build -x test
    
    echo ""
    echo "🐍 2/5 重建 eBook Parser 微服务..."
    docker-compose build ebook-parser
    
    echo ""
    echo "🐳 3/5 构建 Bookd 服务镜像..."
    docker build -t bookd:local .
    
    echo ""
    echo "🛑 4/5 停止旧容器..."
    if docker ps -a --format '{{.Names}}' | grep -q '^bookd-ebook-parser$'; then
        docker-compose stop ebook-parser
        docker-compose rm -f ebook-parser
    fi
    if docker ps -a --format '{{.Names}}' | grep -q '^bookd-server$'; then
        docker-compose stop bookd-server
        docker-compose rm -f bookd-server
    else
        echo "   ℹ️  未发现运行中的容器"
    fi
    
    echo ""
    echo "🚀 5/5 启动新容器..."
    docker-compose up -d ebook-parser bookd-server
    
    show_success_info
}

push_to_hub() {
    echo ""
    read -p "🔑 请输入 Docker Hub 用户名: " DOCKER_USER
    
    if [ -z "$DOCKER_USER" ]; then
        echo "❌ 用户名不能为空"
        return
    fi
    
    BOOKD_IMAGE="${DOCKER_USER}/bookd:latest"
    PARSER_IMAGE="${DOCKER_USER}/bookd-ebook-parser:latest"
    
    echo ""
    echo "📤 构建并推送镜像到 Docker Hub"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🏷️  Bookd 镜像: $BOOKD_IMAGE"
    echo "🏷️  eBook Parser 镜像: $PARSER_IMAGE"
    echo "🏗️  架构支持: linux/amd64, linux/arm64"
    
    echo ""
    echo "🏗️  1/3 构建 Kotlin 应用..."
    ./gradlew clean build -x test
    
    echo ""
    echo "🐳 2/3 构建并推送 eBook Parser 多架构镜像..."
    cd ebook-parser
    docker buildx build --platform linux/amd64,linux/arm64 -t "$PARSER_IMAGE" --push .
    cd ..
    
    echo ""
    echo "🐳 3/3 构建并推送 Bookd 多架构镜像..."
    docker buildx build --platform linux/amd64,linux/arm64 -t "$BOOKD_IMAGE" --push .
    
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "✅ 所有镜像推送成功！"
    echo ""
    echo "📋 NAS 部署命令："
    echo ""
    echo "# 拉取镜像"
    echo "docker pull $BOOKD_IMAGE"
    echo "docker pull $PARSER_IMAGE"
    echo ""
    echo "# 在 docker-compose.yml 中使用："
    echo "services:"
    echo "  ebook-parser:"
    echo "    image: $PARSER_IMAGE"
    echo "  bookd-server:"
    echo "    image: $BOOKD_IMAGE"
    echo ""
    
    read -p "🔄 是否重启本地容器使用新镜像? (y/N): " -n 1 -r
    echo ""
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo ""
        echo "🔄 重启本地容器..."
        
        # Tag as local images
        docker tag "$BOOKD_IMAGE" bookd:local
        docker tag "$PARSER_IMAGE" bookd-ebook-parser:local
        
        if docker ps -a --format '{{.Names}}' | grep -q '^bookd-ebook-parser$'; then
            docker-compose stop ebook-parser
            docker-compose rm -f ebook-parser
        fi
        if docker ps -a --format '{{.Names}}' | grep -q '^bookd-server$'; then
            docker-compose stop bookd-server
            docker-compose rm -f bookd-server
        fi
        
        docker-compose up -d ebook-parser bookd-server
        
        show_success_info
    fi
}

cleanup_images() {
    echo ""
    echo "🧹 清理多余镜像"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "📋 当前的 bookd 相关镜像："
    docker images | grep -E "bookd|ebook-parser|REPOSITORY"
    echo ""
    
    read -p "⚠️  确定要清理吗? 只保留 bookd:local 和 bookd-ebook-parser (y/N): " -n 1 -r
    echo ""
    
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "❌ 取消清理"
        return
    fi
    
    echo ""
    echo "🗑️  清理中..."
    
    # 删除旧的 bookd 自动生成镜像（保留 bookd:local）
    for img in $(docker images --format '{{.Repository}}:{{.Tag}}' | grep 'bookd' | grep -v 'bookd:local' | grep -v 'ebook-parser'); do
        echo "  删除: $img"
        docker rmi "$img" 2>/dev/null || echo "  ⚠️  跳过（可能正在使用）"
    done
    
    # 删除旧的 ebook-parser 自动生成镜像（保留正在使用的）
    for img in $(docker images --format '{{.Repository}}:{{.Tag}}' | grep 'ebook-parser' | grep -v 'bookd-ebook-parser'); do
        echo "  删除: $img"
        docker rmi "$img" 2>/dev/null || echo "  ⚠️  跳过（可能正在使用）"
    done
    
    echo ""
    echo "✅ 清理完成！"
    echo ""
    echo "📋 剩余镜像："
    docker images | grep -E "bookd|ebook-parser|REPOSITORY"
}

show_status() {
    echo ""
    echo "📊 运行状态"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    docker-compose ps
    echo ""
    echo "📋 镜像列表："
    docker images | grep -E "bookd|REPOSITORY"
}

show_logs() {
    echo ""
    echo "📝 查看日志"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "请选择要查看的服务："
    echo "  1) Bookd Server"
    echo "  2) eBook Parser"
    echo "  3) 所有服务"
    echo ""
    read -p "请输入选项 (1-3): " -n 1 -r
    echo ""
    echo ""
    echo "按 Ctrl+C 退出日志查看"
    echo ""
    sleep 2
    
    case $REPLY in
        1)
            docker-compose logs -f bookd-server
            ;;
        2)
            docker-compose logs -f ebook-parser
            ;;
        3)
            docker-compose logs -f
            ;;
        *)
            echo "❌ 无效选项，返回菜单"
            sleep 2
            ;;
    esac
}

show_success_info() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "✅ 操作完成！"
    echo ""
    echo "📋 访问地址："
    echo "   登录页面: http://localhost:7919/login"
    echo "   管理后台: http://localhost:7919/admin"
    echo "   API 健康: http://localhost:7919/api/health"
    echo "   eBook Parser: http://localhost:7920/health"
    echo ""
    echo "📊 常用命令："
    echo "   查看日志: docker-compose logs -f"
    echo "   查看状态: docker-compose ps"
    echo "   停止服务: docker-compose down"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# 主循环
while true; do
    show_menu
    read -p "请输入选项 (0-6): " -n 1 -r
    echo ""
    
    case $REPLY in
        1)
            local_deploy
            read -p "按任意键继续..." -n 1 -r
            ;;
        2)
            update_local
            read -p "按任意键继续..." -n 1 -r
            ;;
        3)
            push_to_hub
            read -p "按任意键继续..." -n 1 -r
            ;;
        4)
            cleanup_images
            read -p "按任意键继续..." -n 1 -r
            ;;
        5)
            show_status
            read -p "按任意键继续..." -n 1 -r
            ;;
        6)
            show_logs
            ;;
        0)
            echo ""
            echo "👋 再见！"
            exit 0
            ;;
        *)
            echo ""
            echo "❌ 无效选项，请重新选择"
            sleep 2
            ;;
    esac
done
