#!/bin/bash
echo "🚀 Starting catalog-service in LOCAL mode..."
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run