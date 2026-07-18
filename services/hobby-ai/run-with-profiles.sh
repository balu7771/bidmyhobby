#!/bin/bash
echo "Running hobby-ai-backend with profile initialization enabled"
mvn spring-boot:run -Dspring-boot.run.arguments="--startup-actions.initializeProfiles=true"
