FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/*.jar app.jar
RUN mkdir -p /app/data
VOLUME ["/app/data"]
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]