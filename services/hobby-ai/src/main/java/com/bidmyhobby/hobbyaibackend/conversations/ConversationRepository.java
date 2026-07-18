package com.bidmyhobby.hobbyaibackend.conversations;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, String> {
}