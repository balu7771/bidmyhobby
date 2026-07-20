package com.bidmyhobby.hobbyaibackend.conversations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {
    Optional<Conversation> findFirstByProfileIdAndUserId(String profileId, String userId);
}
