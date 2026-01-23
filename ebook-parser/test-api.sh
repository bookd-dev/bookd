#!/bin/bash

# EPUB Parser Service - 功能测试脚本

set -e

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SERVICE_URL="${SERVICE_URL:-http://127.0.0.1:7920}"
TEST_EPUB_DIR="${TEST_EPUB_DIR:-/Users/shenchao/ebook/EBook/我怎么可能成为你的恋人 小说}"

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  EPUB Parser Service - 功能测试${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"

# 测试 1: 健康检查
echo -e "${YELLOW}[测试 1/3] 健康检查${NC}"
HEALTH_RESPONSE=$(curl -s "$SERVICE_URL/health")
if echo "$HEALTH_RESPONSE" | grep -q "healthy"; then
    echo -e "${GREEN}✅ 健康检查通过${NC}"
    echo "$HEALTH_RESPONSE" | python3 -m json.tool
else
    echo -e "${RED}❌ 健康检查失败${NC}"
    exit 1
fi

echo ""

# 测试 2: 元数据提取
echo -e "${YELLOW}[测试 2/3] 元数据提取${NC}"

# 查找第一个 EPUB 文件
if [ -d "$TEST_EPUB_DIR" ]; then
    FIRST_EPUB=$(find "$TEST_EPUB_DIR" -name "*.epub" -type f | head -1)
    
    if [ -z "$FIRST_EPUB" ]; then
        echo -e "${RED}❌ 未找到测试 EPUB 文件${NC}"
        exit 1
    fi
    
    echo -e "测试文件: ${BLUE}$(basename "$FIRST_EPUB")${NC}"
    
    METADATA_RESPONSE=$(curl -s -X POST "$SERVICE_URL/api/parse/metadata" \
        -H "Content-Type: application/json" \
        -d "{\"file_path\": \"$FIRST_EPUB\", \"book_id\": 999}")
    
    if echo "$METADATA_RESPONSE" | grep -q "title"; then
        echo -e "${GREEN}✅ 元数据提取成功${NC}"
        echo "$METADATA_RESPONSE" | python3 -m json.tool
    else
        echo -e "${RED}❌ 元数据提取失败${NC}"
        echo "$METADATA_RESPONSE"
        exit 1
    fi
else
    echo -e "${RED}❌ 测试目录不存在: $TEST_EPUB_DIR${NC}"
    exit 1
fi

echo ""

# 测试 3: 封面提取
echo -e "${YELLOW}[测试 3/3] 封面提取${NC}"

COVER_RESPONSE=$(curl -s -X POST "$SERVICE_URL/api/parse/cover" \
    -H "Content-Type: application/json" \
    -d "{\"file_path\": \"$FIRST_EPUB\", \"book_id\": 999}")

if echo "$COVER_RESPONSE" | grep -q "success"; then
    SUCCESS=$(echo "$COVER_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['success'])")
    
    if [ "$SUCCESS" = "True" ]; then
        echo -e "${GREEN}✅ 封面提取成功${NC}"
    else
        echo -e "${YELLOW}⚠️  该 EPUB 无内嵌封面（功能正常）${NC}"
    fi
    echo "$COVER_RESPONSE" | python3 -m json.tool
else
    echo -e "${RED}❌ 封面提取接口异常${NC}"
    echo "$COVER_RESPONSE"
    exit 1
fi

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}🎉 所有测试通过！${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"

echo -e "${YELLOW}💡 提示:${NC}"
echo -e "  - 服务地址: $SERVICE_URL"
echo -e "  - API 文档: ${SERVICE_URL}/docs"
echo -e "  - 测试目录: $TEST_EPUB_DIR"
