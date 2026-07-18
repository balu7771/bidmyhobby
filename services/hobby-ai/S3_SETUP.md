# Hobby AI Backend - S3 & Bedrock Knowledge Base Setup

## S3 Bucket Structure
```
s3://bidmyhobby-ai-profiles/
├── images/
│   ├── 017e4530-49b4-4937-8adf-985a82595d53.jpg
│   ├── 018aafd0-6a0d-4f38-b2ca-45c0c9b13fa2.jpg
│   └── ... (all profile images)
└── profiles.json
```

## Setup Steps

### 1. Upload to S3 (Manual)

Upload profiles and images to S3:
```bash
# Upload profiles.json, done manually.
#aws s3 cp profiles.json s3://bidmyhobby-ai-profiles/profiles.json

# Upload all images
#aws s3 sync ./images/ s3://bidmyhobby-ai-profiles/images/
```

### 2. Create Bedrock Knowledge Base

#### Via AWS Console:
1. Go to AWS Bedrock → Knowledge Bases → Create
2. Configure:
   - **Name**: `hobby-ai-profiles-kb`
   - **Data source**: S3
   - **S3 URI**: `s3://bidmyhobby-ai-profiles/profiles.json`
   - **Embedding model**: `amazon.titan-embed-text-v1`
   - **Vector store**: OpenSearch Serverless (create new)
3. Create and wait for sync
4. Copy Knowledge Base ID

### 3. Configure Application

Add to `.env`:
```bash
AWS_S3_BUCKET_NAME=bidmyhobby-ai-profiles
AWS_BEDROCK_KB_ID=<your-knowledge-base-id>
```

### 4. Run Application

The app will automatically:
- Load profiles from S3 on startup
- Update image URLs to S3 paths
- Enable semantic search via Bedrock KB

```bash
./mvnw spring-boot:run
```

### 5. Test Search API

```bash
# Search for Indian painter
curl "http://localhost:8088/api/profiles/search?query=Indian%20painter&maxResults=5"

# Search for fitness enthusiast
curl "http://localhost:8088/api/profiles/search?query=fitness%20enthusiast"

# Search for software engineer
curl "http://localhost:8088/api/profiles/search?query=software%20engineer%20who%20loves%20coding"
```

## API Endpoints

### Search Profiles (Semantic)
```
GET /api/profiles/search?query={query}&maxResults={count}
```

**Example Response:**
```json
[
  {
    "id": "d69116e9-2e3e-45b0-a2f5-5ee28817b475",
    "firstName": "Aisha",
    "lastName": "Patel",
    "age": 32,
    "ethnicity": "Indian",
    "gender": "FEMALE",
    "bio": "Yoga instructor with a love for mindfulness...",
    "imageUrl": "https://bidmyhobby-ai-profiles.s3.amazonaws.com/images/d69116e9-2e3e-45b0-a2f5-5ee28817b475.jpg",
    "mainHobby": "Yoga"
  }
]
```

## How It Works

1. **Startup**: App loads profiles from `s3://bidmyhobby-ai-profiles/profiles.json`
2. **Image URLs**: Automatically converted to S3 URLs
3. **Search**: Uses Bedrock Knowledge Base for semantic matching
4. **Results**: Returns full profile objects with S3 image URLs

## Environment Variables

```bash
AWS_S3_AI_BUCKET_NAME=bidmyhobby-ai-profiles
AWS_ACCESS_KEY_ID=<your-access-key>
AWS_SECRET_ACCESS_KEY=<your-secret-key>
AWS_DEFAULT_REGION=ap-south-1
AWS_BEDROCK_KB_ID=<your-knowledge-base-id>
```
