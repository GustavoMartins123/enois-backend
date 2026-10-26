FROM eclipse-temurin:25-jdk-alpine@sha256:1e20ead9eec7234db293f1913438090d533ed47c0b9e12dc8c7627a802a44ed6 AS builder
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -q || true
COPY src src
RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:25-jre-alpine@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048
WORKDIR /app
COPY --from=builder /app/target/rede-0.0.1-SNAPSHOT.jar app.jar
# ffmpeg remove metadados de vídeo/áudio com `-c copy` (sem transcodificar) no proxy de mídia.
RUN apk add --no-cache ffmpeg \
    && mkdir -p /app/data \
    && addgroup -S appgroup && adduser -S appuser -G appgroup \
    && chown -R appuser:appgroup /app
USER appuser
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
