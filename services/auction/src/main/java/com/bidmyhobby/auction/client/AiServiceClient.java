package com.bidmyhobby.auction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "hobby-ai-service", url = "${HOBBY_AI_SERVICE_URL:http://hobby-ai-service:8088}")
public interface AiServiceClient {

    @PostMapping("/api/ai/whatsapp/notify-approval")
    Map<String, String> notifyApproval(@RequestBody Map<String, String> request);

    @PostMapping("/api/internal/auction-emails/bid-placed")
    Map<String, String> sendBidPlacedEmails(@RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody Map<String, String> request);

    @PostMapping("/api/internal/auction-emails/bid-approved")
    Map<String, String> sendBidApprovedEmails(@RequestHeader("X-Internal-Token") String internalToken,
            @RequestBody Map<String, String> request);
}
