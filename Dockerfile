FROM eclipse-temurin:17-jre-focal

# Add application user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Create app directory
WORKDIR /app

# Copy jar file
COPY target/*.jar app.jar

# Copy config files
COPY src/main/resources/application.properties /app/config/
COPY src/main/resources/application-prod.properties /app/config/

# Set permissions
RUN chown -R appuser:appuser /app
USER appuser

# Expose ports
EXPOSE 8080
EXPOSE 8081

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.config.location=file:/app/config/"]
