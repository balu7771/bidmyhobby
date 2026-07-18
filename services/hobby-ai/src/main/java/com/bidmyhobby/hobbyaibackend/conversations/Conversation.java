package com.bidmyhobby.hobbyaibackend.conversations;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conversations")
public class Conversation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String profileId;
    
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "conversation_id")
    private List<ChatMessage> messages = new ArrayList<>();
    
    public Conversation() {
        // Required by JPA
    }
    
    public Conversation(String id, String profileId, List<ChatMessage> messages) {
        this.id = id;
        this.profileId = profileId;
        this.messages = messages != null ? messages : new ArrayList<>();
    }
    
    // Original record-style accessors
    public String id() { return id; }
    public String profileId() { return profileId; }
    public List<ChatMessage> messages() { return messages; }
    
    // Standard getters and setters for JPA
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }
    
    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }
}