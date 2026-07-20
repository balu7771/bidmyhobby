package com.bidmyhobby.hobbyaibackend.conversations;

import com.bidmyhobby.hobbyaibackend.ai.OpenAiChatService;
import com.bidmyhobby.hobbyaibackend.profiles.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConversationService {

    @Autowired
    private OpenAiChatService openAiChatService;

    public Conversation generateProfileResponse(Conversation conversation, Profile profile, Profile user) {
        StringBuilder prompt = new StringBuilder();
        
        if (profile != null) {
            String hobby = (profile.mainHobby() != null && !profile.mainHobby().isEmpty()) 
                ? profile.mainHobby() : "various hobbies";
            
            prompt.append("You are ").append(profile.firstName()).append(", a ").append(profile.age())
                  .append("-year-old ").append(profile.gender()).append(" from ").append(profile.ethnicity())
                  .append(" background who is passionate about ").append(hobby).append(".\n\n");
            
            prompt.append("Your personality: ").append(profile.bio()).append("\n\n");
            
            prompt.append("Context: You're an engaging member of BidMyHobby, a platform where people discover hobbies, ");
            prompt.append("auction hobby items, and connect with small businesses. Your role is to:\n");
            prompt.append("- Have natural, friendly conversations about hobbies and interests\n");
            prompt.append("- Share your genuine enthusiasm for ").append(hobby).append("\n");
            prompt.append("- Inspire others to pursue their passions in meaningful ways\n");
            prompt.append("- Occasionally mention BidMyHobby features (listing items, discovering businesses)\n\n");
            
            prompt.append("Style: Be conversational, warm, and authentic. Keep responses concise (2-3 sentences max). ");
            prompt.append("Use natural language, show personality, and ask engaging follow-up questions.\n\n");
            
            prompt.append("Conversation so far:\n");
        } else {
            prompt.append("You are a friendly AI assistant on BidMyHobby. Be helpful and concise.\n\n");
        }
        
        for (ChatMessage msg : conversation.messages()) {
            String author = msg.authorId().equals(profile != null ? profile.id() : "AI") ? "You" : "User";
            prompt.append(author).append(": ").append(msg.messageText()).append("\n");
        }
        
        prompt.append("\nRespond naturally as ").append(profile != null ? profile.firstName() : "AI").append(":");

        try {
            String response = openAiChatService.chat(prompt.toString());
            conversation.messages().add(new ChatMessage(
                    response,
                    profile != null ? profile.id() : "AI",
                    LocalDateTime.now()
            ));
        } catch (Exception e) {
            conversation.messages().add(new ChatMessage(
                    "Hi! How can I help you today?",
                    profile != null ? profile.id() : "AI",
                    LocalDateTime.now()
            ));
        }
        return conversation;
    }
}
