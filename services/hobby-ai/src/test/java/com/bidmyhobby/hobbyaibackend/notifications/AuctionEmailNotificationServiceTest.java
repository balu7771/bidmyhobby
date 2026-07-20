package com.bidmyhobby.hobbyaibackend.notifications;

import com.bidmyhobby.hobbyaibackend.auth.EmailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AuctionEmailNotificationServiceTest {
    private final EmailSender sender = mock(EmailSender.class);
    private final AuctionEmailNotificationService service = new AuctionEmailNotificationService(sender);

    @Test
    void bidEmailsDoNotRevealCounterpartyAddresses() {
        service.bidPlaced("creator@example.com", "bidder@example.com", "Model Train", "2500", "INR");

        ArgumentCaptor<String> recipients = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).sendMessage(recipients.capture(), anyString(), content.capture());

        assertFalse(content.getAllValues().get(0).contains("bidder@example.com"));
        assertFalse(content.getAllValues().get(1).contains("creator@example.com"));
        assertTrue(content.getAllValues().stream().allMatch(message -> message.contains("₹2500")));
    }

    @Test
    void approvalIntroducesCreatorAndSelectedBidderWithNoObligationLanguage() {
        service.bidApproved("creator@example.com", "bidder@example.com", "Model Train", "2500", "INR");

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).sendMessage(anyString(), anyString(), content.capture());
        assertTrue(content.getAllValues().stream().allMatch(message ->
                message.contains("creator@example.com")
                        && message.contains("bidder@example.com")
                        && message.contains("no-obligation")
                        && message.contains("₹2500")));
    }
}
