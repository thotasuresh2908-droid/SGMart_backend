FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --no-create-home sgmart

COPY --from=build /workspace/target/sgmart-api-0.1.0-SNAPSHOT.jar app.jar

USER sgmart

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
