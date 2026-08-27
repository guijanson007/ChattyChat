# Stage 1: Build the application
FROM amazoncorretto:21-alpine AS builder
WORKDIR /app

# Copy gradle wrapper and definition files first
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

# Pre-fetch Gradle wrapper & dependencies using dedicated cache directories
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src src

# Build JAR leveraging Gradle cache
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon -x test

# Stage 2: Minimal runtime image
FROM amazoncorretto:21-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]