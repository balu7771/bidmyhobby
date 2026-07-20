# hobby-ai-service

AI-powered hobby chat and profile service for the BidMyHobby platform using OpenAI.

## Features
- AI-powered hobby conversations using OpenAI
- User profile management
- Image processing and storage
- RESTful API with Swagger documentation
- Cost-efficient AI integration

## Configuration
- Port: 8088
- Eureka registration: hobby-ai-service
- Database: H2 (file-based)
- AI Provider: OpenAI Responses API

## Environment Variables
- `OPENAI_API_KEY`: OpenAI API key
- `OPENAI_MODEL`: Responses API model (default: `gpt-4o-mini`)

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
  -e OPENAI_API_KEY=your-openai-key \
  hobby-ai-service
```
