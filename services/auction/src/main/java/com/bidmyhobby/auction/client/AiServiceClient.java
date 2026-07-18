package com.bidmyhobby.auction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "hobby-ai-service")
public interface AiServiceClient {

    @PostMapping("/api/ai/whatsapp/notify-approval")
    Map<String, String> notifyApproval(@RequestBody Map<String, String> request);
}
