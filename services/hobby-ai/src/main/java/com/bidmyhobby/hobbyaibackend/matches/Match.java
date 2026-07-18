package com.bidmyhobby.hobbyaibackend.matches;

import com.bidmyhobby.hobbyaibackend.profiles.Profile;
import jakarta.persistence.*;

@Entity
@Table(name = "matches")
public class Match {
    
    @Id
    private String id;
    
    @ManyToOne
    @JoinColumn(name = "profile_id")
    private Profile profile;
    
    private String conversationId;
    
    public Match() {
        // Required by JPA
    }
    
    public Match(String id, Profile profile, String conversationId) {
        this.id = id;
        this.profile = profile;
        this.conversationId = conversationId;
    }
    
    // Original record-style accessors
    public String id() { return id; }
    public Profile profile() { return profile; }
    public String conversationId() { return conversationId; }
    
    // Standard getters and setters for JPA
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public Profile getProfile() { return profile; }
    public void setProfile(Profile profile) { this.profile = profile; }
    
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
}