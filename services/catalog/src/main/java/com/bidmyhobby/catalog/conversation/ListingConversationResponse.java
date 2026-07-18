package com.bidmyhobby.catalog.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record ListingConversationResponse(
        String conversationId,
        String state,
        long version,
        String assistantMessage,
        JsonNode messages,
        Map<String, Object> draft,
        List<String> missingFields,
        List<ImageSummary> images,
        List<String> allowedActions,
        String publishedItemId) {
    public record ImageSummary(String imageId, String filename, String contentType) {}
}
