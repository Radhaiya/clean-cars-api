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

# Dokploy: SPRING_PROFILES_ACTIVE is REQUIRED (stage = test deployment, or prod),
# plus the env vars listed in application-stage.yml / application-prod.yml.
# There is deliberately no default: the app refuses to start without a profile.
RUN useradd --system --no-create-home app
USER app
EXPOSE 8089
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
