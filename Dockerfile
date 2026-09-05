# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are cached separately from the sources.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Never run the API as root.
RUN groupadd --system attendance && useradd --system --gid attendance --create-home attendance

COPY --from=build /build/target/attendance-saas.jar app.jar
RUN chown -R attendance:attendance /app
USER attendance

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD ["sh", "-c", "curl -fsS http://localhost:8080/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
