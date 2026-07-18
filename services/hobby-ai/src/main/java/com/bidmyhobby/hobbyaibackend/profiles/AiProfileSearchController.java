package com.bidmyhobby.hobbyaibackend.profiles;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai-search")
@CrossOrigin(origins = "*")
public class AiProfileSearchController {

    private final AiProfileSearchService aiProfileSearchService;

    public AiProfileSearchController(AiProfileSearchService aiProfileSearchService) {
        this.aiProfileSearchService = aiProfileSearchService;
    }

    @PostMapping
    public ResponseEntity<Profile> searchProfile(@RequestBody SearchRequest request) {
        Profile profile = aiProfileSearchService.searchProfile(request.query());
        if (profile != null) {
            return ResponseEntity.ok(profile);
        }
        return ResponseEntity.notFound().build();
    }

    public record SearchRequest(String query) {}
}
