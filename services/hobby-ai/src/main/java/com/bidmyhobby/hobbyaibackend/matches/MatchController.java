package com.bidmyhobby.hobbyaibackend.matches;

import com.bidmyhobby.hobbyaibackend.conversations.Conversation;
import com.bidmyhobby.hobbyaibackend.conversations.ConversationRepository;
import com.bidmyhobby.hobbyaibackend.profiles.Profile;
import com.bidmyhobby.hobbyaibackend.profiles.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
@CrossOrigin(origins = "*")
public class MatchController {

    private final ConversationRepository conversationRepository;
    private final ProfileRepository profileRepository;
    private final MatchRepository matchRepository;

    public MatchController(ConversationRepository conversationRepository, ProfileRepository profileRepository, MatchRepository matchRepository) {
        this.conversationRepository = conversationRepository;
        this.profileRepository = profileRepository;
        this.matchRepository = matchRepository;
    }

    @GetMapping
    public List<Match> getAllMatches() {
        return matchRepository.findAll();
    }

    @GetMapping("/{id}")
    public Match getMatchById(@PathVariable String id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Unable to find match with ID " + id
                ));
    }

    @PostMapping
    public Match createMatch(@RequestBody CreateMatchRequest request) {
        Profile profile = profileRepository.findById(request.profileId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Unable to find a profile with ID " + request.profileId()
                ));
        
        // Create a new conversation for this match
        Conversation conversation = new Conversation(
                UUID.randomUUID().toString(),
                profile.id(),
                new ArrayList<>()
        );
        conversationRepository.save(conversation);
        
        // Create the match with the profile and conversation ID
        Match match = new Match(
                UUID.randomUUID().toString(),
                profile,
                conversation.id()
        );
        return matchRepository.save(match);
    }

    public record CreateMatchRequest(String profileId) {}
}