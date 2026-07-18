package com.bidmyhobby.hobbyaibackend;

import com.bidmyhobby.hobbyaibackend.conversations.ConversationRepository;
import com.bidmyhobby.hobbyaibackend.matches.MatchRepository;
import com.bidmyhobby.hobbyaibackend.profiles.ProfileCreationService;
import com.bidmyhobby.hobbyaibackend.profiles.ProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import java.io.File;

@SpringBootApplication
@EnableDiscoveryClient
public class HobbyAiBackendApplication implements CommandLineRunner {

	@Autowired
	private ProfileRepository profileRepository;
	
	@Autowired
	private ConversationRepository conversationRepository;
	
	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private ProfileCreationService profileCreationService;
	
	@Value("${startup-actions.initializeProfiles:false}")
	private Boolean initializeProfiles;
	
	@Value("${app.storage.images-dir:images}")
	private String imagesDir;

	public static void main(String[] args) {
		SpringApplication.run(HobbyAiBackendApplication.class, args);
	}

	@Override
	public void run(String... args) {
		System.out.println("Application starting...");
		
		// Ensure directories exist
		ensureDirectoriesExist();
		
		long existingProfileCount = profileRepository.count();
		
		if (Boolean.TRUE.equals(initializeProfiles)) {
			System.out.println("Initializing profiles (startup-actions.initializeProfiles=true)");
			clearAllData();
			profileCreationService.createProfiles(0);
		} else if (existingProfileCount == 0) {
			System.out.println("No existing profiles found, loading from S3");
			profileCreationService.createProfiles(0);
		} else {
			System.out.println("Found " + existingProfileCount + " existing profiles in database");
		}
		
		System.out.println("Application startup completed");
	}
	
	private void ensureDirectoriesExist() {
		// Create images directory
		File imagesDirFile = new File(imagesDir);
		if (!imagesDirFile.exists()) {
			imagesDirFile.mkdirs();
			System.out.println("Created images directory: " + imagesDirFile.getAbsolutePath());
		}
		
		// Create data directory for H2
		File dataDirFile = new File("data");
		if (!dataDirFile.exists()) {
			dataDirFile.mkdirs();
			System.out.println("Created data directory: " + dataDirFile.getAbsolutePath());
		}
	}

	private void clearAllData() {
		System.out.println("Clearing existing data...");
		matchRepository.deleteAll();
		conversationRepository.deleteAll();
		profileRepository.deleteAll();
		System.out.println("Data cleared");
	}
}