package com.bidmyhobby.catalog.service;

import com.bidmyhobby.catalog.event.NotificationPublisher;
import com.bidmyhobby.catalog.model.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncItemProcessor {

    @Autowired
    private BedrockModerationService bedrockModerationService;

    @Autowired
    private ImageProcessingService imageProcessingService;

    @Autowired
    private S3StorageService s3StorageService;

    @Autowired
    private NotificationPublisher notificationPublisher;

    @Async
    public void processItemBackground(byte[] fileBytes, String contentType, String name, String description,
            String email, String s3Key, Item item) {
        try {
            System.out.println("🔄 Starting async processing for item: " + item.getItemId());

            // AI Detail Generation (Slow)
            BedrockModerationService.ModerationResult aiResult = bedrockModerationService.generateItemDetails(fileBytes,
                    contentType, name, description);

            // Image Processing (Watermark)
            byte[] processedImage = fileBytes;
            try {
                processedImage = imageProcessingService.processImage(fileBytes, email);
                System.out.println("✅ Image processed with Lambda watermark");
            } catch (Exception e) {
                System.out.println("⚠️ Lambda not available: " + e.getMessage());
                // Fallback to original image
            }

            // Upload Image to S3
            s3StorageService.uploadFile(s3Key, processedImage, contentType);

            // Update Item Details
            item.setDescription(aiResult.getEnhancedDescription());
            item.setSuggestedPrice(aiResult.getSuggestedPrice());
            item.setCategory(determineCategory(aiResult.getReason()));
            item.setStatus("ACTIVE");

            // Save Final Item
            s3StorageService.saveItem(item);
            System.out.println("✅ Async processing complete for item: " + item.getItemId());

            // Send Notification
            notificationPublisher.publishItemUploadEvent(
                    item.getItemId(),
                    email,
                    item.getName());

        } catch (Exception e) {
            System.err.println("❌ Async processing failed for item " + item.getItemId() + ": " + e.getMessage());
            e.printStackTrace();
            // Optionally update item status to FAILED
            item.setStatus("FAILED");
            try {
                s3StorageService.saveItem(item);
            } catch (Exception saveEx) {
                // Ignore
            }
        }
    }

    private String determineCategory(String aiReason) {
        String reason = aiReason.toLowerCase();

        if (reason.contains("electronics")) {
            return "ELECTRONICS";
        } else if (reason.contains("business")) {
            return "BUSINESS";
        } else if (reason.contains("painting") || reason.contains("art")) {
            return "ART";
        } else if (reason.contains("craft") || reason.contains("handmade")) {
            return "CRAFTS";
        } else if (reason.contains("pottery") || reason.contains("ceramic")) {
            return "POTTERY";
        } else if (reason.contains("wood") || reason.contains("carving")) {
            return "WOODWORK";
        } else if (reason.contains("textile") || reason.contains("fabric")) {
            return "TEXTILES";
        } else if (reason.contains("jewelry") || reason.contains("accessory")) {
            return "JEWELRY";
        }

        return "HOBBY"; // Default category
    }
}
