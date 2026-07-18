package com.bidmyhobby.catalog.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AIDescriptionService {
    
    public String enhanceDescription(MultipartFile file, String originalDescription) {
        System.out.println("🤖 Enhancing description using AI");
        
        try {
            // Simulate AI description enhancement
            Thread.sleep(1200); // Simulate processing time
            
            // Simple enhancement logic
            StringBuilder enhanced = new StringBuilder(originalDescription);
            
            if (!originalDescription.toLowerCase().contains("condition")) {
                enhanced.append(" This item appears to be in good condition.");
            }
            
            if (!originalDescription.toLowerCase().contains("hobby")) {
                enhanced.append(" Perfect for hobby enthusiasts and collectors.");
            }
            
            enhanced.append(" Verified and processed by AI moderation.");
            
            String result = enhanced.toString();
            System.out.println("✅ Enhanced description generated");
            return result;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ AI description enhancement interrupted");
            return originalDescription; // Return original on error
        }
    }
}