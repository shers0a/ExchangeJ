# syntax=docker/dockerfile:1

# Two stages: the first one has Maven and the full JDK and builds the jar; the second one only has a JRE
# and the jar, so the final image is small and contains no build tools or source code.
# Image names are fully qualified (docker.io/library/...) so Podman can use them without asking.

# ---- build ----
FROM docker.io/library/maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY engine engine
COPY api api
# the cache mount keeps ~/.m2 between builds, so dependencies are not downloaded every time;
# tests are skipped here because CI runs them before the image is built
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ---- run ----
FROM docker.io/library/eclipse-temurin:25-jre
WORKDIR /app
# do not run as root inside the container
RUN useradd --system --uid 10001 exchange
COPY --from=build /src/api/target/api-*.jar app.jar
USER exchange
EXPOSE 8080
# the JVM sees the container's memory limit; by default it would use only 25% of it for the heap
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# exec form: java is process 1, so it receives the stop signal and Spring shuts the engines down cleanly
ENTRYPOINT ["java", "-jar", "app.jar"]
