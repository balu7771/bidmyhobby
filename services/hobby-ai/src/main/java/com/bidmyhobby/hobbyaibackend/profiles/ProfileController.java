package com.bidmyhobby.hobbyaibackend.profiles;

import com.bidmyhobby.hobbyaibackend.config.ImageStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.bidmyhobby.hobbyaibackend.auth.AuthTokenService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/profiles")
@CrossOrigin(origins = "*")
public class ProfileController {

    private final ProfileRepository profileRepository;
    private final ImageStorageService imageStorageService;
    private final ProfileSearchService profileSearchService;
    private final SimpleProfileService simpleProfileService;
    private final AuthTokenService authTokenService;

    public ProfileController(ProfileRepository profileRepository, 
                           ImageStorageService imageStorageService,
                           ProfileSearchService profileSearchService,
                           SimpleProfileService simpleProfileService,
                           AuthTokenService authTokenService) {
        this.profileRepository = profileRepository;
        this.imageStorageService = imageStorageService;
        this.profileSearchService = profileSearchService;
        this.simpleProfileService = simpleProfileService;
        this.authTokenService = authTokenService;
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
        List<Profile> profiles = profileSearchService.searchProfiles(query, maxResults);
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
    public ResponseEntity<Profile> createOrUpdateUserProfile(@RequestBody Profile userProfile,
            @CookieValue("bmh_session") String sessionToken) {
        AuthTokenService.Session session = authTokenService.verify(sessionToken);
        userProfile.setId(session.email());
        if (!userProfile.isWhatsappNotificationsEnabled()) {
            userProfile.setWhatsappConsentAt(null);
            userProfile.setWhatsappConsentVersion(null);
        } else if (userProfile.getWhatsappConsentAt() == null) {
            userProfile.setWhatsappConsentAt(java.time.LocalDateTime.now());
            userProfile.setWhatsappConsentVersion("2026-07-19");
        }
        Profile savedProfile = profileRepository.save(userProfile);
        return ResponseEntity.ok(savedProfile);
    }
}
