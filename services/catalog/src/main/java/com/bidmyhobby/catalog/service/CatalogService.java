package com.bidmyhobby.catalog.service;

import com.bidmyhobby.catalog.model.Item;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.math.BigDecimal;

@Service
public class CatalogService {

    // public static Logger log = LoggerFactory.getLogger(CatalogService.class);

    @Autowired
    ImageProcessingService imageProcessingService;

    @Autowired
    S3StorageService s3StorageService;

    @Autowired
    BedrockModerationService bedrockModerationService;

    @Autowired
    com.bidmyhobby.catalog.event.NotificationPublisher notificationPublisher;

    public List<Item> getAllItems() {
        return s3StorageService.getAllItems();
    }

    public Item getItem(String itemId) {
        return s3StorageService.getItem(itemId);
    }

    public Item uploadItem(List<MultipartFile> files, String name, String description, String email, Double basePrice,
            String currency, String size, String city, String userCategory) throws Exception {
        // Email/phone validation is optional now – authentication is based on WhatsApp number.
        if (files == null || files.isEmpty()) {
            throw new RuntimeException("At least one file is required");
        }
        if (files.size() > 4) {
            throw new RuntimeException("Maximum 4 files allowed per item");
        }

        // Validate basePrice — BUSINESS items may have 0 (contact for price)
        if (basePrice == null || basePrice < 0) {
            throw new RuntimeException("Valid base price is required (use 0 for \"Contact for price\")");
        }
        // Require a positive price only for non-business items
        if (basePrice == 0 && (size == null || !size.equalsIgnoreCase("N/A"))) {
            // Allow 0 for BUSINESS uploads (size will be "N/A")
            // For HOBBY items, a price must be set
            if (description == null || !description.toLowerCase().contains("business")) {
                // We can't know item type here without an explicit param, so just allow 0
                // and let the async AI moderation determine the right category
            }
        }

        // Validate currency
        if (currency == null || currency.trim().isEmpty()) {
            currency = "INR"; // Default currency
        }

        // Validate size (required field)
        if (size == null || size.trim().isEmpty()) {
            throw new RuntimeException("Size is required");
        }

        // 1. Synchronous Guardrail Check (Fast)
        System.out.println("🤖 Performing fast guardrail check...");
        BedrockModerationService.ModerationResult syncResult = bedrockModerationService.moderateItemSync(files.get(0), name,
                description);

        if (!syncResult.isApproved()) {
            throw new RuntimeException("Item rejected by AI moderation: " + syncResult.getReason());
        }

        // 2. Prepare for Async Processing
        List<byte[]> filesBytes = new java.util.ArrayList<>();
        List<String> contentTypes = new java.util.ArrayList<>();
        List<String> s3Keys = new java.util.ArrayList<>();
        List<String> imageUrls = new java.util.ArrayList<>();

        for (MultipartFile file : files) {
            filesBytes.add(file.getBytes());
            contentTypes.add(file.getContentType());
            String s3Key = "images/" + System.currentTimeMillis() + "-" + file.getOriginalFilename();
            s3Keys.add(s3Key);
            imageUrls.add(s3StorageService.generateImageUrl(s3Key));
        }

        // Create initial Item
        Item item = new Item();
        item.setItemId(s3Keys.get(0));
        item.setName(name);
        item.setDescription(description); // Initial description
        item.setCreatorEmail(email);
        item.setBasePrice(BigDecimal.valueOf(basePrice));
        item.setSuggestedPrice(0.0); // Placeholder
        item.setCurrency(currency);
        item.setSize(size);
        item.setCity(city);
        item.setCategory("PROCESSING"); // Temporary category or status indicator
        item.setStatus("PROCESSING");
        item.setImageUrl(imageUrls.get(0)); // URL will be valid once uploaded
        item.setImageUrls(imageUrls);

        // Save initial state
        s3StorageService.saveItem(item);

        // 3. Trigger Async Background Processing
        processItemBackground(filesBytes, contentTypes, name, description, email, s3Keys, item, userCategory);

        System.out.println("⏳ Item upload accepted, processing in background: " + item.getItemId());
        return item;
    }

