package com.bidmyhobby.catalog.conversation;

import com.bidmyhobby.catalog.service.CatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ListingConversationServiceTest {

    @Test
    void reloadsConversationAfterExternalAnalysisBeforeSavingResults() {
        ListingConversationRepository conversations = mock(ListingConversationRepository.class);
        ListingDraftImageRepository images = mock(ListingDraftImageRepository.class);
        OpenAiListingService openAi = mock(OpenAiListingService.class);
        ListingConversation conversation = new ListingConversation("owner@example.com");

        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversations.save(any(ListingConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversations.saveAndFlush(any(ListingConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(images.countByConversationId(conversation.getId())).thenReturn(1L);
        when(images.findByConversationIdOrderByCreatedAtAsc(conversation.getId())).thenReturn(List.of());
        when(openAi.analyze(anyString(), anyList())).thenReturn(new OpenAiListingService.ListingAnalysis(
                true, true, 0.92, "", "Camera", "A hobby camera", "Photography",
                "Used", 5000, null, "Hyderabad", "", "Confirm the price.", false, List.of("price")));

        ListingConversationService service = new ListingConversationService(
                conversations, images, openAi, mock(CatalogService.class), new ObjectMapper());

        ListingConversationResponse response = service.message(
                conversation.getId(), "owner@example.com", "It is in good condition");

        assertEquals("Camera", response.draft().get("title"));
        verify(conversations, atLeast(2)).findById(conversation.getId());
        verify(conversations).saveAndFlush(conversation);
    }

    @Test
    void retainsAiEstimateAfterSellerConfirmsDifferentAskingPrice() {
        ListingConversationRepository conversations = mock(ListingConversationRepository.class);
        ListingDraftImageRepository images = mock(ListingDraftImageRepository.class);
        OpenAiListingService openAi = mock(OpenAiListingService.class);
        ListingConversation conversation = new ListingConversation("owner@example.com");
        conversation.setSuggestedPrice(2000.0);

        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(conversations.save(any(ListingConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversations.saveAndFlush(any(ListingConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(images.countByConversationId(conversation.getId())).thenReturn(1L);
        when(images.findByConversationIdOrderByCreatedAtAsc(conversation.getId())).thenReturn(List.of());
        when(openAi.analyze(anyString(), anyList())).thenReturn(new OpenAiListingService.ListingAnalysis(
                true, true, 0.95, "", "Camera", "A hobby camera", "Photography",
                "Used", 1500, 1500.0, "Hyderabad", "", "What city are you in?", false, List.of("city")));

        ListingConversationService service = new ListingConversationService(
                conversations, images, openAi, mock(CatalogService.class), new ObjectMapper());

        ListingConversationResponse response = service.message(
                conversation.getId(), "owner@example.com", "A4, and my asking price is 1500");

        assertEquals(2000.0, response.draft().get("suggestedPrice"));
        assertEquals(1500.0, response.draft().get("basePrice"));
        org.junit.jupiter.api.Assertions.assertFalse(response.missingFields().contains("askingPrice"));
    }
}
