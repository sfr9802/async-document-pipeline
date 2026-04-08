FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Copy Gradle wrapper and build config first (cache layer)
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY api/build.gradle api/build.gradle
COPY job/build.gradle job/build.gradle
COPY infra-queue/build.gradle infra-queue/build.gradle
COPY infra-storage/build.gradle infra-storage/build.gradle
COPY support/build.gradle support/build.gradle

RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies --quiet || true

# Copy source and build
COPY . .
RUN chmod +x ./gradlew && ./gradlew --no-daemon :api:bootJar

# Runtime image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

COPY --from=builder /workspace/api/build/libs/*.jar app.jar

EXPOSE 8082
USER app

ENTRYPOINT ["java", "-jar", "app.jar"]
