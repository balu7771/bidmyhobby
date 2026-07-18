package com.bidmyhobby.hobbyaibackend.profiles;

import com.bidmyhobby.hobbyaibackend.config.BedrockKnowledgeBaseService;
import com.bidmyhobby.hobbyaibackend.config.ImageStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/profiles")
@CrossOrigin(origins = "*")
public class ProfileController {

    private final ProfileRepository profileRepository;
    private final ImageStorageService imageStorageService;
    private final BedrockKnowledgeBaseService knowledgeBaseService;
    private final SimpleProfileService simpleProfileService;

    public ProfileController(ProfileRepository profileRepository, 
                           ImageStorageService imageStorageService,
                           BedrockKnowledgeBaseService knowledgeBaseService,
                           SimpleProfileService simpleProfileService) {
        this.profileRepository = profileRepository;
        this.imageStorageService = imageStorageService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.simpleProfileService = simpleProfileService;
    }

    @GetMapping
    public List<Profile> getAllProfiles() {
        List<Profile> profiles = profileRepository.findAll();
        profiles.forEach(this::fixImageUrl);
        return profiles;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Profile> getProfileById(@PathVariable String id) {
        return profileRepository.findById(id)
                .map(profile -> {
                    fixImageUrl(profile);
                    return ResponseEntity.ok(profile);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/random")
    public ResponseEntity<Profile> getRandomProfile() {
        Profile profile = profileRepository.getRandomProfile();
        if (profile != null) {
            fixImageUrl(profile);
            return ResponseEntity.ok(profile);
        }
        return ResponseEntity.notFound().build();
    }
    
    private void fixImageUrl(Profile profile) {
        if (profile.getImageUrl() != null && profile.getImageUrl().startsWith("/api/images/")) {
            profile.setImageUrl(profile.getImageUrl().replace("/api/images/", ""));
        }
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<Profile>> searchProfiles(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int maxResults) {
        List<Profile> profiles = knowledgeBaseService.searchProfiles(query, maxResults);
        return ResponseEntity.ok(profiles);
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshProfiles() {
        profileRepository.deleteAll();
        simpleProfileService.saveProfilesToDB();
        return ResponseEntity.ok("Profiles refreshed from S3");
    }
    
    @PostMapping("/{id}/image")
    public ResponseEntity<?> uploadProfileImage(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        return profileRepository.findById(id)
                .map(profile -> {
                    try {
                        // Delete old image if exists
                        if (profile.getImageUrl() != null && !profile.getImageUrl().isEmpty()) {
                            imageStorageService.deleteImage(profile.getImageUrl());
                        }
                        
                        // Store new image
                        String filename = imageStorageService.storeImage(file);
                        profile.setImageUrl(filename);
                        
                        // Save updated profile
                        Profile savedProfile = profileRepository.save(profile);
                        return ResponseEntity.ok(savedProfile);
                    } catch (IOException e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/user")
    public ResponseEntity<Profile> createOrUpdateUserProfile(@RequestBody Profile userProfile) {
        // Set or update user profile with email as ID
        Profile savedProfile = profileRepository.save(userProfile);
        return ResponseEntity.ok(savedProfile);
    }
}