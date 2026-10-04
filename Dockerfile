# ---- build ----
# Uses the Gradle bundled in the base image (not the wrapper) so the build never
# has to download a Gradle distribution — the build host may block services.gradle.org.
FROM gradle:9-jdk25 AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle build.gradle lombok.config ./
COPY gradle ./gradle
COPY src ./src
# Tests are skipped: they need the MySQL container up and would slow the build.
RUN gradle bootJar --no-daemon -x test

# ---- run ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

# Dokploy: SPRING_PROFILES_ACTIVE is REQUIRED (stage = test deployment, or prod),
# plus the env vars listed in application-stage.yml / application-prod.yml.
# There is deliberately no default: the app refuses to start without a profile.
RUN useradd --system --no-create-home app
USER app
# Container listens on 8080 (set the Dokploy domain port to 8080). Overridable via SERVER_PORT.
ENV SERVER_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
