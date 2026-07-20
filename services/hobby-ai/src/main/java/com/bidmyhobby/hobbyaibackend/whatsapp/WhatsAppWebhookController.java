package com.bidmyhobby.hobbyaibackend.whatsapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/ai/whatsapp")
public class WhatsAppWebhookController {

    private final WhatsAppAgentService whatsappAgentService;

    @Autowired
    public WhatsAppWebhookController(WhatsAppAgentService whatsappAgentService) {
        this.whatsappAgentService = whatsappAgentService;
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> receiveWebhook(
            @RequestParam("From") String from,
            @RequestParam("Body") String body,
            @RequestParam(value = "MediaUrl0", required = false) String mediaUrl) {
        
        // Run asynchronously to avoid blocking Twilio
        new Thread(() -> {
            whatsappAgentService.handleIncomingMessage(from, body, mediaUrl);
        }).start();

        // Twilio expects a valid TwiML response. We return an empty TwiML since we reply async via API
        return ResponseEntity.ok("<Response></Response>");
    }

    @PostMapping("/login/check-consent")
    public ResponseEntity<Map<String, Object>> checkConsent(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        boolean hasConsent = whatsappAgentService.checkConsent(phoneNumber);
        Map<String, Object> response = new HashMap<>();
        response.put("hasConsent", hasConsent);
        response.put("sandboxNumber", "+14155238886");
        response.put("joinKeyword", "join cream-enter");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/confirm-consent")
    public ResponseEntity<Map<String, Object>> confirmConsent(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        // Record consent and immediately dispatch OTP
        whatsappAgentService.recordConsent(phoneNumber);
        Map<String, Object> result = whatsappAgentService.generateAndSendOtp(phoneNumber);

        // Handle rate-limit case
        if (result.containsKey("error")) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", result.get("message"));
            errorResp.put("error", result.get("error"));
            return ResponseEntity.status(429).body(errorResp);
        }

        boolean success = Boolean.TRUE.equals(result.get("success"));
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Consent recorded and OTP sent");
        response.put("success", success);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/send-otp")
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> result = whatsappAgentService.generateAndSendOtp(phoneNumber);

        // Handle rate-limit case — "success" key is absent, "error" key is present
        if (result.containsKey("error")) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", result.get("message"));
            errorResp.put("error", result.get("error"));
            return ResponseEntity.status(429).body(errorResp);
        }

        boolean success = Boolean.TRUE.equals(result.get("success"));
        Map<String, Object> response = new HashMap<>();
        response.put("message", "OTP processed");
        response.put("success", success);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        String otp = request.get("otp");

        if (phoneNumber == null || otp == null) {
            return ResponseEntity.badRequest().build();
        }

        boolean isValid = whatsappAgentService.verifyOtp(phoneNumber, otp);
        if (isValid) {
            Map<String, String> response = new HashMap<>();
            response.put("token", "mock-jwt-token-for-" + phoneNumber);
            response.put("email", phoneNumber); // using phone as identifier in UI
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid OTP"));
        }
    }

    @PostMapping("/login/verify-status")
    public ResponseEntity<Map<String, Object>> getVerifyStatus(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, Object> status = whatsappAgentService.getVerificationStatus(phoneNumber);
        return ResponseEntity.ok(status);
    }

    /**
     * Magic link auto-login: called when user taps the link in their WhatsApp message.
     * The token is a one-time UUID embedded in the URL sent to WhatsApp.
     */
    @PostMapping("/login/auto-login")
    public ResponseEntity<Map<String, Object>> autoLogin(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Token is required"));
        }
        Map<String, Object> result = whatsappAgentService.autoLoginByToken(token);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success ? ResponseEntity.ok(result) : ResponseEntity.status(401).body(result);
    }

    @PostMapping("/notify-approval")
    public ResponseEntity<Map<String, String>> notifyApproval(@RequestBody Map<String, String> request) {
        String sellerPhone = request.get("sellerPhone");
        String buyerPhone  = request.get("buyerPhone");
        String itemName    = request.get("itemName");
        if (sellerPhone == null || buyerPhone == null || itemName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "sellerPhone, buyerPhone and itemName are required"));
        }
        whatsappAgentService.sendApprovalNotification(sellerPhone, buyerPhone, itemName);
        return ResponseEntity.ok(Map.of("message", "Approval notifications dispatched"));
    }

    @PostMapping("/notify-bid")
    public ResponseEntity<Map<String, String>> notifyBid(@RequestBody Map<String, String> request) {
        String sellerPhone = request.get("sellerPhone");
        String itemName    = request.get("itemName");
        String bidAmount   = request.get("bidAmount");
        String currency    = request.get("currency");
        // Only notify if seller is a phone-based identity
        if (sellerPhone != null && (sellerPhone.startsWith("+") || sellerPhone.matches("\\d+")) && itemName != null) {
            String symbol = "GBP".equals(currency) ? "£" : "INR".equals(currency) ? "₹" : "$";
            String msg = "\uD83D\uDD14 New bid on your item *" + itemName + "*!\n\n"
                    + "Bid amount: *" + symbol + bidAmount + "*\n\n"
                    + "Log in to BidMyHobby to review all bids and approve a winner.";
            whatsappAgentService.sendWhatsAppMessage(sellerPhone, msg);
        }
        return ResponseEntity.ok(Map.of("message", "Notification dispatched"));
    }
}
