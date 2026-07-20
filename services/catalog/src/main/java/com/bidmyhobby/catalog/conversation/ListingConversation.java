package com.bidmyhobby.catalog.conversation;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "listing_conversations")
public class ListingConversation {
    @Id
    private String id;
    @Column(nullable = false)
    private String ownerId;
    @Column(nullable = false)
    private String state;
    @Version
    private long version;
    @Lob
    @Column(columnDefinition = "CLOB")
    private String transcriptJson = "[]";
    private String title;
    @Lob
    @Column(columnDefinition = "CLOB")
    private String description;
    private String category;
    private String conditionLabel;
    private Double suggestedPrice;
    private Double basePrice;
    private String currency = "INR";
    private String listingType = "AUCTION";
    private String city;
    private String size;
    private boolean hobbyItem;
    private boolean safe;
    private boolean priceConfirmed;
    private double confidence;
    private String moderationReason;
    private String publishedItemId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected ListingConversation() {}

    public ListingConversation(String ownerId) {
        this.id = UUID.randomUUID().toString();
        this.ownerId = ownerId;
        this.state = "COLLECTING";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public String getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public long getVersion() { return version; }
    public String getTranscriptJson() { return transcriptJson; }
    public void setTranscriptJson(String transcriptJson) { this.transcriptJson = transcriptJson; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getConditionLabel() { return conditionLabel; }
    public void setConditionLabel(String conditionLabel) { this.conditionLabel = conditionLabel; }
    public Double getSuggestedPrice() { return suggestedPrice; }
    public void setSuggestedPrice(Double suggestedPrice) { this.suggestedPrice = suggestedPrice; }
    public Double getBasePrice() { return basePrice; }
    public void setBasePrice(Double basePrice) { this.basePrice = basePrice; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getListingType() { return listingType; }
    public void setListingType(String listingType) { this.listingType = listingType; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public boolean isHobbyItem() { return hobbyItem; }
    public void setHobbyItem(boolean hobbyItem) { this.hobbyItem = hobbyItem; }
    public boolean isSafe() { return safe; }
    public void setSafe(boolean safe) { this.safe = safe; }
    public boolean isPriceConfirmed() { return priceConfirmed; }
    public void setPriceConfirmed(boolean priceConfirmed) { this.priceConfirmed = priceConfirmed; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getModerationReason() { return moderationReason; }
    public void setModerationReason(String moderationReason) { this.moderationReason = moderationReason; }
    public String getPublishedItemId() { return publishedItemId; }
    public void setPublishedItemId(String publishedItemId) { this.publishedItemId = publishedItemId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
