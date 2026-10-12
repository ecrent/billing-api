# Stage 1: Build
# Maven image contains both JDK and Maven — the VPS/CI needs neither installed.
FROM maven:3.9-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build
WORKDIR /app
# Copy pom first so dependency downloads are cached separately from source changes.
COPY pom.xml .
COPY .mvn .mvn
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: Runtime
# Alpine JRE is ~100MB vs ~300MB for the full JDK image.
FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
WORKDIR /app
# Pull fixed OS packages (openssl/libexpat/sqlite/p11-kit HIGHs) on top of the
# pinned base digest; rebuilds always get the current patched versions.
RUN apk upgrade --no-cache
RUN addgroup -S billing && adduser -S billing -G billing
USER billing
COPY --from=build /app/target/billing-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
