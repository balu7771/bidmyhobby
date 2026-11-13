FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY target/*.jar app.jar
RUN mkdir -p /app/data
VOLUME ["/app/data"]
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
