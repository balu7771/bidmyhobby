package com.bidmyhobby.catalog.service;

import com.bidmyhobby.catalog.model.Item;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;
import com.bidmyhobby.catalog.conversation.ListingConversation;
import com.bidmyhobby.catalog.conversation.ListingDraftImage;

@Service
public class CatalogService {

    // public static Logger log = LoggerFactory.getLogger(CatalogService.class);

    @Autowired
    ImageProcessingService imageProcessingService;

    @Autowired
    S3StorageService s3StorageService;

    @Autowired
    com.bidmyhobby.catalog.event.NotificationPublisher notificationPublisher;

    public List<Item> getAllItems() {
        return s3StorageService.getAllItems();
    }

    public Item getItem(String itemId) {
        return s3StorageService.getItem(itemId);
    }

    /** Publishes a draft already classified and moderated by OpenAI. */
    public Item publishConversationDraft(ListingConversation draft, List<ListingDraftImage> images,
            boolean manualReview) throws Exception {
        if (images == null || images.isEmpty()) throw new IllegalArgumentException("At least one image is required");
        List<String> imageUrls = new java.util.ArrayList<>();
        String itemId = UUID.randomUUID().toString();

        for (int i = 0; i < images.size(); i++) {
            ListingDraftImage image = images.get(i);
            byte[] processed = imageProcessingService.processImage(image.getData(), draft.getOwnerId(), false);
            String key = "images/" + itemId + "-" + (i + 1) + ".jpg";
            s3StorageService.uploadFile(key, processed, "image/jpeg");
            imageUrls.add(s3StorageService.generateImageUrl(key));
        }

        Item item = new Item();
        item.setItemId(itemId);
        item.setName(draft.getTitle());
        item.setDescription(maskContactDetails(draft.getDescription()));
        item.setCreatorEmail(draft.getOwnerId());
        if (draft.getBasePrice() != null) item.setBasePrice(BigDecimal.valueOf(draft.getBasePrice()));
        item.setSuggestedPrice(draft.getSuggestedPrice());
        item.setListingType(draft.getListingType());
        item.setCurrency(draft.getCurrency());
        item.setSize(draft.getSize() == null || draft.getSize().isBlank() ? "N/A" : draft.getSize());
        item.setCity(draft.getCity());
        item.setCategory(draft.getCategory() == null ? "HOBBY" : draft.getCategory().toUpperCase());
        item.setStatus(manualReview ? "REVIEW_REQUIRED" : "ACTIVE");
        item.setImageUrl(imageUrls.get(0));
        item.setImageUrls(imageUrls);
        return s3StorageService.saveItem(item);
    }

    private String maskContactDetails(String value) {
        if (value == null) return null;
        String masked = value.replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "[email hidden]");
        return masked.replaceAll("(?<!\\d)(?:\\+?\\d[\\d .()-]{6,}\\d)(?!\\d)", "[phone hidden]");
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

    public boolean deleteItem(String itemId, String email, boolean isAdmin) {
        Item item = s3StorageService.getItem(itemId);
        if (item == null) {
            throw new RuntimeException("Item not found");
        }

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

}
