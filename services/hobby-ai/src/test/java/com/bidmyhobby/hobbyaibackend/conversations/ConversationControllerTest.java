package com.bidmyhobby.hobbyaibackend.conversations;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationControllerTest {

    @Test
    void limitsOnlyUserMessagesToTen() {
        List<ChatMessage> messages = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            messages.add(new ChatMessage("Question " + index, "guest@example.com", LocalDateTime.now()));
            messages.add(new ChatMessage("Answer " + index, "profile-1", LocalDateTime.now()));
        }
        Conversation conversation = new Conversation("conversation-1", "profile-1", messages);

        assertFalse(ConversationController.hasReachedMessageLimit(conversation));

        messages.add(new ChatMessage("Final question", "guest@example.com", LocalDateTime.now()));
        assertTrue(ConversationController.hasReachedMessageLimit(conversation));
    }
}
