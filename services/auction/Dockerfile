FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]