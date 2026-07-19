package com.bidmyhobby.hobbyaibackend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", List.of(Map.of("role", "user", "content", prompt))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI request failed with status " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            for (JsonNode output : root.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText())) return content.path("text").asText();
                }
            }
            throw new IllegalStateException("OpenAI returned no text");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI request interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate response: " + e.getMessage(), e);
        }
    }
}
