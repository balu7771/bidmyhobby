package com.bidmyhobby.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

@Service
public class BedrockModerationService {

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.bedrock.region:ap-south-1}")
    private String awsBedrockRegion;

    @Value("${aws.bedrock.model-id:apac.amazon.nova-pro-v1:0}")
    private String bedrockModelId;

    @Value("${aws.bedrock.guardrail.id}")
    private String guardrailId;

    @Value("${aws.bedrock.guardrail.version:DRAFT}")
    private String guardrailVersion;

    public BedrockModerationService(@Value("${aws.bedrock.region:ap-south-1}") String awsBedrockRegion) {
        this.bedrockClient = BedrockRuntimeClient.builder()
                .region(Region.of(awsBedrockRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // Sync method for quick guardrail check
    public ModerationResult moderateItemSync(MultipartFile file, String name, String description) {
        try {
            // Simplified prompt just for safety check if guardrail doesn't catch it
            String prompt = "Check if this item is safe to list on a hobby marketplace. Respond JSON: {\"approved\": true/false, \"reason\": \"...\"}";

            // Normalize image bytes to JPEG to avoid MIME type mismatch with Bedrock
            byte[] originalBytes = file.getBytes();
            String detectedFormat = detectFormatFromBytes(originalBytes);
            byte[] normalizedBytes = normalizeToJpeg(originalBytes);
            String normalizedContentType = "image/jpeg";

            System.out.println("📸 Detected image format: " + detectedFormat + ", normalized to JPEG for Bedrock");

            return callBedrock(normalizedBytes, normalizedContentType, prompt, name, description, true);

        } catch (Exception e) {
            System.err.println("❌ Bedrock sync moderation failed: " + e.getMessage());
            return new ModerationResult(false, "AI moderation error: " + e.getMessage(), 0.0, description);
        }
    }

    // Async method for detailed generation
    public ModerationResult generateItemDetails(byte[] fileBytes, String contentType, String name, String description) {
        try {
            String prompt = buildDetailGenerationPrompt(name, description);
            // Normalize image bytes to JPEG to avoid MIME type mismatch with Bedrock
            byte[] normalizedBytes = normalizeToJpeg(fileBytes);
            return callBedrock(normalizedBytes, "image/jpeg", prompt, name, description, false);
        } catch (Exception e) {
            System.err.println("❌ Bedrock detail generation failed: " + e.getMessage());
            return new ModerationResult(true, "Detail generation failed", 0.0, description);
        }
    }

    // Method for business card extraction
    public BusinessCardResult extractBusinessCardInfo(byte[] fileBytes, String contentType, String userProvidedName,
            String userProvidedDescription) {
        try {
            String prompt = buildBusinessCardExtractionPrompt(userProvidedName, userProvidedDescription);
            return callBedrockForBusinessCard(fileBytes, contentType, prompt, userProvidedName,
                    userProvidedDescription);
        } catch (Exception e) {
            System.err.println("❌ Bedrock business card extraction failed: " + e.getMessage());
            return new BusinessCardResult(false, "Business card extraction error: " + e.getMessage(), null);
        }
    }

    private ModerationResult callBedrock(byte[] imageBytes, String contentType, String prompt, String name,
            String originalDescription, boolean isSyncCheck) throws Exception {
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");

        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("image", Map.of(
                "format", getImageFormat(contentType),
                "source", Map.of("bytes", imageBase64)));

        Map<String, String> textContent = new HashMap<>();
        textContent.put("text", prompt);

        message.put("content", new Object[] { imageContent, textContent });
        requestBody.put("messages", new Object[] { message });

        Map<String, Object> inferenceConfig = new HashMap<>();
        inferenceConfig.put("max_new_tokens", 500); // 500 for sync? maybe less for sync
        inferenceConfig.put("temperature", 0.3);
        requestBody.put("inferenceConfig", inferenceConfig);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        InvokeModelRequest.Builder requestBuilder = InvokeModelRequest.builder()
                .modelId(bedrockModelId)
                .body(SdkBytes.fromUtf8String(jsonBody));

        // Only apply guardrail for the sync check to save time/cost on the second call
        // if needed,
        // or apply on both to be safe. Let's apply on both for now.
        requestBuilder.guardrailIdentifier(guardrailId)
                .guardrailVersion(guardrailVersion);

        InvokeModelResponse response = bedrockClient.invokeModel(requestBuilder.build());
        String responseBody = response.body().asUtf8String();

        return parseBedrockResponse(responseBody, name, originalDescription, isSyncCheck);
    }

    private String buildDetailGenerationPrompt(String name, String description) {
        return "Analyze this item for BidMyHobby platform. Generate enhanced description, price, and category.\n\n" +
                "Item: " + name + "\nDescription: " + description + "\n\n" +
                "Respond in JSON format:\n" +
                "{\"approved\": true, \"reason\": \"ok\", \"category\": \"hobby/electronics/business/other\", " +
                "\"enhanced_description\": \"3-4 lines, max 100 words, extract key details from image and text\", " +
                "\"suggested_price\": estimated_value_in_INR}";
    }

    private String buildBusinessCardExtractionPrompt(String userProvidedName, String userProvidedDescription) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(
                "Extract business information from this visiting card image for a small business directory.\\n\\n");

        if (userProvidedName != null && !userProvidedName.trim().isEmpty()) {
            prompt.append("User provided business name: ").append(userProvidedName).append("\\n");
        }
        if (userProvidedDescription != null && !userProvidedDescription.trim().isEmpty()) {
            prompt.append("User provided description: ").append(userProvidedDescription).append("\\n");
        }

        prompt.append("\\nExtract the following information from the visiting card:\\n");
        prompt.append("- Business name (if not provided by user)\\n");
        prompt.append("- Business description (if not provided by user, create brief description based on card)\\n");
        prompt.append("- Phone number\\n");
        prompt.append("- Email address\\n");
        prompt.append("- Website URL\\n");
        prompt.append("- Instagram handle or profile URL\\n");
        prompt.append("- Facebook profile URL or page name\\n");
        prompt.append("- Physical location/address\\n");
        prompt.append("- Business category (e.g., restaurant, salon, retail, services, etc.)\\n\\n");
        prompt.append("Respond in JSON format:\\n");
        prompt.append("{\\\"approved\\\": true/false, \\\"reason\\\": \\\"approval reason\\\", ");
        prompt.append("\\\"business_name\\\": \\\"extracted or user-provided name\\\", ");
        prompt.append("\\\"description\\\": \\\"extracted or user-provided description\\\", ");
        prompt.append("\\\"phone\\\": \\\"phone number\\\", ");
        prompt.append("\\\"email\\\": \\\"email address\\\", ");
        prompt.append("\\\"website\\\": \\\"website URL\\\", ");
        prompt.append("\\\"instagram\\\": \\\"Instagram handle or URL\\\", ");
        prompt.append("\\\"facebook\\\": \\\"Facebook profile or page\\\", ");
        prompt.append("\\\"location\\\": \\\"physical address or city\\\", ");
        prompt.append("\\\"category\\\": \\\"business category\\\"}");

        return prompt.toString();
    }

    private ModerationResult parseBedrockResponse(String responseBody, String originalName,
            String originalDescription, boolean isSyncCheck) {
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            String aiText = jsonResponse.get("output").get("message").get("content").get(0).get("text").asText();

            // Cleanup potential markdown blocks if Bedrock returns ```json ... ```
            if (aiText.startsWith("```json")) {
                aiText = aiText.substring(7);
                if (aiText.endsWith("```")) {
                    aiText = aiText.substring(0, aiText.length() - 3);
                }
            } else if (aiText.startsWith("```")) {
                aiText = aiText.substring(3);
                if (aiText.endsWith("```")) {
                    aiText = aiText.substring(0, aiText.length() - 3);
                }
            }

            JsonNode result = objectMapper.readTree(aiText);

            boolean approved = result.has("approved") ? result.get("approved").asBoolean() : true;
            String reason = result.has("reason") ? result.get("reason").asText() : "No reason provided";

            if (isSyncCheck) {
                return new ModerationResult(approved, reason, 0.0, originalDescription);
            }

            String category = result.has("category") ? result.get("category").asText() : "hobby";
            double suggestedPrice = result.has("suggested_price") ? result.get("suggested_price").asDouble() : 0.0;
            String enhancedDescription = result.has("enhanced_description")
                    ? result.get("enhanced_description").asText()
                    : originalDescription;

            return new ModerationResult(approved, reason + " [" + category + "]", suggestedPrice, enhancedDescription);

        } catch (Exception e) {
            System.err.println("❌ Error parsing Bedrock response: " + e.getMessage());
            return new ModerationResult(true, "Auto-approved (parsing error)", 0.0, originalDescription);
        }
    }

    private String getImageFormat(String contentType) {
        if (contentType == null)
            return "jpeg";
        if (contentType.contains("png"))
            return "png";
        if (contentType.contains("gif"))
            return "gif";
        if (contentType.contains("webp"))
            return "webp";
        return "jpeg";
    }

    /**
     * Detects the actual image format from the file's magic bytes (header bytes),
     * independent of the HTTP content-type header which may be incorrect on mobile.
     */
    private String detectFormatFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return "jpeg";
        // PNG: 0x89 0x50 0x4E 0x47
        if (bytes[0] == (byte)0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) return "png";
        // JPEG: 0xFF 0xD8 0xFF
        if (bytes[0] == (byte)0xFF && bytes[1] == (byte)0xD8 && bytes[2] == (byte)0xFF) return "jpeg";
        // GIF: "GIF8"
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38) return "gif";
        // WebP: bytes 8-11 = "WEBP"
        if (bytes.length >= 12 && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) return "webp";
        return "jpeg"; // default assumption
    }

    /**
     * Normalizes any image (JPEG, PNG, WEBP, etc.) to JPEG bytes using Java's built-in
     * ImageIO. This ensures Bedrock always receives a format it expects and avoids
     * content-type mismatches caused by incorrect HTTP headers from mobile browsers.
     */
    private byte[] normalizeToJpeg(byte[] originalBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (img == null) {
                System.out.println("⚠️ ImageIO could not decode image, using original bytes");
                return originalBytes;
            }
            // If image has alpha channel (ARGB), convert to RGB first to avoid JPEG encoding issues
            if (img.getType() == BufferedImage.TYPE_INT_ARGB || img.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
                BufferedImage rgbImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                rgbImg.createGraphics().drawImage(img, 0, 0, java.awt.Color.WHITE, null);
                img = rgbImg;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpeg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            System.out.println("⚠️ Image normalization failed, using original bytes: " + e.getMessage());
            return originalBytes;
        }
    }

    private BusinessCardResult callBedrockForBusinessCard(byte[] imageBytes, String contentType, String prompt,
            String userProvidedName, String userProvidedDescription) throws Exception {
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");

        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("image", Map.of(
                "format", getImageFormat(contentType),
                "source", Map.of("bytes", imageBase64)));

        Map<String, String> textContent = new HashMap<>();
        textContent.put("text", prompt);

        message.put("content", new Object[] { imageContent, textContent });
        requestBody.put("messages", new Object[] { message });

        Map<String, Object> inferenceConfig = new HashMap<>();
        inferenceConfig.put("max_new_tokens", 500);
        inferenceConfig.put("temperature", 0.3);
        requestBody.put("inferenceConfig", inferenceConfig);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        InvokeModelRequest.Builder requestBuilder = InvokeModelRequest.builder()
                .modelId(bedrockModelId)
                .body(SdkBytes.fromUtf8String(jsonBody));

        // Note: Guardrail is NOT applied for business card extraction
        // Business cards are legitimate data extraction tasks, not content that needs
        // moderation
        // The guardrail is designed for marketplace item moderation and would reject
        // business card prompts

        InvokeModelResponse response = bedrockClient.invokeModel(requestBuilder.build());
        String responseBody = response.body().asUtf8String();

        return parseBusinessCardResponse(responseBody, userProvidedName, userProvidedDescription);
    }

    private BusinessCardResult parseBusinessCardResponse(String responseBody, String userProvidedName,
            String userProvidedDescription) {
        try {
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            // Check for guardrail intervention
            if (jsonResponse.has("stopReason")
                    && "GUARDRAIL_INTERVENED".equals(jsonResponse.get("stopReason").asText())) {
                System.err.println("⚠️ Guardrail intervened on business card");
                return new BusinessCardResult(false, "Business card content was blocked by content filters", null);
            }

            String aiText = jsonResponse.get("output").get("message").get("content").get(0).get("text").asText();
            System.out.println("📄 AI Response Text: " + aiText);

            // Cleanup potential markdown blocks
            if (aiText.startsWith("```json")) {
                aiText = aiText.substring(7);
                if (aiText.endsWith("```")) {
                    aiText = aiText.substring(0, aiText.length() - 3);
                }
            } else if (aiText.startsWith("```")) {
                aiText = aiText.substring(3);
                if (aiText.endsWith("```")) {
                    aiText = aiText.substring(0, aiText.length() - 3);
                }
            }

            aiText = aiText.trim();

            // Check if response is not JSON (e.g., starts with "Sorry" or other text)
            if (!aiText.startsWith("{")) {
                System.err.println("❌ AI returned non-JSON response: " + aiText);
                return new BusinessCardResult(false, "Unable to process business card. AI response: "
                        + aiText.substring(0, Math.min(100, aiText.length())), null);
            }

            JsonNode result = objectMapper.readTree(aiText);

            boolean approved = result.has("approved") ? result.get("approved").asBoolean() : true;
            String reason = result.has("reason") ? result.get("reason").asText() : "No reason provided";

            if (!approved) {
                return new BusinessCardResult(false, reason, null);
            }

            // Extract business information
            Map<String, String> businessInfo = new HashMap<>();

            // Use user-provided values if available, otherwise use extracted values
            businessInfo.put("business_name",
                    (userProvidedName != null && !userProvidedName.trim().isEmpty())
                            ? userProvidedName
                            : (result.has("business_name") ? result.get("business_name").asText() : ""));

            businessInfo.put("description",
                    (userProvidedDescription != null && !userProvidedDescription.trim().isEmpty())
                            ? userProvidedDescription
                            : (result.has("description") ? result.get("description").asText() : ""));

            businessInfo.put("phone", result.has("phone") ? result.get("phone").asText() : "");
            businessInfo.put("email", result.has("email") ? result.get("email").asText() : "");
            businessInfo.put("website", result.has("website") ? result.get("website").asText() : "");
            businessInfo.put("instagram", result.has("instagram") ? result.get("instagram").asText() : "");
            businessInfo.put("facebook", result.has("facebook") ? result.get("facebook").asText() : "");
            businessInfo.put("location", result.has("location") ? result.get("location").asText() : "");
            businessInfo.put("category", result.has("category") ? result.get("category").asText() : "");

            return new BusinessCardResult(true, reason, businessInfo);

        } catch (Exception e) {
            System.err.println("❌ Error parsing business card response: " + e.getMessage());
            e.printStackTrace();
            return new BusinessCardResult(false, "Parsing error: " + e.getMessage(), null);
        }
    }

    public static class BusinessCardResult {
        private final boolean approved;
        private final String reason;
        private final Map<String, String> businessInfo;

        public BusinessCardResult(boolean approved, String reason, Map<String, String> businessInfo) {
            this.approved = approved;
            this.reason = reason;
            this.businessInfo = businessInfo;
        }

        public boolean isApproved() {
            return approved;
        }

        public String getReason() {
            return reason;
        }

        public Map<String, String> getBusinessInfo() {
            return businessInfo;
        }
    }

    public static class ModerationResult {
        private final boolean approved;
        private final String reason;
        private final double suggestedPrice;
        private final String enhancedDescription;

        public ModerationResult(boolean approved, String reason, double suggestedPrice, String enhancedDescription) {
            this.approved = approved;
            this.reason = reason;
            this.suggestedPrice = suggestedPrice;
            this.enhancedDescription = enhancedDescription;
        }

        public boolean isApproved() {
            return approved;
        }

        public String getReason() {
            return reason;
        }

        public double getSuggestedPrice() {
            return suggestedPrice;
        }

        public String getEnhancedDescription() {
            return enhancedDescription;
        }
    }
}
