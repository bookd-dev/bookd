FROM eclipse-temurin:21-jre-alpine

# Install fontconfig and Chinese fonts for cover generation
RUN apk add --no-cache fontconfig ttf-dejavu && \
    apk add --no-cache --repository=https://dl-cdn.alpinelinux.org/alpine/edge/testing font-wqy-zenhei

WORKDIR /app

COPY build/libs/server-all.jar app.jar

EXPOSE 8080

ENV TZ=Asia/Shanghai

CMD ["java", "-jar", "app.jar"]
