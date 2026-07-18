package com.bidmyhobby.hobbyaibackend.config;

import com.bidmyhobby.hobbyaibackend.profiles.Gender;
import com.bidmyhobby.hobbyaibackend.profiles.Profile;
import com.bidmyhobby.hobbyaibackend.profiles.ProfileRepository;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BedrockKnowledgeBaseService {

    private final BedrockAgentRuntimeClient bedrockAgentClient;
    private final ProfileRepository profileRepository;
    private final Gson gson;

    @Value("${aws.bedrock.knowledge-base-id:}")
    private String knowledgeBaseId;

    public BedrockKnowledgeBaseService(BedrockAgentRuntimeClient bedrockAgentClient,
            ProfileRepository profileRepository) {
        this.bedrockAgentClient = bedrockAgentClient;
        this.profileRepository = profileRepository;
        this.gson = new Gson();
    }

    public List<Profile> searchProfiles(String query, int maxResults) {
        if (knowledgeBaseId == null || knowledgeBaseId.isEmpty()) {
            return List.of();
        }

        try {
            // Extract gender from query if present
            Gender requestedGender = extractGenderFromQuery(query);

            // Request more results from KB to account for gender filtering
            int kbMaxResults = requestedGender != null ? maxResults * 3 : maxResults;

            RetrieveRequest request = RetrieveRequest.builder()
                    .knowledgeBaseId(knowledgeBaseId)
                    .retrievalQuery(q -> q.text(query))
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(kbMaxResults)
                                    .build())
                            .build())
                    .build();

            RetrieveResponse response = bedrockAgentClient.retrieve(request);

            List<String> profileIds = response.retrievalResults().stream()
                    .map(result -> extractProfileId(result.content()))
                    .filter(id -> id != null && !id.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            List<Profile> profiles = profileIds.stream()
                    .map(profileRepository::findById)
                    .filter(opt -> opt.isPresent())
                    .map(opt -> opt.get())
                    .collect(Collectors.toList());

            // Filter by gender if specified in query
            if (requestedGender != null) {
                profiles = profiles.stream()
                        .filter(p -> p.getGender() == requestedGender)
                        .limit(maxResults)
                        .collect(Collectors.toList());
            }

            return profiles;

        } catch (Exception e) {
            System.err.println("Error searching knowledge base: " + e.getMessage());
            return List.of();
        }
    }

    private Gender extractGenderFromQuery(String query) {
        String lowerQuery = query.toLowerCase();

        // Check for female keywords
        if (lowerQuery.contains("girl") || lowerQuery.contains("woman") ||
                lowerQuery.contains("female") || lowerQuery.contains("she") ||
                lowerQuery.contains("lady")) {
            return Gender.FEMALE;
        }

        // Check for male keywords
        if (lowerQuery.contains("boy") || lowerQuery.contains("man") ||
                lowerQuery.contains("male") || lowerQuery.contains("he") ||
                lowerQuery.contains("guy")) {
            return Gender.MALE;
        }

        return null; // No gender specified
    }

    private String extractProfileId(RetrievalResultContent content) {
        try {
            String text = content.text();
            Map<String, Object> jsonMap = gson.fromJson(text, Map.class);
            return (String) jsonMap.get("id");
        } catch (Exception e) {
            return null;
        }
    }
}
