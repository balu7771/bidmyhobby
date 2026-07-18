package com.bidmyhobby.catalog.service;

import com.bidmyhobby.catalog.model.SmallBusiness;
import com.bidmyhobby.catalog.repository.SmallBusinessRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SmallBusinessService {

    @Autowired
    private SmallBusinessRepository smallBusinessRepository;

    @Autowired
    private S3StorageService s3StorageService;

    @Autowired
    private BedrockModerationService bedrockModerationService;

    public SmallBusiness createBusinessFromCard(MultipartFile businessCardImage,
            String uploaderEmail,
            String businessName,
            String category,
            String location,
            String description) throws Exception {

        // Use Bedrock to extract business card information with guardrails
        BedrockModerationService.BusinessCardResult result = bedrockModerationService.extractBusinessCardInfo(
                businessCardImage.getBytes(),
                businessCardImage.getContentType(),
                businessName,
                description);

        if (!result.isApproved()) {
            throw new RuntimeException("Business card content not approved: " + result.getReason());
        }

        Map<String, String> businessInfo = result.getBusinessInfo();

        // Upload image to S3
        String imageUrl = s3StorageService.uploadImage(businessCardImage.getBytes(), "business-cards");

        // Create SmallBusiness entity
        SmallBusiness business = new SmallBusiness();

        // Use extracted or user-provided values
        business.setBusinessName(businessInfo.getOrDefault("business_name", businessName != null ? businessName : ""));
        business.setDescription(businessInfo.getOrDefault("description", description != null ? description : ""));
        business.setCategory(category != null ? category : businessInfo.getOrDefault("category", ""));
        business.setLocation(location != null ? location : businessInfo.getOrDefault("location", ""));
        business.setUploaderEmail(uploaderEmail);
        business.setBusinessCardImageUrl(imageUrl);

        // Set extracted contact information
        String phone = businessInfo.getOrDefault("phone", "");
        if (phone != null && !phone.trim().isEmpty()) {
            business.setPhone(phone);
        }

        String email = businessInfo.getOrDefault("email", "");
        if (email != null && !email.trim().isEmpty()) {
            business.setEmail(email);
        }

        String website = businessInfo.getOrDefault("website", "");
        if (website != null && !website.trim().isEmpty()) {
            business.setWebsite(website);
        }

        String instagram = businessInfo.getOrDefault("instagram", "");
        if (instagram != null && !instagram.trim().isEmpty()) {
            business.setInstagramHandle(instagram);
        }

        String facebook = businessInfo.getOrDefault("facebook", "");
        if (facebook != null && !facebook.trim().isEmpty()) {
            business.setFacebookProfile(facebook);
        }

        // Auto-approve if content was approved by AI
        business.setIsApproved(true);

        return smallBusinessRepository.save(business);
    }

    public Page<SmallBusiness> getAllApprovedBusinesses(Pageable pageable) {
        return smallBusinessRepository.findByIsApprovedTrueAndIsActiveTrue(pageable);
    }

    public Page<SmallBusiness> getBusinessesByCategory(String category, Pageable pageable) {
        return smallBusinessRepository.findByIsApprovedTrueAndIsActiveTrueAndCategoryContainingIgnoreCase(
                category, pageable);
    }

    public Page<SmallBusiness> getBusinessesByLocation(String location, Pageable pageable) {
        return smallBusinessRepository.findByIsApprovedTrueAndIsActiveTrueAndLocationContainingIgnoreCase(
                location, pageable);
    }

    public Page<SmallBusiness> searchBusinesses(String keyword, Pageable pageable) {
        return smallBusinessRepository.searchByKeyword(keyword, pageable);
    }

    public Optional<SmallBusiness> getBusinessById(Long id) {
        return smallBusinessRepository.findById(id);
    }

    public List<SmallBusiness> getBusinessesByUploader(String uploaderEmail) {
        return smallBusinessRepository.findByUploaderEmailAndIsActiveTrue(uploaderEmail);
    }

    public SmallBusiness updateBusiness(Long id, SmallBusiness updatedBusiness) {
        Optional<SmallBusiness> existingBusiness = smallBusinessRepository.findById(id);
        if (existingBusiness.isPresent()) {
            SmallBusiness business = existingBusiness.get();
            business.setBusinessName(updatedBusiness.getBusinessName());
            business.setDescription(updatedBusiness.getDescription());
            business.setCategory(updatedBusiness.getCategory());
            business.setLocation(updatedBusiness.getLocation());
            business.setPhone(updatedBusiness.getPhone());
            business.setEmail(updatedBusiness.getEmail());
            business.setWebsite(updatedBusiness.getWebsite());
            business.setInstagramHandle(updatedBusiness.getInstagramHandle());
            return smallBusinessRepository.save(business);
        }
        throw new RuntimeException("Business not found");
    }

    public void deleteBusiness(Long id) {
        Optional<SmallBusiness> business = smallBusinessRepository.findById(id);
        if (business.isPresent()) {
            SmallBusiness b = business.get();
            b.setIsActive(false);
            smallBusinessRepository.save(b);
        }
    }

    public long getTotalBusinessCount() {
        return smallBusinessRepository.countByIsApprovedTrueAndIsActiveTrue();
    }
}