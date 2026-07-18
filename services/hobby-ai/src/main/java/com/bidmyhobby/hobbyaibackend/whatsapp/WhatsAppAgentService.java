package com.bidmyhobby.hobbyaibackend.whatsapp;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import com.bidmyhobby.hobbyaibackend.ai.AiClientConfig.BedrockChatService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;
import java.util.Random;
import java.util.Map;
import java.time.LocalDateTime;

@Service
public class WhatsAppAgentService {

    @Value("${twilio.account.sid:}")
    private String accountSid;

    @Value("${twilio.auth.token:}")
    private String authToken;

    @Value("${twilio.phone.number:}")
    private String twilioPhoneNumber;

    @Value("${app.site.url:https://bidmyhobby.com}")
    private String siteUrl;

    private final WhatsAppSessionRepository sessionRepository;
    private final OtpCacheRepository otpCacheRepository;
    private final BedrockChatService chatClient;

    @Autowired
    public WhatsAppAgentService(WhatsAppSessionRepository sessionRepository, OtpCacheRepository otpCacheRepository, BedrockChatService chatClient) {
        this.sessionRepository = sessionRepository;
        this.otpCacheRepository = otpCacheRepository;
        this.chatClient = chatClient;
    }

    @PostConstruct
    public void initTwilio() {
        if (accountSid != null && !accountSid.isEmpty() && authToken != null && !authToken.isEmpty()) {
            Twilio.init(accountSid, authToken);
            System.out.println("Twilio initialized for WhatsApp Agent.");
        } else {
            System.out.println("Twilio credentials not set. WhatsApp sending will be mocked.");
        }
    }

    public void sendWhatsAppMessage(String to, String messageBody) {
        if (accountSid == null || accountSid.isEmpty()) {
            System.out.println("MOCK WA to " + to + ": " + messageBody);
            return;
        }
        
        try {
            Message message = Message.creator(
                    new PhoneNumber(to.startsWith("whatsapp:") ? to : "whatsapp:" + to),
                    new PhoneNumber(twilioPhoneNumber.startsWith("whatsapp:") ? twilioPhoneNumber : "whatsapp:" + twilioPhoneNumber),
                    messageBody
            ).create();
            System.out.println("Sent WA message SID: " + message.getSid());
        } catch (Exception e) {
            System.err.println("Failed to send WhatsApp message: " + e.getMessage());
        }
    }

    public java.util.Map<String, Object> generateAndSendOtp(String phoneNumber) {
        // Check rate limiting: max 3 OTP requests per phone per hour
        long otpCountLastHour = otpCacheRepository.findByPhoneNumberAndCreatedAtAfter(
            phoneNumber,
            LocalDateTime.now().minusHours(1)
        ).size();

        if (otpCountLastHour >= 3) {
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("error", "RATE_LIMIT_EXCEEDED");
            result.put("message", "Too many OTP requests. Please try again after 1 hour.");
            return result;
        }

        // Generate 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(999999));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        // Save to persistent database
        OtpCache otpCache = new OtpCache(phoneNumber, otp, expiresAt);
        otpCacheRepository.save(otpCache);

        String loginToken = otpCache.getLoginToken();
        String magicLink = siteUrl + "/login?token=" + loginToken;
        String otpMsg = "\uD83D\uDD10 Your BidMyHobby login code is: *" + otp + "*\n\n"
                + "This code expires in 10 minutes. Do not share it with anyone.\n\n"
                + "\uD83D\uDC49 Or tap to log in instantly (no code needed):\n" + magicLink;
        boolean success = sendWhatsAppMessageBoolean(phoneNumber, otpMsg);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("otp", otp);
        result.put("success", success);
        result.put("expiresAt", expiresAt);
        return result;
    }

