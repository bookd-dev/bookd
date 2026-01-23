#!/bin/bash

# EPUB Parser Service - 开发环境快速启动脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 启动 EPUB Parser 微服务...${NC}"

# 检查 Python
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}❌ Python 3 未安装${NC}"
    exit 1
fi

echo -e "${YELLOW}📦 检查依赖...${NC}"

# 检查依赖是否已安装
if ! python3 -c "import fastapi" 2>/dev/null; then
    echo -e "${YELLOW}安装 Python 依赖...${NC}"
    pip3 install --user fastapi uvicorn ebooklib Pillow pydantic
fi

# 创建必要的目录
COVERS_DIR="${COVERS_DIR:-../covers}"
BOOK_IMAGES_DIR="${BOOK_IMAGES_DIR:-../book_images}"

mkdir -p "$COVERS_DIR" "$BOOK_IMAGES_DIR"

echo -e "${GREEN}✅ 环境准备完成${NC}"

# 设置环境变量
export COVERS_DIR="$COVERS_DIR"
export BOOK_IMAGES_DIR="$BOOK_IMAGES_DIR"
export PORT="${PORT:-7920}"
export LOG_LEVEL="${LOG_LEVEL:-info}"

echo -e "${GREEN}📝 配置信息:${NC}"
echo -e "  PORT: $PORT"
echo -e "  COVERS_DIR: $COVERS_DIR"
echo -e "  BOOK_IMAGES_DIR: $BOOK_IMAGES_DIR"
echo -e "  LOG_LEVEL: $LOG_LEVEL"

# 查找 uvicorn 路径
UVICORN_CMD="uvicorn"
if [ -f "$HOME/Library/Python/3.9/bin/uvicorn" ]; then
    UVICORN_CMD="$HOME/Library/Python/3.9/bin/uvicorn"
fi

echo -e "\n${GREEN}🎯 启动服务...${NC}"
echo -e "${YELLOW}健康检查: http://127.0.0.1:$PORT/health${NC}"
echo -e "${YELLOW}按 Ctrl+C 停止服务${NC}\n"

# 启动服务
$UVICORN_CMD app.main:app --host 0.0.0.0 --port "$PORT" --reload
