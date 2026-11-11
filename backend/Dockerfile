# syntax=docker/dockerfile:1.7
# Spring API image. Multi-stage: the JDK and Gradle stay in the build stage; the runtime image is
# a JRE with the application split into layers (dependencies change rarely, so pushes are small).
# CI builds it for linux/arm64 (Graviton on ECS): docker buildx build --platform linux/arm64 .

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# Dependencies first: this layer is reused until the build file changes.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q dependencies > /dev/null
COPY src/main src/main
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q bootJar
RUN java -Djarmode=tools -jar "$(ls build/libs/*.jar | grep -v plain)" extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
EXPOSE 8081
# Container-aware heap sizing comes from JAVA_TOOL_OPTIONS (-XX:MaxRAMPercentage=75 in ECS).
# Extra arguments (e.g. the migration task's --lms.migrate-only=true) are appended by ECS.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
