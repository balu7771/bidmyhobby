package com.bidmyhobby.catalog.service;

import com.bidmyhobby.catalog.model.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ItemProcessingService {
    
    @Autowired
    private AIModerationService aiModerationService;
    
    @Autowired
    private ImageProcessingService imageProcessingService;
    
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    
    public CompletableFuture<Item> processItemAsync(MultipartFile file, String name, String description, String email) {
        // Simplified processing - bypass AI services for now
        return processItemSimplified(file, name, description, email);
    }
    
    private CompletableFuture<Item> processItemSimplified(MultipartFile file, String name, String description, String email) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("📦 Processing item: " + name);
                
                // AI Moderation with LangGraph
                AIModerationService.ModerationResult moderation = aiModerationService.moderateItem(file, name, description);
                
                if (!moderation.isApproved()) {
                    throw new RuntimeException("Item rejected: " + moderation.getReason());
                }
                
                // Process image
                String imageUrl;
                try {
                    byte[] processedImage = imageProcessingService.processImage(file.getBytes(), email);
                    imageUrl = "images/" + System.currentTimeMillis() + "-processed-" + file.getOriginalFilename();
                    System.out.println("✅ Image processed with Lambda watermark");
                } catch (Exception e) {
                    System.out.println("⚠️ Lambda not available, using original image: " + e.getMessage());
                    imageUrl = "images/" + System.currentTimeMillis() + "-" + file.getOriginalFilename();
                }
                
                // Create item with AI-enhanced data
                Item item = new Item();
                item.setName(name);
                item.setDescription(moderation.getEnhancedDescription());
                item.setCreatorEmail(email);
                item.setImageUrl(imageUrl);
                item.setSuggestedPrice(moderation.getSuggestedPrice());
                
                System.out.println("✅ Item processed with AI: " + name);
                return item;
                
            } catch (Exception e) {
                System.err.println("❌ Error processing item: " + e.getMessage());
                throw new RuntimeException("Item processing failed", e);
            }
        }, executor);
    }
    
    // TODO: Re-enable when Python AI microservice is ready
    /*
    private CompletableFuture<Item> processItemParallel(MultipartFile file, String name, String description, String email) {
        // Parallel tasks
        CompletableFuture<Double> priceTask = 
            CompletableFuture.supplyAsync(() -> priceAnalysisService.analyzePrice(file, name, description), executor);
            
        CompletableFuture<String> descriptionTask = 
            CompletableFuture.supplyAsync(() -> aiDescriptionService.enhanceDescription(file, description), executor);
            
        CompletableFuture<String> imageTask = 
            CompletableFuture.supplyAsync(() -> {
                try {
                    byte[] processedImage = imageProcessingService.processImage(file.getBytes(), email);
                    return "images/" + System.currentTimeMillis() + "-" + file.getOriginalFilename();
                } catch (Exception e) {
                    throw new RuntimeException("Image processing failed", e);
                }
            }, executor);
        
        // Combine all results
        return CompletableFuture.allOf(priceTask, descriptionTask, imageTask)
            .thenApply(v -> {
                Item item = new Item();
                item.setName(name);
                item.setDescription(descriptionTask.join());
                item.setCreatorEmail(email);
                item.setSuggestedPrice(priceTask.join());
                item.setImageUrl(imageTask.join());
                return item;
            })
            .exceptionally(ex -> {
                System.err.println("❌ Error processing item: " + ex.getMessage());
                throw new RuntimeException("Item processing failed", ex);
            });
    }
    */
}