# Stage 1: Build
# Maven image contains both JDK and Maven — the VPS/CI needs neither installed.
FROM maven:3-eclipse-temurin-26@sha256:7db7cc8dfd4e52c1da9d3a56b508909d0d4039a284fde455d928cfc75aec4e2e AS build
WORKDIR /app
# Copy pom first so dependency downloads are cached separately from source changes.
COPY pom.xml .
COPY .mvn .mvn
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: Runtime
# Alpine JRE is ~100MB vs ~300MB for the full JDK image.
FROM eclipse-temurin:21-jre-alpine@sha256:704db3c40204a44f471191446ddd9cda5d60dab40f0e15c6507b815ed897238b
WORKDIR /app
RUN addgroup -S billing && adduser -S billing -G billing
USER billing
COPY --from=build /app/target/billing-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
