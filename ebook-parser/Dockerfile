# 使用 Alpine 基础镜像以减小体积和提高构建速度
# 支持多架构: linux/amd64, linux/arm64
FROM python:3.11-alpine

WORKDIR /app

# 声明构建参数（用于多架构构建）
ARG TARGETPLATFORM
ARG BUILDPLATFORM

# 安装系统依赖（Pillow 和 curl）
# 使用阿里云镜像加速（可选，如果在中国使用）
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories && \
    apk add --no-cache \
    jpeg-dev \
    zlib-dev \
    gcc \
    musl-dev \
    curl && \
    echo "Building for platform: ${TARGETPLATFORM}"

# 复制依赖文件
COPY pyproject.toml ./

# 安装 Python 依赖（直接使用 pip，不需要 Poetry）
# 使用清华大学 PyPI 镜像加速（中国用户）
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple \
    fastapi==0.115.6 \
    uvicorn[standard]==0.34.0 \
    ebooklib==0.18 \
    Pillow==11.1.0 \
    pydantic==2.10.5 \
    beautifulsoup4==4.12.0 \
    lxml==5.1.0 \
    python-multipart==0.0.9

# 复制应用代码
COPY app/ ./app/

# 创建封面和图片目录
RUN mkdir -p /app/covers /app/book_images

# 暴露端口
EXPOSE 7920

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:7920/health || exit 1

# 运行应用
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "7920"]
