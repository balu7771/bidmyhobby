# catalog-service

Catalog management service for BidMyHobby platform with enhanced business card processing.

## Features
- Item catalog management
- Small business listings via business card upload
- AI-powered business card analysis using AWS Bedrock
- Content moderation for business listings
- Instagram integration for business profiles
- Automated information extraction from visiting cards

## New Business Card Features
- Upload business card images
- Extract contact information (phone, email, website)
- Detect Instagram profiles and fetch latest reel links
- AI content moderation using AWS Bedrock Nova Micro
- Automatic approval for appropriate content

## API Endpoints
### Small Business Management
- `POST /api/small-businesses/upload-business-card` - Upload and process business card
- `GET /api/small-businesses` - List all approved businesses
- `GET /api/small-businesses/category/{category}` - Filter by category
- `GET /api/small-businesses/search?keyword=` - Search businesses
- `GET /api/small-businesses/my-businesses?email=` - Get user's businesses

## Configuration
- Port: 8081
- Database: H2/MySQL
- AWS Services: Lambda, Bedrock, Textract, S3

## Environment Variables
- `AWS_ACCESS_KEY_ID`: AWS credentials
- `AWS_SECRET_ACCESS_KEY`: AWS credentials
- `AWS_DEFAULT_REGION`: AWS region
- `AWS_S3_BUCKET_NAME`: S3 bucket for images
