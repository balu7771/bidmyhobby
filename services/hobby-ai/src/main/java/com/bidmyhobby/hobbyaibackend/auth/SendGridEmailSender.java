package com.bidmyhobby.hobbyaibackend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class SendGridEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(SendGridEmailSender.class);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;
    @Value("${sendgrid.api.key:}") private String apiKey;
    @Value("${sendgrid.from.email:}") private String fromEmail;

    public SendGridEmailSender(ObjectMapper mapper) { this.mapper = mapper; }

    public String sendLoginCode(String email, String code) {
        return sendMessage(email, "Your BidMyHobby login code",
                "Your BidMyHobby login code is " + code
                        + ". It expires in 10 minutes. Do not share this code.");
    }

    public String sendMessage(String email, String subject, String content) {
        if (apiKey == null || apiKey.isBlank() || fromEmail == null || fromEmail.isBlank())
            throw new IllegalStateException("Email delivery is not configured");
        try {
            Map<String, Object> payload = Map.of(
                    "personalizations", List.of(Map.of("to", List.of(Map.of("email", email)))),
                    "from", Map.of("email", fromEmail, "name", "BidMyHobby"),
                    "subject", subject,
                    "content", List.of(Map.of("type", "text/plain", "value", content)));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.sendgrid.com/v3/mail/send"))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("SendGrid status " + response.statusCode());
            String requestId = response.headers().firstValue("X-Message-Id").orElse("unavailable");
            log.info("Email accepted by SendGrid; subject={}, requestId={}", subject, requestId);
            return requestId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Email delivery interrupted", e);
        } catch (Exception e) { throw new IllegalStateException("Email delivery failed", e); }
    }
}
