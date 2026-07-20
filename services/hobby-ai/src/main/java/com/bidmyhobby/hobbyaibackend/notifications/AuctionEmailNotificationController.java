package com.bidmyhobby.hobbyaibackend.notifications;

import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/auction-emails")
public class AuctionEmailNotificationController {
    private final AuctionEmailNotificationService notifications;
    @Value("${auth.token.secret:local-development-secret-change-me-123456789}")
    private String internalServiceToken;

    public AuctionEmailNotificationController(AuctionEmailNotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping("/bid-placed")
    public ResponseEntity<Map<String, String>> bidPlaced(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody Map<String, String> request) {
        authorize(token);
        require(request, "creatorEmail", "bidderEmail", "itemName", "bidAmount");
        notifications.bidPlaced(request.get("creatorEmail"), request.get("bidderEmail"),
                request.get("itemName"), request.get("bidAmount"), request.get("currency"));
        return ResponseEntity.ok(Map.of("message", "Bid emails sent"));
    }

    @PostMapping("/bid-approved")
    public ResponseEntity<Map<String, String>> bidApproved(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody Map<String, String> request) {
        authorize(token);
        require(request, "creatorEmail", "bidderEmail", "itemName", "bidAmount");
        notifications.bidApproved(request.get("creatorEmail"), request.get("bidderEmail"),
                request.get("itemName"), request.get("bidAmount"), request.get("currency"));
        return ResponseEntity.ok(Map.of("message", "Approval emails sent"));
    }

    private void require(Map<String, String> request, String... fields) {
        for (String field : fields) {
            if (request.get(field) == null || request.get(field).isBlank()) {
                throw new IllegalArgumentException(field + " is required");
            }
        }
    }

    private void authorize(String token) {
        boolean matches = token != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                internalServiceToken.getBytes(StandardCharsets.UTF_8));
        if (!matches) throw new SecurityException("Invalid internal service token");
    }
}
