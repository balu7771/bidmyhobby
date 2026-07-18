package com.bidmyhobby.hobbyaibackend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class AiClientConfig {

    @Value("${aws.region:ap-south-1}")
    private String awsRegion;

    @Value("${aws.bedrock.region:ap-south-1}")
    private String bedrockRegion;

    @Value("${aws.bedrock.model-id}")
    private String modelId;

    @Value("${aws.bedrock.guardrail.id}")
    private String guardrailId;

    @Value("${aws.bedrock.guardrail.version}")
    private String guardrailVersion;

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.of(bedrockRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public BedrockAgentRuntimeClient bedrockAgentRuntimeClient() {
        return BedrockAgentRuntimeClient.builder()
                .region(Region.of(bedrockRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public BedrockChatService bedrockChatService(BedrockRuntimeClient bedrockRuntimeClient) {
        return new BedrockChatService(bedrockRuntimeClient, modelId, guardrailId, guardrailVersion);
    }

    public static class BedrockChatService {
        private static final Logger log = LoggerFactory.getLogger(BedrockChatService.class);
        private final BedrockRuntimeClient bedrockClient;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final String modelId;
        private final String guardrailId;
        private final String guardrailVersion;

        public BedrockChatService(BedrockRuntimeClient bedrockClient, String modelId, String guardrailId,
                String guardrailVersion) {
            this.bedrockClient = bedrockClient;
            this.modelId = modelId;
            this.guardrailId = guardrailId;
            this.guardrailVersion = guardrailVersion;
        }

        public String chat(String prompt) {
            log.info("BedrockChatService.chat() called with prompt: {}",
                    prompt.substring(0, Math.min(50, prompt.length())));
            try {
                Map<String, Object> requestBody = new HashMap<>();
                Map<String, Object> message = new HashMap<>();
                message.put("role", "user");

                Map<String, String> contentItem = new HashMap<>();
                contentItem.put("text", prompt);
                message.put("content", new Object[] { contentItem });

                requestBody.put("messages", new Object[] { message });

                Map<String, Object> inferenceConfig = new HashMap<>();
                inferenceConfig.put("max_new_tokens", 2000);
                inferenceConfig.put("temperature", 0.8);
                inferenceConfig.put("top_p", 0.9);
                requestBody.put("inferenceConfig", inferenceConfig);

                String jsonBody = objectMapper.writeValueAsString(requestBody);
                log.info("Bedrock request: {}", jsonBody);

                InvokeModelRequest request = InvokeModelRequest.builder()
                        .modelId(modelId)
                        .body(SdkBytes.fromUtf8String(jsonBody))
                        .guardrailIdentifier(guardrailId)
                        .guardrailVersion(guardrailVersion)
                        .build();

                InvokeModelResponse response = bedrockClient.invokeModel(request);
                String responseBody = response.body().asUtf8String();
                log.info("Bedrock response: {}", responseBody);

                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                return jsonResponse.get("output").get("message").get("content").get(0).get("text").asText();
            } catch (Exception e) {
                log.error("Bedrock error: ", e);
                // Return actual error message for debugging
                return "Error connecting to Bedrock: " + e.getMessage();
            }
        }
    }
}