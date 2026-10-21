# syntax=docker/dockerfile:1

# ---------- build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . /workspace
# The New Relic agent jar is untracked in git (added to .gitignore in an earlier
# commit). The image runs without it by default; mount it in at deploy time if
# you want APM.
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon -x test

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd --system --uid 10001 --create-home appuser
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
COPY --chown=appuser:appuser src/main/resources/application.yml /app/config/application.yml
# Writable directory for multipart product-image uploads; mount a volume here in prod.
RUN mkdir -p /app/uploads/products && chown -R appuser:appuser /app/uploads
USER appuser
EXPOSE 3001
# Boot reads env-based overrides (PORT, username, password, JWT_SECRET, ...)
# from the runtime environment -- see README's "Configuration" section.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
