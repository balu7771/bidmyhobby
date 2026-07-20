package com.bidmyhobby.hobbyaibackend.conversations;

import com.bidmyhobby.hobbyaibackend.profiles.Profile;
import com.bidmyhobby.hobbyaibackend.profiles.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(origins = "*")
public class ConversationController {

    static final int MAX_USER_MESSAGES = 10;

    private final ConversationRepository conversationRepository;
    private final ProfileRepository profileRepository;
    private final ConversationService conversationService;

    public ConversationController(ConversationRepository conversationRepository, ProfileRepository profileRepository, ConversationService conversationService) {
        this.conversationRepository = conversationRepository;
        this.profileRepository = profileRepository;
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<Conversation> getAllConversations() {
        return conversationRepository.findAll();
    }

    @GetMapping("/{id}")
    public Conversation getConversationById(@PathVariable String id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Unable to find conversation with the ID " + id
                ));
    }

    @PostMapping
    public Conversation createConversation(@RequestBody CreateConversationRequest request) {
        Conversation existing = conversationRepository
                .findFirstByProfileIdAndUserId(request.profileId(), request.userId())
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        List<ChatMessage> messages = new ArrayList<>();
        if (request.initialMessage() != null && !request.initialMessage().isBlank()) {
            messages.add(new ChatMessage(request.initialMessage(), request.userId(), LocalDateTime.now()));
        }
        
        Conversation conversation = new Conversation(null, request.profileId(), request.userId(), messages);
        conversation = conversationRepository.save(conversation);
        
        if (!messages.isEmpty()) {
            Profile profile = profileRepository.findById(request.profileId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Unable to find a profile with ID " + request.profileId()
                    ));
            // For email-based or phone-based user IDs, we don't need to look up a profile
            Profile user = null;
            if (isExternalUser(request.userId())) {
                // Email, phone, or Guest user - no profile lookup needed
                user = null;
            } else {
                // Profile ID - look up the profile
                user = profileRepository.findById(request.userId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Unable to find a profile with ID " + request.userId()
                        ));
            }
            
            conversation = conversationService.generateProfileResponse(conversation, profile, user);
            conversation = conversationRepository.save(conversation);
        }
        
        return conversation;
    }

    @PostMapping("/{id}/messages")
    public Conversation addMessage(@PathVariable String id, @RequestBody ChatMessage chatMessage) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Unable to find conversation with the ID " + id
                ));

        if (conversation.userId() != null && !conversation.userId().equals(chatMessage.authorId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This conversation belongs to another user");
        }

        if (hasReachedMessageLimit(conversation)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "This conversation has reached its 10-message limit"
            );
        }
        
        String profileId = conversation.profileId();
        
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Unable to find a profile with ID " + profileId
                ));
        
        // For email-based or phone-based user IDs, we don't need to look up a profile
        Profile user = null;
        if (isExternalUser(chatMessage.authorId())) {
            // Email, phone, or Guest user - no profile lookup needed
            user = null;
        } else {
            // Profile ID - look up the profile
            user = profileRepository.findById(chatMessage.authorId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Unable to find a profile with ID " + chatMessage.authorId()
                    ));
        }

        ChatMessage messageWithTime = new ChatMessage(
                chatMessage.messageText(),
                chatMessage.authorId(),
                LocalDateTime.now()
        );
        
        conversation.messages().add(messageWithTime);
        conversation = conversationService.generateProfileResponse(conversation, profile, user);
        return conversationRepository.save(conversation);
    }

    static boolean hasReachedMessageLimit(Conversation conversation) {
        long userMessageCount = conversation.messages().stream()
                .filter(message -> !conversation.profileId().equals(message.authorId()))
                .filter(message -> !"AI".equals(message.authorId()))
                .count();
        return userMessageCount >= MAX_USER_MESSAGES;
    }

    private boolean isExternalUser(String userId) {
        if (userId == null) return true;
        return userId.contains("@") 
            || userId.startsWith("Guest")
            || userId.startsWith("+") 
            || userId.matches("\\d+");
    }

    public record CreateConversationRequest(String userId, String profileId, String initialMessage) {}
}
