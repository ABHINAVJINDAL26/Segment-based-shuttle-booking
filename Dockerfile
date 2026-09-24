FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add unprivileged user for security
RUN addgroup -S shuttle && adduser -S shuttle -G shuttle
USER shuttle

# Copy the executable JAR produced by the build stage
COPY --from=build /build/target/*.jar app.jar

# Expose server port
EXPOSE 8080

# Run JVM with container memory awareness
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
