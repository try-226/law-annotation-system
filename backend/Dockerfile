FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache wget \
    && addgroup -S app \
    && adduser -S -G app app
WORKDIR /app
COPY --from=builder /workspace/target/*.jar /app/app.jar
RUN mkdir -p /app/data/uploads \
    && chown -R app:app /app

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
