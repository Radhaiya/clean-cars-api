# ---- build ----
# Gradle downloads 9.7.1 via the wrapper; the base image only supplies the JDK.
FROM gradle:9-jdk25 AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle build.gradle lombok.config ./
COPY gradle ./gradle
COPY src ./src
# Tests are skipped: they need the MySQL container up and would slow the build.
RUN ./gradlew bootJar --no-daemon -x test

# ---- run ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
