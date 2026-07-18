package com.bidmyhobby.catalog.conversation;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "listing_draft_images")
public class ListingDraftImage {
    @Id
    private String id;
    @Column(nullable = false)
    private String conversationId;
    private String filename;
    private String contentType;
    @Lob
    @Column(nullable = false)
    private byte[] data;
    private LocalDateTime createdAt;

    protected ListingDraftImage() {}
    public ListingDraftImage(String conversationId, String filename, String contentType, byte[] data) {
        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId;
        this.filename = filename;
        this.contentType = contentType;
        this.data = data;
        this.createdAt = LocalDateTime.now();
    }
    public String getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
}
