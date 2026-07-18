package com.bidmyhobby.hobbyaibackend.profiles;

import com.bidmyhobby.hobbyaibackend.config.S3Service;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.util.List;

@Service
public class SimpleProfileService {
    
    private final ProfileRepository profileRepository;
    private final S3Service s3Service;
    
    public SimpleProfileService(ProfileRepository profileRepository, S3Service s3Service) {
        this.profileRepository = profileRepository;
        this.s3Service = s3Service;
    }
    
    public void saveProfilesToDB() {
        try {
            Gson gson = new Gson();
            List<Profile> profiles = gson.fromJson(
                new InputStreamReader(s3Service.getProfilesJson()), 
                new TypeToken<List<Profile>>() {}.getType()
            );
            
            if (profiles != null && !profiles.isEmpty()) {
                // Update image URLs to S3 paths
                profiles.forEach(profile -> {
                    if (profile.getImageUrl() != null && !profile.getImageUrl().startsWith("http")) {
                        profile.setImageUrl(s3Service.getImageUrl(profile.getImageUrl()));
                    }
                });
                
                profileRepository.saveAll(profiles);
                System.out.println("Loaded " + profiles.size() + " profiles from S3");
            }
        } catch (Exception e) {
            System.out.println("Error loading profiles from S3: " + e.getMessage());
        }
    }
}