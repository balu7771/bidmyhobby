package com.bidmyhobby.hobbyaibackend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiChatService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiChatService.class);
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public OpenAiChatService(ObjectMapper mapper,
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.api.url:https://api.openai.com/v1/responses}") String apiUrl,
            @Value("${openai.model:gpt-4o-mini}") String model) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String chat(String prompt) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY is not configured");

        // Try primary configured apiUrl (e.g. /v1/responses or /v1/chat/completions)
        try {
            return sendRequest(apiUrl, prompt);
        } catch (Exception e) {
            log.warn("Primary OpenAI endpoint ({}) failed: {}. Trying fallback to /v1/chat/completions...", apiUrl, e.getMessage());
            if (!apiUrl.contains("/chat/completions")) {
                try {
                    return sendRequest("https://api.openai.com/v1/chat/completions", prompt);
                } catch (Exception fallbackEx) {
                    log.error("OpenAI fallback also failed: {}", fallbackEx.getMessage());
                    throw new IllegalStateException("OpenAI failed: " + fallbackEx.getMessage(), fallbackEx);
                }
            }
            throw new IllegalStateException("Could not generate response: " + e.getMessage(), e);
        }
    }

    private String sendRequest(String targetUrl, String prompt) throws Exception {
        boolean isChatCompletions = targetUrl.contains("/chat/completions");
        String body;
        if (isChatCompletions) {
            body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are an expert holiday deal finder for BidMyHobby."),
                            Map.of("role", "user", "content", prompt)
                    )
            ));
        } else {
            body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", List.of(Map.of("role", "user", "content", prompt))
            ));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI request failed with status " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());

        // Check for chat/completions choices format
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            if (message.has("content") && !message.path("content").asText().isBlank()) {
                return message.path("content").asText();
            }
        }

        // Check for responses output format
        for (JsonNode output : root.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    return content.path("text").asText();
                }
            }
        }

        throw new IllegalStateException("OpenAI returned no recognized text output");
    }
}
