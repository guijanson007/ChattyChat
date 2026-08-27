# Stage 1: Build the application
FROM amazoncorretto:21-alpine AS builder
WORKDIR /app

# 1. Copy only the files needed to resolve dependencies
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew

# 2. Download dependencies into the standard Docker layer
RUN ./gradlew dependencies --no-daemon || true

# 3. Copy source code ONLY after dependencies are cached
COPY src src

# 4. Build the JAR
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Minimal runtime image
FROM amazoncorretto:21-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]