# Bedrock Moderation Migration

## Changes Made

### 1. Updated Dependencies (pom.xml)
- Added `bedrockruntime` AWS SDK dependency for direct Bedrock API calls

### 2. Created BedrockModerationService.java
New service that replaces Lambda-based moderation with direct AWS Bedrock Nova Pro integration:

**Features:**
- Uses `us.amazon.nova-pro-v1:0` model (better quality than Nova Micro)
- Implements AWS Bedrock Guardrails (ID: `urjpbiqc03ne`)
- Supports multimodal input (image + text)
- Extracts information and generates 3-4 line descriptions (max 100 words)
- Accepts: hobby items, old electronics, small business products
- Returns: approval status, category, enhanced description, suggested price

**Prompt Design:**
- Clear acceptance criteria (hobby items, electronics, business products)
- Rejection criteria (weapons, illegal items, inappropriate content)
- Structured JSON response format
- Automatic information extraction from images
- Concise description generation (100 words max)

### 3. Updated CatalogService.java
- Replaced `AIModerationLambdaService` with `BedrockModerationService`
- Updated category detection to include ELECTRONICS and BUSINESS
- Changed default category from HANDMADE to HOBBY

### 4. Guardrails Protection
All moderation requests now use AWS Bedrock Guardrail `urjpbiqc03ne` (DRAFT version) to:
- Filter inappropriate content
- Enforce content policies
- Protect against harmful responses

## Benefits

1. **No Lambda dependency** - Direct API calls, faster response
2. **Better AI model** - Nova Pro vs Nova Micro
3. **Cost efficient** - No Lambda invocation costs
4. **Guardrails** - Built-in content safety
5. **Multimodal** - Analyzes both image and text
6. **Broader acceptance** - Supports electronics and business items
7. **Better descriptions** - AI extracts key details from images

## Testing

Upload a hobby item with:
- Image file
- Name and description
- Email, base price, currency, size

Expected: AI will analyze the image, generate a 3-4 line description, suggest a price, and categorize the item.

## Error Handling

If Bedrock fails, the service returns:
- `approved: false`
- Error message in reason field
- Original description preserved