    @org.springframework.scheduling.annotation.Async
    public void processItemBackground(List<byte[]> filesBytes, List<String> contentTypes, String name, String description,
            String email, List<String> s3Keys, Item item, String userCategory) {
        try {
            System.out.println("🔄 Starting async processing for item: " + item.getItemId());

            boolean isBusinessCard = "BUSINESS".equalsIgnoreCase(userCategory);

            if (isBusinessCard) {
                // Business cards: call Python Lambda for Textract extraction — no price estimation
                System.out.println("🪪 Business card detected — calling Lambda for text extraction");
                try {
                    java.util.Map<String, Object> lambdaInput = new java.util.HashMap<>();
                    lambdaInput.put("imageData", java.util.Base64.getEncoder().encodeToString(filesBytes.get(0)));
                    lambdaInput.put("email", email);
                    lambdaInput.put("isBusinessCard", true);

                    java.util.Map<String, Object> lambdaResult = imageProcessingService.processBusinessCard(lambdaInput);

                    // Enrich description with extracted contact info
                    Object bizInfoObj = lambdaResult.get("businessInfo");
                    if (bizInfoObj instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> bizInfo = (java.util.Map<String, Object>) bizInfoObj;
                        StringBuilder enriched = new StringBuilder(description);
                        appendIfPresent(enriched, "Phone", bizInfo.get("phone"));
                        appendIfPresent(enriched, "Email", bizInfo.get("email"));
                        appendIfPresent(enriched, "Website", bizInfo.get("website"));
                        appendIfPresent(enriched, "Instagram", bizInfo.get("instagram"));
                        item.setDescription(enriched.toString());
                    }

                    // Use the Lambda-processed (watermarked) image
                    Object processedB64 = lambdaResult.get("processedImage");
                    byte[] processedBytes = processedB64 != null
                            ? java.util.Base64.getDecoder().decode((String) processedB64)
                            : filesBytes.get(0);
                    s3StorageService.uploadFile(s3Keys.get(0), processedBytes, contentTypes.get(0));

                } catch (Exception e) {
                    System.out.println("⚠️ Lambda extraction failed, using original: " + e.getMessage());
                    s3StorageService.uploadFile(s3Keys.get(0), filesBytes.get(0), contentTypes.get(0));
                }

                // Upload remaining images (if any) without business-card processing
                for (int i = 1; i < filesBytes.size(); i++) {
                    s3StorageService.uploadFile(s3Keys.get(i), filesBytes.get(i), contentTypes.get(i));
                }

                item.setCategory("BUSINESS");
                item.setSuggestedPrice(null); // No price for business listings
                item.setStatus("ACTIVE");

            } else {
                // Hobby items: AI detail generation + price estimation
                BedrockModerationService.ModerationResult aiResult = bedrockModerationService.generateItemDetails(
                        filesBytes.get(0), contentTypes.get(0), name, description);

                if (!aiResult.isApproved()) {
                    throw new RuntimeException("Rejected by AI Moderation: " + aiResult.getReason());
                }

                // Watermark every image via Lambda
                for (int i = 0; i < filesBytes.size(); i++) {
                    byte[] processedImage;
                    try {
                        processedImage = imageProcessingService.processImage(filesBytes.get(i), email, false);
                        System.out.println("✅ Image watermarked");
                    } catch (Exception e) {
                        System.out.println("⚠️ Image processing failed, using original: " + e.getMessage());
                        processedImage = filesBytes.get(i);
                    }
                    s3StorageService.uploadFile(s3Keys.get(i), processedImage, contentTypes.get(i));
                }

                item.setDescription(aiResult.getEnhancedDescription());
                item.setSuggestedPrice(aiResult.getSuggestedPrice());
                item.setCategory(determineCategory(aiResult.getReason()));
                item.setStatus("ACTIVE");
            }

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
                notificationPublisher.publishItemRejectedEvent(item.getItemId(), email, item.getName(), e.getMessage());
            } catch (Exception saveEx) {
                // Ignore
            }
        }
    }

    public Item saveProcessedItem(Item processedItem) {
        try {
            return s3StorageService.saveItem(processedItem);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save processed item: " + e.getMessage(), e);
        }
    }

    public List<Item> getItemsByCreator(String email) {
        return s3StorageService.getItemsByCreator(email);
    }

    public List<Item> getActiveItems() {
        return s3StorageService.getActiveItems();
    }

    public boolean deleteItem(String itemId, String email) {
        Item item = s3StorageService.getItem(itemId);
        if (item == null) {
            throw new RuntimeException("Item not found");
        }

        // Admin bypass: platform admin can delete any item
        boolean isAdmin = email != null && email.replaceAll("[^0-9]", "").endsWith("9353035490");

        if (!isAdmin && !item.getCreatorEmail().equals(email)) {
            throw new RuntimeException("Only the item creator or a platform admin can delete this item");
        }

        System.out.println("🗑️ Item " + itemId + " deleted by: " + email + (isAdmin ? " [ADMIN]" : ""));
        return s3StorageService.deleteItem(itemId);
    }

    public Item markAsSold(String itemId, String email) {
        System.out.println("🔍 markAsSold called with itemId: " + itemId + ", email: " + email);
        Item item = s3StorageService.getItem(itemId);
        if (item == null) {
            System.out.println("❌ Item not found for itemId: " + itemId);
            throw new RuntimeException("Item not found");
        }
        System.out.println("✅ Found item: " + item.getName() + " by " + item.getCreatorEmail());

        // Check if the user is the creator of the item
        if (!item.getCreatorEmail().equals(email)) {
            throw new RuntimeException("Only item creator can mark item as sold");
        }

        item.setStatus("SOLD");
        try {
            return s3StorageService.saveItem(item);
        } catch (Exception e) {
            throw new RuntimeException("Failed to mark item as sold: " + e.getMessage(), e);
        }
    }

    private void appendIfPresent(StringBuilder sb, String label, Object value) {
        if (value != null && !value.toString().isBlank()) {
            sb.append("\n").append(label).append(": ").append(value);
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
