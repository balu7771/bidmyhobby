package com.bidmyhobby.hobbyaibackend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SendGridEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(SendGridEmailSender.class);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${smtp.username:${SMTP_USERNAME:}}") private String smtpUsername;
    @Value("${smtp.password:${SMTP_PASSWORD:}}") private String smtpPassword;
    @Value("${smtp.from:${SMTP_FROM:${SMTP_USERNAME:}}}") private String smtpFrom;

    @Value("${sendgrid.api.key:}") private String apiKey;
    @Value("${sendgrid.from.email:}") private String fromEmail;
    @Value("${n8n.webhook.url:${N8N_WEBHOOK_URL:}}") private String n8nWebhookUrl;
    @Value("${n8n.webhook.token:${N8N_WEBHOOK_TOKEN:}}") private String n8nWebhookToken;

    public SendGridEmailSender(ObjectMapper mapper) { this.mapper = mapper; }

    private boolean isSmtpConfigured() {
        return mailSender != null && smtpUsername != null && !smtpUsername.isBlank()
                && smtpPassword != null && !smtpPassword.isBlank()
                && !smtpUsername.startsWith("dummy") && !smtpPassword.startsWith("dummy")
                && !smtpPassword.contains("placeholder");
    }

    private boolean isN8nConfigured() {
        return n8nWebhookUrl != null && !n8nWebhookUrl.isBlank()
                && !n8nWebhookUrl.startsWith("dummy") && !n8nWebhookUrl.contains("placeholder");
    }

    private boolean isSendGridConfigured() {
        return apiKey != null && !apiKey.isBlank() && fromEmail != null && !fromEmail.isBlank()
                && !apiKey.startsWith("dummy") && !apiKey.contains("placeholder") && !apiKey.contains("replace");
    }

    @Override
    public String sendLoginCode(String email, String code) {
        String subject = "Your BidMyHobby login code";
        String content = "Your BidMyHobby login code is " + code
                + ". It expires in 10 minutes. Do not share this code.";

        if (isSmtpConfigured()) {
            try {
                return sendViaSmtp(email, subject, content, code);
            } catch (Exception e) {
                log.warn("⚠️ Direct SMTP delivery failed for OTP ({}). Trying next provider...", e.getMessage());
            }
        }

        if (isN8nConfigured()) {
            try {
                return sendViaN8n(email, subject, content, code, "LOGIN_OTP");
            } catch (Exception e) {
                log.warn("⚠️ n8n webhook delivery failed for OTP ({}). Trying next provider...", e.getMessage());
            }
        }

        if (isSendGridConfigured()) {
            return sendViaSendGrid(email, subject, content);
        }

        log.info("\n======================================================================\n"
                + "[LOCAL DEV OTP] Login code for {} is: {}\n"
                + "======================================================================",
                email, code);
        return "local-dev-mock-id";
    }

    @Override
    public String sendMessage(String email, String subject, String content) {
        if (isSmtpConfigured()) {
            try {
                return sendViaSmtp(email, subject, content, null);
            } catch (Exception e) {
                log.warn("⚠️ Direct SMTP delivery failed for notification ({}). Trying next provider...", e.getMessage());
            }
        }

        if (isN8nConfigured()) {
            try {
                return sendViaN8n(email, subject, content, null, "NOTIFICATION");
            } catch (Exception e) {
                log.warn("⚠️ n8n webhook delivery failed for notification ({}). Trying next provider...", e.getMessage());
            }
        }

        if (isSendGridConfigured()) {
            return sendViaSendGrid(email, subject, content);
        }

        log.warn("\n======================================================================\n"
                + "[LOCAL DEV EMAIL (No email provider configured)]\n"
                + "To: {}\n"
                + "Subject: {}\n"
                + "Content: {}\n"
                + "======================================================================",
                email, subject, content);
        return "local-dev-mock-id";
    }

    private String sendViaSmtp(String to, String subject, String content, String code) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        String from = (smtpFrom != null && !smtpFrom.isBlank()) ? smtpFrom.trim() : smtpUsername.trim();
        helper.setFrom(new InternetAddress(from, "BidMyHobby"));
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, buildHtmlBody(code, content));
        mailSender.send(message);
        log.info("Email dispatched successfully via direct SMTP to {}", to);
        return "smtp-sent-" + System.currentTimeMillis();
    }

    private String buildHtmlBody(String code, String content) {
        if (code != null && !code.isBlank()) {
            return """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 28px; border: 1px solid #e2e8f0; border-radius: 16px; background-color: #ffffff;">
                  <div style="margin-bottom: 24px;">
                    <h1 style="margin: 0; font-size: 22px; font-weight: 800; color: #0f172a; letter-spacing: -0.5px;">
                      BidMy<span style="color: #2563eb;">Hobby</span>
                    </h1>
                  </div>
                  <p style="font-size: 15px; color: #334155; line-height: 1.5; margin-bottom: 20px;">
                    Use the verification code below to sign in to your BidMyHobby account:
                  </p>
                  <div style="text-align: center; padding: 18px 24px; background-color: #f1f5f9; border-radius: 12px; margin: 24px 0;">
                    <span style="font-family: 'SF Mono', Monaco, Menlo, Consolas, monospace; font-size: 34px; font-weight: 700; letter-spacing: 8px; color: #1e293b;">%s</span>
                  </div>
                  <p style="font-size: 13px; color: #64748b; line-height: 1.5; margin-bottom: 0;">
                    This code is valid for <strong>10 minutes</strong> and can only be used once.<br/>
                    If you did not request this login code, you can safely ignore this email.
                  </p>
                  <hr style="border: none; border-top: 1px solid #f1f5f9; margin: 24px 0 16px;" />
                  <p style="font-size: 11px; color: #94a3b8; text-align: center; margin: 0;">
                    © BidMyHobby. All rights reserved.
                  </p>
                </div>
                """.formatted(code);
        }
        return """
            <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 28px; border: 1px solid #e2e8f0; border-radius: 16px; background-color: #ffffff;">
              <h1 style="margin: 0 0 20px; font-size: 22px; font-weight: 800; color: #0f172a;">
                BidMy<span style="color: #2563eb;">Hobby</span>
              </h1>
              <div style="font-size: 14px; color: #334155; line-height: 1.6; white-space: pre-line;">
                %s
              </div>
            </div>
            """.formatted(content);
    }

    private String sendViaN8n(String email, String subject, String content, String code, String type) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("to", email);
        payload.put("email", email);
        payload.put("subject", subject);
        payload.put("content", content);
        payload.put("type", type);
        if (code != null) {
            payload.put("code", code);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(n8nWebhookUrl.trim()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));

        if (n8nWebhookToken != null && !n8nWebhookToken.isBlank()) {
            builder.header("Authorization", "Bearer " + n8nWebhookToken.trim());
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("n8n responded with status " + response.statusCode() + ": " + response.body());
        }

        log.info("Email dispatched successfully via n8n webhook: to={}, type={}, status={}", email, type, response.statusCode());
        return "n8n-webhook-id-" + response.statusCode();
    }

    private String sendViaSendGrid(String email, String subject, String content) {
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
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("⚠️ SendGrid returned status {}. Logging OTP code locally as fallback.", response.statusCode());
                log.warn("\n======================================================================\n"
                        + "[LOGIN OTP CODE - SendGrid Fallback]\n"
                        + "To: {}\n"
                        + "Content: {}\n"
                        + "======================================================================",
                        email, content);
                return "sendgrid-fallback-id";
            }
            String requestId = response.headers().firstValue("X-Message-Id").orElse("unavailable");
            log.info("Email accepted by SendGrid; subject={}, requestId={}", subject, requestId);
            return requestId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[LOGIN OTP CODE - Interrupted Fallback]: To: {}, Content: {}", email, content);
            return "sendgrid-fallback-id";
        } catch (Exception e) {
            log.warn("⚠️ SendGrid delivery failed ({}), logging OTP code as fallback.", e.getMessage());
            log.warn("\n======================================================================\n"
                    + "[LOGIN OTP CODE - Fallback]\n"
                    + "To: {}\n"
                    + "Content: {}\n"
                    + "======================================================================",
                    email, content);
            return "sendgrid-fallback-id";
        }
    }
}
