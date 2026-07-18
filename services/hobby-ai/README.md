# hobby-ai-service

AI-powered hobby recommendation service for the BidMyHobby platform using AWS Bedrock Nova Micro.

## Features
- AI-powered hobby recommendations using AWS Bedrock Nova Micro model
- User profile management
- Image processing and storage
- RESTful API with Swagger documentation
- Cost-efficient AI integration

## Configuration
- Port: 8088
- Eureka registration: hobby-ai-service
- Database: H2 (file-based)
- AI Provider: AWS Bedrock Nova Micro

## Environment Variables
- `AWS_ACCESS_KEY_ID`: AWS Access Key for Bedrock access
- `AWS_SECRET_ACCESS_KEY`: AWS Secret Key for Bedrock access
- `AWS_DEFAULT_REGION`: AWS region (default: us-east-1)

## API Documentation
- Swagger UI: http://localhost:8088/swagger-ui.html
- API Docs: http://localhost:8088/api-docs

## Local Development
```bash
# Run with Maven
./mvnw spring-boot:run

# Or run with Docker
docker build -t hobby-ai-service .
docker run -p 8088:8088 \
  -e AWS_ACCESS_KEY_ID=your-access-key \
  -e AWS_SECRET_ACCESS_KEY=your-secret-key \
  -e AWS_DEFAULT_REGION=us-east-1 \
  hobby-ai-service
```

## AWS Bedrock Benefits
- Lower cost compared to OpenAI
- Better latency for regional deployments
- No API key management complexity
- Integrated with AWS ecosystem