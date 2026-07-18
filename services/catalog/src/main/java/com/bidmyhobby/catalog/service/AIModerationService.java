package com.bidmyhobby.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class AIModerationService {
    
    @Value("${ai.moderation.lambda.url:}")
    private String lambdaUrl;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public ModerationResult moderateItem(MultipartFile file, String name, String description) {
        if (lambdaUrl == null || lambdaUrl.isEmpty()) {
            System.out.println("⚠️ AI Lambda URL not configured, skipping moderation");
            return createFallbackResult(name, description);
        }
        
        try {
            // Prepare multipart request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            // Create multipart body
            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("image", file.getResource());
            body.add("name", name);
            body.add("description", description);
            
            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            
            // Call AI service
            ResponseEntity<String> response = restTemplate.postForEntity(lambdaUrl, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode result = objectMapper.readTree(response.getBody());
                return parseResult(result, name, description);
            }
            
        } catch (Exception e) {
            System.err.println("❌ AI moderation failed: " + e.getMessage());
        }
        
        return createFallbackResult(name, description);
    }
    
    private ModerationResult parseResult(JsonNode result, String originalName, String originalDescription) {
        JsonNode moderation = result.get("moderation");
        boolean approved = moderation != null && moderation.get("approved").asBoolean();
        String reason = moderation != null ? moderation.get("reason").asText() : "Unknown";
        String itemType = moderation != null && moderation.has("item_type") ? 
            moderation.get("item_type").asText() : "handmade";
        
        double suggestedPrice = result.has("suggested_price") ? result.get("suggested_price").asDouble() : 1000.0;
        String enhancedDescription = result.has("enhanced_description") ? 
            result.get("enhanced_description").asText() : originalDescription;
        
        // Combine reason and item_type for better category determination
        String fullReason = reason + " " + itemType;
        
        return new ModerationResult(approved, fullReason, suggestedPrice, enhancedDescription);
    }
    
    private ModerationResult createFallbackResult(String name, String description) {
        // Basic keyword check when AI is unavailable
        String combined = (name + " " + description).toLowerCase();
        if (combined.contains("handmade") || combined.contains("craft") || 
            combined.contains("hobby") || combined.contains("creation")) {
            return new ModerationResult(true, "AI service unavailable - approved based on keywords", 1000.0, description);
        }
        return new ModerationResult(false, "AI service unavailable - rejected for manual review", 0.0, description);
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