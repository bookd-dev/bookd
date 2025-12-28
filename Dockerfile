FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/server-all.jar app.jar

EXPOSE 8080

ENV TZ=Asia/Shanghai

CMD ["java", "-jar", "app.jar"]
