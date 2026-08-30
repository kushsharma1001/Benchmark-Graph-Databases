# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Optional: containerize the benchmark harness itself, so the CLIENT runs from a
# reproducible image. (The databases run on their own tiers; this is just the
# runner.) Build once, then run from the same region as your cloud instances.
#
#   docker build -t gdbench .
#   docker run --rm --env-file .env gdbench all --platform cognodb
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
COPY config/ config/
RUN ./mvnw -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
RUN useradd --system --uid 10001 appuser
USER appuser
COPY --from=build /app/target/gdbench.jar app.jar
COPY --from=build /app/config/ config/
# Credentials are injected at runtime via --env-file / -e; never baked in.
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--help"]
