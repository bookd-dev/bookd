#!/bin/sh

set -eu

for platform in linux/amd64 linux/arm64; do
  docker buildx build \
    --platform "$platform" \
    --target libmobi-builder \
    --output type=cacheonly \
    .
done
