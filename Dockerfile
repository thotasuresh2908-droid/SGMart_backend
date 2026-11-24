FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

# Create the app user, and a folder it is allowed to write to (product images).
RUN useradd --system --no-create-home sgmart \
 && mkdir -p /app/data/images \
 && chown -R sgmart:sgmart /app

COPY --from=build --chown=sgmart:sgmart /workspace/target/sgmart-api-0.1.0-SNAPSHOT.jar app.jar

USER sgmart

EXPOSE 9001

ENTRYPOINT ["java", "-jar", "app.jar"]
