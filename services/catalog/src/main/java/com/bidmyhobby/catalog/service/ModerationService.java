package com.bidmyhobby.catalog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

@Service
public class ModerationService {
    
    @Value("${openai.api.key:demo-key}")
    private String openaiApiKey;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    public CompletableFuture<Boolean> validateHobbyItem(MultipartFile file, String name, String description) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("🔍 Starting moderation for item: " + name);
                
                // Call OpenAI Vision API to validate if it's a hobby item
                String prompt = String.format(
                    "Is this a hobby-related item? Item name: %s, Description: %s. " +
                    "Return only 'true' if it's a hobby item (collectibles, crafts, games, sports equipment, etc.) " +
                    "or 'false' if it's not.", name, description
                );
                
                // Simulate OpenAI API call (replace with actual implementation)
                boolean isHobbyItem = callOpenAIModerationAPI(prompt, file);
                
                System.out.println("✅ Moderation result for " + name + ": " + isHobbyItem);
                return isHobbyItem;
                
            } catch (Exception e) {
                System.err.println("❌ Moderation failed for item: " + name + " - " + e.getMessage());
                return false; // Reject on error
            }
        });
    }
    
    private boolean callOpenAIModerationAPI(String prompt, MultipartFile file) {
        // Simulate API call - replace with actual OpenAI implementation
        try {
            Thread.sleep(1000); // Simulate API latency
            
            // Simple keyword-based validation for demo
            String content = prompt.toLowerCase();
            return content.contains("hobby") || 
                   content.contains("collectible") || 
                   content.contains("craft") || 
                   content.contains("game") ||
                   content.contains("sport") ||
                   content.contains("art") ||
                   content.contains("music");
                   
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}