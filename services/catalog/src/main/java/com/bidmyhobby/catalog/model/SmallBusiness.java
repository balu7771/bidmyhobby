package com.bidmyhobby.catalog.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "small_businesses")
public class SmallBusiness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String businessName;

    private String ownerName;
    private String phone;
    private String email;
    private String website;
    private String instagramHandle;
    private String facebookProfile;
    private String latestReelLink;

    @Column(length = 1000)
    private String description;

    private String category;
    private String location;
    private String businessCardImageUrl;

    @Column(nullable = false)
    private String uploaderEmail;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Boolean isApproved = false;

    @Column(nullable = false)
    private Boolean isActive = true;

    // Constructors
    public SmallBusiness() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getInstagramHandle() {
        return instagramHandle;
    }

    public void setInstagramHandle(String instagramHandle) {
        this.instagramHandle = instagramHandle;
    }

    public String getFacebookProfile() {
        return facebookProfile;
    }

    public void setFacebookProfile(String facebookProfile) {
        this.facebookProfile = facebookProfile;
    }

    public String getLatestReelLink() {
        return latestReelLink;
    }

    public void setLatestReelLink(String latestReelLink) {
        this.latestReelLink = latestReelLink;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getBusinessCardImageUrl() {
        return businessCardImageUrl;
    }

    public void setBusinessCardImageUrl(String businessCardImageUrl) {
        this.businessCardImageUrl = businessCardImageUrl;
    }

    public String getUploaderEmail() {
        return uploaderEmail;
    }

    public void setUploaderEmail(String uploaderEmail) {
        this.uploaderEmail = uploaderEmail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getIsApproved() {
        return isApproved;
    }

    public void setIsApproved(Boolean isApproved) {
        this.isApproved = isApproved;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}