package com.bidmyhobby.catalog.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ListingDraftImageRepository extends JpaRepository<ListingDraftImage, String> {
    List<ListingDraftImage> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    long countByConversationId(String conversationId);
}
