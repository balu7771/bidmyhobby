package com.bidmyhobby.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class AIModerationLambdaService {
    
    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;
    
    @Value("${aws.lambda.function.ai-moderation:bidmyhobby-ai-moderation}")
    private String functionName;
    
    public AIModerationLambdaService(LambdaClient lambdaClient) {
        this.lambdaClient = lambdaClient;
        this.objectMapper = new ObjectMapper();
    }
    
    public ModerationResult moderateItem(MultipartFile file, String name, String description) {
        return moderateItem(file, name, description, "INR");
    }
    
    public ModerationResult moderateItem(MultipartFile file, String name, String description, String currency) {
        try {
            System.out.println("🔍 Invoking Lambda function: " + functionName);
            
            // Prepare Lambda payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("image_data", Base64.getEncoder().encodeToString(file.getBytes()));
            payload.put("name", name);
            payload.put("description", description);
            payload.put("currency", currency != null ? currency : "INR");
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            System.out.println("📤 Payload size: " + payloadJson.length() + " characters");
            
            // Invoke Lambda
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(functionName)
                    .payload(SdkBytes.fromUtf8String(payloadJson))
                    .build();
            
            InvokeResponse response = lambdaClient.invoke(request);
            String responseJson = response.payload().asUtf8String();
            
            System.out.println("📥 Lambda response: " + responseJson);
            
            // Parse response
            JsonNode result = objectMapper.readTree(responseJson);
            
            if (result.has("body")) {
                // Lambda returned wrapped response
                JsonNode body = objectMapper.readTree(result.get("body").asText());
                return parseResult(body, name, description);
            } else {
                // Direct response
                return parseResult(result, name, description);
            }
            
        } catch (Exception e) {
            System.err.println("❌ AI Lambda moderation failed: " + e.getMessage());
            e.printStackTrace();
            return createFallbackResult(name, description);
        }
    }
    
    private ModerationResult parseResult(JsonNode result, String originalName, String originalDescription) {
        try {
            System.out.println("🔍 Parsing result: " + result.toString());
            
            JsonNode moderation = result.get("moderation");
            if (moderation == null) {
                System.err.println("❌ No moderation field in response");
                return createFallbackResult(originalName, originalDescription);
            }
            
            boolean approved = moderation.has("approved") && moderation.get("approved").asBoolean();
            String reason = moderation.has("reason") ? moderation.get("reason").asText() : "No reason provided";
            String itemType = moderation.has("item_type") ? moderation.get("item_type").asText() : "handmade";
            
            double suggestedPrice = result.has("suggested_price") ? result.get("suggested_price").asDouble() : 1000.0;
            String enhancedDescription = result.has("enhanced_description") ? 
                result.get("enhanced_description").asText() : originalDescription;
            
            System.out.println("✅ Parsed - Approved: " + approved + ", Price: " + suggestedPrice);
            
            String fullReason = reason + " " + itemType;
            return new ModerationResult(approved, fullReason, suggestedPrice, enhancedDescription);
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing Lambda response: " + e.getMessage());
            return createFallbackResult(originalName, originalDescription);
        }
    }
    
    private ModerationResult createFallbackResult(String name, String description) {
        // Basic keyword check when Lambda is unavailable
        String combined = (name + " " + description).toLowerCase();
        if (combined.contains("handmade") || combined.contains("craft") || 
            combined.contains("hobby") || combined.contains("creation")) {
            return new ModerationResult(true, "Lambda unavailable - approved based on keywords", 1000.0, description);
        }
        return new ModerationResult(false, "Lambda unavailable - rejected for manual review", 0.0, description);
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
        
        public boolean isApproved() { return approved; }
        public String getReason() { return reason; }
        public double getSuggestedPrice() { return suggestedPrice; }
        public String getEnhancedDescription() { return enhancedDescription; }
    }
}