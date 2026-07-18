package com.bidmyhobby.hobbyaibackend.conversations;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(length = 2000)
    private String messageText;
    
    private String authorId;
    
    private LocalDateTime messageTime;
    
    public ChatMessage() {
        // Required by JPA
    }
    
    public ChatMessage(String messageText, String authorId, LocalDateTime messageTime) {
        this.messageText = messageText;
        this.authorId = authorId;
        this.messageTime = messageTime;
    }
    
    // Original record-style accessors
    public String messageText() { return messageText; }
    public String authorId() { return authorId; }
    public LocalDateTime messageTime() { return messageTime; }
    
    // Standard getters and setters for JPA
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }
    
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    
    public LocalDateTime getMessageTime() { return messageTime; }
    public void setMessageTime(LocalDateTime messageTime) { this.messageTime = messageTime; }
}