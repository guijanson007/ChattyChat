# Stage 1: Build the application
FROM amazoncorretto:21-alpine AS builder
WORKDIR /app

# Copy gradle wrapper and config files first to leverage Docker layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Ensure gradlew is executable inside the container
RUN chmod +x gradlew

# Download dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build the JAR
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Minimal runtime image
FROM amazoncorretto:21-alpine
WORKDIR /app

# Create a non-root system user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built jar from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose default Spring Boot web port
EXPOSE 8080

# Configure JVM memory and entrypoint
ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]