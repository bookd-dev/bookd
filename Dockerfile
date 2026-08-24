FROM alpine:3.22 AS libmobi-builder

ARG LIBMOBI_REPOSITORY=https://github.com/bfabiszewski/libmobi.git
ARG LIBMOBI_COMMIT=85dcfe803fc2a21020ddcf15c3eb66b93d388add

RUN apk add --no-cache autoconf automake build-base git libtool
RUN git clone "$LIBMOBI_REPOSITORY" /src/libmobi && \
    cd /src/libmobi && \
    git checkout --detach "$LIBMOBI_COMMIT" && \
    test "$(git rev-parse HEAD)" = "$LIBMOBI_COMMIT" && \
    ./autogen.sh && \
    ./configure --prefix=/usr/local --disable-shared --enable-static --enable-tools-static --with-zlib=no --with-libxml2=no && \
    make -j2 && \
    make install DESTDIR=/out && \
    strip /out/usr/local/bin/mobitool && \
    mkdir -p /out/usr/local/share/licenses/libmobi && \
    cp COPYING /out/usr/local/share/licenses/libmobi/COPYING && \
    mkdir -p /smoke && \
    /out/usr/local/bin/mobitool -v | grep -F "libmobi: 0.12" && \
    /out/usr/local/bin/mobitool -e -o /smoke tests/samples/sample-ncx.mobi && \
    test -s /smoke/sample-ncx.epub

FROM eclipse-temurin:21-jre-alpine

# Install fontconfig and Chinese fonts for cover generation
RUN apk add --no-cache fontconfig ttf-dejavu && \
    apk add --no-cache --repository=https://dl-cdn.alpinelinux.org/alpine/edge/testing font-wqy-zenhei

COPY --from=libmobi-builder /out/usr/local/bin/mobitool /usr/local/bin/mobitool
COPY --from=libmobi-builder /out/usr/local/share/licenses/libmobi /usr/local/share/licenses/libmobi

WORKDIR /app

COPY build/libs/server-all.jar app.jar

EXPOSE 8080

ENV TZ=Asia/Shanghai

# JVM memory settings: 512MB initial, 4GB max
CMD ["java", "-Xms512m", "-Xmx4g", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=200", "-jar", "app.jar"]
