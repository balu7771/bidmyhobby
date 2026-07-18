package com.bidmyhobby.hobbyaibackend.profiles;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProfileCreationService {

    private final SimpleProfileService simpleProfileService;

    @Value("${startup-actions.initializeProfiles:false}")
    private Boolean initializeProfiles;

    public ProfileCreationService(SimpleProfileService simpleProfileService) {
        this.simpleProfileService = simpleProfileService;
    }

    public void createProfiles(int numberOfProfiles) {
        if (!Boolean.TRUE.equals(this.initializeProfiles)) {
            System.out.println("Profile initialization disabled. Profiles will load from S3 on demand.");
            return;
        }
        
        System.out.println("Loading profiles from S3...");
        simpleProfileService.saveProfilesToDB();
    }
}