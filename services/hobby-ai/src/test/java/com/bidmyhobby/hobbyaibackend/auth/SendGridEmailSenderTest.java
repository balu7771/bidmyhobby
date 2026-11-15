package com.bidmyhobby.hobbyaibackend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SendGridEmailSenderTest {

    private SendGridEmailSender sender;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        sender = new SendGridEmailSender(mapper);
    }

    @Test
    void unconfiguredReturnsLocalDevMockId() {
        String result = sender.sendLoginCode("test@example.com", "123456");
        assertEquals("local-dev-mock-id", result);

        String msgResult = sender.sendMessage("test@example.com", "Subject", "Body");
        assertEquals("local-dev-mock-id", msgResult);
    }

    @Test
    void placeholderSendGridKeyFallsBackToLocalDevMockId() {
        ReflectionTestUtils.setField(sender, "apiKey", "dummy-api-key");
        ReflectionTestUtils.setField(sender, "fromEmail", "from@example.com");

        String result = sender.sendLoginCode("test@example.com", "654321");
        assertEquals("local-dev-mock-id", result);
    }

    @Test
    void unreachableN8nWebhookFallsBackGracefully() {
        ReflectionTestUtils.setField(sender, "n8nWebhookUrl", "http://127.0.0.1:59999/webhook/send-email");

        // Should not throw exception, should fall back gracefully
        String result = sender.sendLoginCode("test@example.com", "112233");
        assertEquals("local-dev-mock-id", result);
    }

    @Test
    void smtpFailureFallsBackGracefully() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP connection refused"));

        ReflectionTestUtils.setField(sender, "mailSender", mailSender);
        ReflectionTestUtils.setField(sender, "smtpUsername", "user@gmail.com");
        ReflectionTestUtils.setField(sender, "smtpPassword", "secret-password");

        // Should catch the SMTP exception and fall back to local dev mock id
        String result = sender.sendLoginCode("test@example.com", "445566");
        assertEquals("local-dev-mock-id", result);
    }
}
