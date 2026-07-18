package com.bidmyhobby.catalog.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class AIServiceClient {

    @Value("${ai.service.url:http://localhost:8090}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public AIProcessingResult processItem(MultipartFile file, String name, String description, String email) {
        try {
            System.out.println("🤖 Calling Python AI service for item processing");
            
            Map<String, Object> request = new HashMap<>();
            request.put("imageData", Base64.getEncoder().encodeToString(file.getBytes()));
            request.put("fileName", file.getOriginalFilename());
            request.put("itemName", name);
            request.put("description", description);
            request.put("creatorEmail", email);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            AIProcessingResult result = restTemplate.postForObject(
                aiServiceUrl + "/api/ai/process-item", 
                entity, 
                AIProcessingResult.class
            );

            System.out.println("✅ AI processing completed successfully");
            return result;

        } catch (Exception e) {
            System.err.println("❌ AI service unavailable, using fallback: " + e.getMessage());
            return createFallbackResult(file, name, description);
        }
    }

    private AIProcessingResult createFallbackResult(MultipartFile file, String name, String description) {
        AIProcessingResult fallback = new AIProcessingResult();
        fallback.setProcessedImageUrl("images/" + System.currentTimeMillis() + "-" + file.getOriginalFilename());
        fallback.setEnhancedDescription(description);
        fallback.setSuggestedPrice(1000.0);
        fallback.setModerationPassed(true);
        fallback.setProcessingStatus("FALLBACK");
        return fallback;
    }

    public static class AIProcessingResult {
        private String processedImageUrl;
        private String enhancedDescription;
        private Double suggestedPrice;
        private boolean moderationPassed;
        private String processingStatus;

        // Getters and setters
        public String getProcessedImageUrl() { return processedImageUrl; }
        public void setProcessedImageUrl(String processedImageUrl) { this.processedImageUrl = processedImageUrl; }
        
        public String getEnhancedDescription() { return enhancedDescription; }
        public void setEnhancedDescription(String enhancedDescription) { this.enhancedDescription = enhancedDescription; }
        
        public Double getSuggestedPrice() { return suggestedPrice; }
        public void setSuggestedPrice(Double suggestedPrice) { this.suggestedPrice = suggestedPrice; }
        
        public boolean isModerationPassed() { return moderationPassed; }
        public void setModerationPassed(boolean moderationPassed) { this.moderationPassed = moderationPassed; }
        
        public String getProcessingStatus() { return processingStatus; }
        public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    }
}