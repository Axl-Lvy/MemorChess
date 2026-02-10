# Build stage
FROM gradle:8-jdk17 AS build
WORKDIR /project
COPY . .
RUN gradle :server:buildFatJar --no-daemon

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /project/server/build/libs/*-all.jar app.jar
COPY --from=build /project/server/src/main/resources/db/migration server/src/main/resources/db/migration
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
