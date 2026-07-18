package com.bidmyhobby.hobbyaibackend.whatsapp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_sessions")
public class WhatsAppSession {

    @Id
    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "state")
    private String state; // e.g., "AWAITING_IMAGE", "AWAITING_TITLE", "AWAITING_PRICE", "COMPLETED"

    @Column(name = "draft_item_name")
    private String draftItemName;

    @Column(name = "draft_item_description", length = 1000)
    private String draftItemDescription;

    @Column(name = "draft_base_price")
    private Double draftBasePrice;

    @Column(name = "draft_image_url", length = 1000)
    private String draftImageUrl;

    @Column(name = "draft_category")
    private String draftCategory;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven = false;

    public WhatsAppSession() {
        this.lastUpdated = LocalDateTime.now();
    }

    public WhatsAppSession(String phoneNumber, String state) {
        this.phoneNumber = phoneNumber;
        this.state = state;
        this.lastUpdated = LocalDateTime.now();
    }

    // Getters and setters
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getDraftItemName() { return draftItemName; }
    public void setDraftItemName(String draftItemName) { this.draftItemName = draftItemName; }

    public String getDraftItemDescription() { return draftItemDescription; }
    public void setDraftItemDescription(String draftItemDescription) { this.draftItemDescription = draftItemDescription; }

    public Double getDraftBasePrice() { return draftBasePrice; }
    public void setDraftBasePrice(Double draftBasePrice) { this.draftBasePrice = draftBasePrice; }

    public String getDraftImageUrl() { return draftImageUrl; }
    public void setDraftImageUrl(String draftImageUrl) { this.draftImageUrl = draftImageUrl; }

    public String getDraftCategory() { return draftCategory; }
    public void setDraftCategory(String draftCategory) { this.draftCategory = draftCategory; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public boolean isConsentGiven() { return consentGiven; }
    public void setConsentGiven(boolean consentGiven) { this.consentGiven = consentGiven; }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }
}
