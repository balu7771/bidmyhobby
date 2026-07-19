package com.bidmyhobby.catalog.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class OpenAiListingService {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final int timeoutSeconds;

    public OpenAiListingService(ObjectMapper mapper,
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.api.url:https://api.openai.com/v1/responses}") String apiUrl,
            @Value("${openai.model:gpt-5.4-mini}") String model,
            @Value("${openai.timeout-seconds:30}") int timeoutSeconds) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public ListingAnalysis analyze(String transcriptJson, List<ListingDraftImage> images) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }
        try {
            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of("type", "input_text", "text", prompt(transcriptJson)));
            for (ListingDraftImage image : images.stream().limit(4).toList()) {
                String mime = image.getContentType() == null ? "image/jpeg" : image.getContentType();
                if (!mime.startsWith("image/")) continue;
                String url = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image.getData());
                content.add(Map.of("type", "input_image", "image_url", url));
            }

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", model);
            request.put("input", List.of(Map.of("role", "user", "content", content)));
            request.put("text", Map.of("format", Map.of(
                    "type", "json_schema",
                    "name", "hobby_listing_analysis",
                    "strict", true,
                    "schema", schema())));

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(openAiError(response));
            }
            JsonNode root = mapper.readTree(response.body());
            String outputText = extractOutputText(root);
            return mapper.readValue(outputText, ListingAnalysis.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI request interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not analyze listing: " + e.getMessage(), e);
        }
    }

    private String openAiError(HttpResponse<String> response) {
        String detail = "";
        try {
            JsonNode error = mapper.readTree(response.body()).path("error");
            String message = error.path("message").asText("");
            String code = error.path("code").asText("");
            if (!message.isBlank()) detail = ": " + message;
            if (!code.isBlank() && !message.contains(code)) detail += " [" + code + "]";
        } catch (Exception ignored) {
            // Keep the status-only fallback for non-JSON upstream responses.
        }
        String requestId = response.headers().firstValue("x-request-id")
                .map(id -> " (request ID: " + id + ")")
                .orElse("");
        return "OpenAI request failed with status " + response.statusCode() + detail + requestId;
    }

    private String extractOutputText(JsonNode root) {
        for (JsonNode output : root.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText()) && content.hasNonNull("text")) {
                    return content.get("text").asText();
                }
            }
        }
        throw new IllegalStateException("OpenAI returned no structured output");
    }

    private String prompt(String transcriptJson) {
        return """
                You create draft listings for BidMyHobby, a hobby-item marketplace.
                Analyze the attached photos and conversation. Only hobby items are allowed.
                A hobby item includes collectibles, art/craft supplies and creations, musical gear,
                games, models, photography gear, sports hobby equipment, restoration projects,
                and specialist tools primarily used for a hobby.

                Separately assess safety and hobby relevance. Do not infer contact details or include
                phone numbers, email addresses, social handles, or addresses in title/description.
                Be conservative about identity, authenticity and condition.

                Pricing has two independent meanings:
                - suggestedPrice is your own INR market-value estimate based on the item. Never copy
                  a price stated by the seller into suggestedPrice and do not change your estimate
                  merely to agree with the seller.
                - sellerAskingPrice is the amount the seller explicitly says they want. Extract
                  natural statements such as "asking 1500", "I want ₹1,500", "price is 1500", or
                  "sell it for 1500". Return null when the seller has not stated an asking price.

                Ask exactly one short, friendly follow-up question for the most important missing
                detail. Do not ask the seller to confirm the AI estimate if they already stated
                their own asking price. Set readyForReview only when title, description, category,
                condition, city, and a seller asking price are present.

                Conversation JSON:
                """ + transcriptJson;
    }

    private Map<String, Object> schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("hobbyItem", Map.of("type", "boolean"));
        props.put("safe", Map.of("type", "boolean"));
        props.put("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1));
        props.put("moderationReason", Map.of("type", "string"));
        props.put("title", Map.of("type", "string"));
        props.put("description", Map.of("type", "string"));
        props.put("category", Map.of("type", "string"));
        props.put("condition", Map.of("type", "string"));
        props.put("suggestedPrice", Map.of("type", "number", "minimum", 0));
        props.put("sellerAskingPrice", Map.of(
                "anyOf", List.of(
                        Map.of("type", "number", "minimum", 0),
                        Map.of("type", "null"))));
        props.put("city", Map.of("type", "string"));
        props.put("size", Map.of("type", "string"));
        props.put("assistantMessage", Map.of("type", "string"));
        props.put("readyForReview", Map.of("type", "boolean"));
        props.put("missingFields", Map.of("type", "array", "items", Map.of("type", "string")));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", props,
                "required", new ArrayList<>(props.keySet()));
    }

    public record ListingAnalysis(boolean hobbyItem, boolean safe, double confidence,
            String moderationReason, String title, String description, String category,
            String condition, double suggestedPrice, Double sellerAskingPrice, String city, String size,
            String assistantMessage, boolean readyForReview, List<String> missingFields) {}
}
