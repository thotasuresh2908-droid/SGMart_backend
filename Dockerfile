
# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create application user
RUN useradd --system --no-create-home sgmart

# Create data directory and grant permissions
RUN mkdir -p /app/data \
    && chown -R sgmart:sgmart /app

# Copy application jar
COPY --from=build /workspace/target/sgmart-api-0.1.0-SNAPSHOT.jar app.jar

# Ensure application user owns application files
RUN chown -R sgmart:sgmart /app

# Run as non-root user
USER sgmart

EXPOSE 9001

ENTRYPOINT ["java", "-jar", "app.jar"]
