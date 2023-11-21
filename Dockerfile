# syntax=docker/dockerfile:1
FROM openjdk:11-jdk-slim
COPY --from=top-base /app/top-backend/target/*.jar /usr/src/top-backend/top-backend.jar
WORKDIR /usr/src/top-backend
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "top-backend.jar", "org.springframework.boot.loader.PropertiesLauncher"]
