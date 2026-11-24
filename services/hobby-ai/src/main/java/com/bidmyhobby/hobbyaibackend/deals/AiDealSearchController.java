package com.bidmyhobby.hobbyaibackend.deals;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/deals", "/api/ai/deals"})
@CrossOrigin(origins = "*")
public class AiDealSearchController {

    private final AiDealSearchService dealSearchService;

    public AiDealSearchController(AiDealSearchService dealSearchService) {
        this.dealSearchService = dealSearchService;
    }

    @PostMapping("/ai-search")
    public ResponseEntity<DealSearchResult> searchDealsPost(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        DealSearchResult result = dealSearchService.searchDeals(query);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ai-search")
    public ResponseEntity<DealSearchResult> searchDealsGet(@RequestParam(value = "q", defaultValue = "") String query) {
        DealSearchResult result = dealSearchService.searchDeals(query);
        return ResponseEntity.ok(result);
    }
}
