
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create a valid Linux system user
RUN useradd --system --no-create-home sgmart

# Switch to non-root user
USER sgmart

# Copy application JAR
COPY --from=build /workspace/target/SGMart-*.jar app.jar

# Start application
ENTRYPOINT ["java", "-jar", "app.jar"]
