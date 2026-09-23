FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add unprivileged user for security
RUN addgroup -S shuttle && adduser -S shuttle -G shuttle
USER shuttle

# Copy pre-packaged executable JAR
COPY target/*.jar app.jar

# Expose server port
EXPOSE 8080

# Run JVM with container memory awareness
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
