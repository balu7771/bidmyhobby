package com.bidmyhobby.hobbyaibackend.profiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.*;

@Service
public class AiProfileSearchService {

    private static final Logger log = LoggerFactory.getLogger(AiProfileSearchService.class);
    private final BedrockAgentRuntimeClient bedrockAgentClient;
    private final ProfileRepository profileRepository;

    @Value("${aws.bedrock.knowledge-base-id}")
    private String knowledgeBaseId;

    public AiProfileSearchService(BedrockAgentRuntimeClient bedrockAgentClient, ProfileRepository profileRepository) {
        this.bedrockAgentClient = bedrockAgentClient;
        this.profileRepository = profileRepository;
    }

    public Profile searchProfile(String query) {
        try {
            log.info("Searching KB with query: {}", query);
            log.info("Using Knowledge Base ID: {}", knowledgeBaseId);

            RetrieveRequest request = RetrieveRequest.builder()
                    .knowledgeBaseId(knowledgeBaseId)
                    .retrievalQuery(q -> q.text(query))
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(1)
                                    .build())
                            .build())
                    .build();

            RetrieveResponse response = bedrockAgentClient.retrieve(request);

            log.info("KB returned {} results", response.retrievalResults().size());

            if (!response.retrievalResults().isEmpty()) {
                String resultText = response.retrievalResults().get(0).content().text();
                log.info("KB result text (first 200 chars): {}",
                        resultText.substring(0, Math.min(200, resultText.length())));

                String profileId = extractProfileId(resultText);
                if (profileId != null) {
                    log.info("Extracted profile ID from KB result: {}", profileId);
                    var profileOpt = profileRepository.findById(profileId);
                    if (profileOpt.isPresent()) {
                        Profile profile = profileOpt.get();
                        log.info("Found profile in DB: {} {} (Gender: {}, Age: {}, Ethnicity: {})",
                                profile.getFirstName(),
                                profile.getLastName(),
                                profile.getGender(),
                                profile.getAge(),
                                profile.getEthnicity());
                        return profile;
                    } else {
                        log.warn("Profile ID {} not found in database, falling back to random", profileId);
                    }
                } else {
                    log.warn("Could not extract profile ID from KB result, falling back to random");
                }
            } else {
                log.warn("KB returned no results for query: {}", query);
            }

            Profile randomProfile = getRandomProfile();
            log.info("Returning random profile: {} {} (Gender: {})",
                    randomProfile.getFirstName(),
                    randomProfile.getLastName(),
                    randomProfile.getGender());
            return randomProfile;
        } catch (Exception e) {
            log.error("Error searching KB: ", e);
            Profile randomProfile = getRandomProfile();
            log.info("Error fallback - returning random profile: {} {} (Gender: {})",
                    randomProfile.getFirstName(),
                    randomProfile.getLastName(),
                    randomProfile.getGender());
            return randomProfile;
        }
    }

    private String extractProfileId(String text) {
        try {
            if (text.contains("\"id\":")) {
                int start = text.indexOf("\"id\":") + 6;
                int end = text.indexOf("\"", start + 1);
                if (end > start) {
                    return text.substring(start + 1, end);
                }
            }
        } catch (Exception e) {
            log.error("Error extracting profile ID: ", e);
        }
        return null;
    }

    private Profile getRandomProfile() {
        return profileRepository.getRandomProfile();
    }
}
