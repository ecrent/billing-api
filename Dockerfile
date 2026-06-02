# Stage 1: Build
# Maven image contains both JDK and Maven — the VPS/CI needs neither installed.
FROM maven:3.9-eclipse-temurin-21@sha256:52182d56c0cf03e906a4503a77de81323706e57f0cb4ed3acbfa1054cbff1bae AS build
WORKDIR /app
# Copy pom first so dependency downloads are cached separately from source changes.
COPY pom.xml .
COPY .mvn .mvn
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: Runtime
# Alpine JRE is ~100MB vs ~300MB for the full JDK image.
FROM eclipse-temurin:25-jre-alpine@sha256:c707c0d18cb9e8556380719f80d96a7529d0746fbb42143893949b98ed2f8943
WORKDIR /app
RUN addgroup -S billing && adduser -S billing -G billing
USER billing
COPY --from=build /app/target/billing-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
