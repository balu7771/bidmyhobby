package com.bidmyhobby.hobbyaibackend.profiles;

import com.bidmyhobby.hobbyaibackend.config.S3Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SimpleProfileService {
    
    private final ProfileRepository profileRepository;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;
    
    public SimpleProfileService(ProfileRepository profileRepository, S3Service s3Service,
            ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.s3Service = s3Service;
        this.objectMapper = objectMapper;
    }
    
    public void saveProfilesToDB() {
        try {
            List<Profile> profiles;
            try (var input = s3Service.getProfilesJson()) {
                profiles = objectMapper.readValue(input, new TypeReference<List<Profile>>() {});
            }
            
            if (profiles != null && !profiles.isEmpty()) {
                // Update image URLs to S3 paths
                profiles.forEach(profile -> {
                    if (profile.getImageUrl() != null && !profile.getImageUrl().startsWith("http")) {
                        profile.setImageUrl(s3Service.getImageUrl(profile.getImageUrl()));
                    }
                });
                
                profileRepository.saveAll(profiles);
                System.out.println("Loaded " + profiles.size() + " profiles from S3");
            } else {
                System.out.println("No profiles found in the S3 profiles file; keeping existing profiles");
            }
        } catch (Exception e) {
            System.out.println("Error loading profiles from S3: " + e.getMessage());
        }
    }
}