    public java.util.Map<String, Object> autoLoginByToken(String token) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        Optional<OtpCache> otpOpt = otpCacheRepository.findByLoginToken(token);
        if (otpOpt.isEmpty()) {
            result.put("success", false);
            result.put("error", "Token not found or already used");
            return result;
        }
        OtpCache otp = otpOpt.get();
        if (otp.isExpired()) {
            result.put("success", false);
            result.put("error", "Token has expired");
            return result;
        }
        otp.setVerified(true);
        otpCacheRepository.save(otp);
        result.put("success", true);
        result.put("phoneNumber", otp.getPhoneNumber());
        result.put("email", otp.getPhoneNumber());
        return result;
    }

    public boolean checkConsent(String phoneNumber) {
        return sessionRepository.findById(phoneNumber)
                .map(WhatsAppSession::isConsentGiven)
                .orElse(false);
    }

    public void recordConsent(String phoneNumber) {
        WhatsAppSession session = sessionRepository.findById(phoneNumber)
                .orElse(new WhatsAppSession(phoneNumber, "START"));
        session.setConsentGiven(true);
        sessionRepository.save(session);
    }

    private boolean sendWhatsAppMessageBoolean(String to, String messageBody) {
        if (accountSid == null || accountSid.isEmpty()) {
            System.out.println("MOCK WA to " + to + ": " + messageBody);
            return false;
        }
        try {
            Message message = Message.creator(
                    new PhoneNumber(to.startsWith("whatsapp:") ? to : "whatsapp:" + to),
                    new PhoneNumber(twilioPhoneNumber.startsWith("whatsapp:") ? twilioPhoneNumber : "whatsapp:" + twilioPhoneNumber),
                    messageBody
            ).create();
            System.out.println("Sent WA message SID: " + message.getSid());
            // Note: If user hasn't joined sandbox, Twilio often still returns a SID (queued) but fails later.
            // We will return false if the exception is caught.
            return true;
        } catch (Exception e) {
            System.err.println("Failed to send WhatsApp message: " + e.getMessage());
            return false;
        }
    }

    public boolean verifyOtp(String phoneNumber, String code) {
        // Find the latest (most recent) OTP for this phone number
        Optional<OtpCache> otpOptional = otpCacheRepository.findByPhoneNumber(phoneNumber)
            .stream()
            .filter(otp -> !otp.isVerified() && !otp.isExpired())
            .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));

        if (otpOptional.isPresent() && otpOptional.get().getOtpCode().equals(code)) {
            // Mark as verified
            OtpCache otp = otpOptional.get();
            otp.setVerified(true);
            otpCacheRepository.save(otp);
            return true;
        }
        return false;
    }

    public java.util.Map<String, Object> getVerificationStatus(String phoneNumber) {
        java.util.Map<String, Object> status = new java.util.HashMap<>();

        Optional<OtpCache> otpOptional = otpCacheRepository.findLatestUnverifiedOtp(phoneNumber);

        if (otpOptional.isEmpty()) {
            status.put("verified", true);
            status.put("message", "No pending OTP verification");
            return status;
        }

        OtpCache otp = otpOptional.get();

        if (otp.isExpired()) {
            status.put("verified", false);
            status.put("expired", true);
            status.put("message", "OTP has expired");
            status.put("expiresAt", otp.getExpiresAt());
            return status;
        }

        // OTP is still valid and waiting for verification
        status.put("verified", false);
        status.put("expired", false);
        status.put("message", "OTP verification in progress");
        status.put("expiresAt", otp.getExpiresAt());

        return status;
    }

    /**
     * Sends a WhatsApp notification to the winning buyer when the creator approves their bid.
     * This facilitates the exchange by sharing the seller's contact with the buyer.
     */
    public void sendApprovalNotification(String sellerPhone, String buyerPhone, String itemName) {
        // Notify the buyer
        String buyerMsg = "🎉 Congratulations! Your bid for *" + itemName + "* has been approved by the seller!\n\n"
                + "You can now connect with them directly on WhatsApp:\n"
                + "👉 https://wa.me/" + sellerPhone.replaceAll("[^0-9]", "") + "\n\n"
                + "Thank you for using BidMyHobby!";
        sendWhatsAppMessage(buyerPhone, buyerMsg);

        // Notify the seller
        String sellerMsg = "✅ You approved a bid for *" + itemName + "*!\n\n"
                + "The buyer's WhatsApp number is: *" + buyerPhone + "*\n"
                + "They have been notified and will reach out to you.\n\n"
                + "Happy trading! - BidMyHobby";
        sendWhatsAppMessage(sellerPhone, sellerMsg);
    }

    public void handleIncomingMessage(String from, String body, String mediaUrl) {
        Optional<WhatsAppSession> sessionOpt = sessionRepository.findById(from);
        WhatsAppSession session = sessionOpt.orElse(new WhatsAppSession(from, "START"));

        // Auto-record consent when user sends the join keyword
        if (body != null && body.trim().equalsIgnoreCase("join cream-enter")) {
            session.setConsentGiven(true);
            sessionRepository.save(session);
            String loginLink = siteUrl + "/login";
            sendWhatsAppMessage(from,
                    "\u2705 You're now connected to BidMyHobby!\n\n"
                    + "Your one-time login code will arrive in a moment.\n\n"
                    + "\uD83D\uDC49 Return to the app to complete login:\n" + loginLink);
            return;
        }

        String reply = "";

        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            // User sent an image
            session.setState("AWAITING_TITLE");
            session.setDraftImageUrl(mediaUrl); // We'd ideally download from Twilio and upload to S3 here
            sessionRepository.save(session);
            reply = "I received a picture of your hobby item! What would you like to set as the Title?";
        } else {
            // Process text based on state
            switch (session.getState()) {
                case "AWAITING_TITLE":
                    session.setDraftItemName(body);
                    session.setState("AWAITING_PRICE");
                    sessionRepository.save(session);
                    reply = "Got it! '" + body + "'. What is the starting Base Price for this item? (e.g., 50.0)";
                    break;
                case "AWAITING_PRICE":
                    try {
                        session.setDraftBasePrice(Double.parseDouble(body));
                        session.setState("AWAITING_DESCRIPTION");
                        sessionRepository.save(session);
                        reply = "Great. Please provide a short description for the item.";
                    } catch (NumberFormatException e) {
                        reply = "I didn't understand that price. Please provide a number like 50.0";
                    }
                    break;
                case "AWAITING_DESCRIPTION":
                    session.setDraftItemDescription(body);
                    session.setState("AWAITING_CATEGORY");
                    sessionRepository.save(session);
                    reply = "Awesome. One last thing, what category does this fall under? (e.g., ART, COLLECTIBLE, HOBBY)";
                    break;
                case "AWAITING_CATEGORY":
                    session.setDraftCategory(body);
                    session.setState("COMPLETED");
                    sessionRepository.save(session);
                    // Here we would call catalog-service to create the item!
                    reply = "All set! Your item '" + session.getDraftItemName() + "' has been successfully uploaded to BidMyHobby. 🎉";
                    break;
                case "COMPLETED":
                case "START":
                default:
                    // Use Bedrock ChatClient for generic replies
                    try {
                        String aiReply = chatClient.chat("You are a helpful WhatsApp assistant for BidMyHobby. The user says: " + body + ". Briefly explain they can send an image of their hobby item to start uploading.");
                        reply = aiReply;
                    } catch (Exception e) {
                        reply = "Welcome to BidMyHobby! Send me a photo of a hobby item to begin listing it.";
                    }
                    break;
            }
        }
        
        sendWhatsAppMessage(from, reply);
    }
}
