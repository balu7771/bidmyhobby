package com.bidmyhobby.hobbyaibackend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailOtpServiceTest {
    private EmailOtpChallengeRepository repository;
    private EmailSender sender;
    private EmailOtpService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmailOtpChallengeRepository.class);
        sender = mock(EmailSender.class);
        AuthTokenService tokens = new AuthTokenService("test-secret-that-is-longer-than-thirty-two-characters", "admin@example.com");
        service = new EmailOtpService(repository, sender, tokens, "hash-secret-that-is-longer-than-thirty-two-characters");
        when(repository.findByEmailAndCreatedAtAfter(anyString(), any())).thenReturn(List.of());
        when(repository.findBySourceIpHashAndCreatedAtAfter(anyString(), any())).thenReturn(List.of());
        when(repository.findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull(anyString())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void normalizesEmailAndVerifiesSingleUseCode() {
        ArgumentCaptor<EmailOtpChallenge> challengeCaptor = ArgumentCaptor.forClass(EmailOtpChallenge.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        service.send("  Admin@Example.COM ", "127.0.0.1");
        verify(repository, atLeastOnce()).save(challengeCaptor.capture());
        verify(sender).sendLoginCode(eq("admin@example.com"), codeCaptor.capture());
        EmailOtpChallenge challenge = challengeCaptor.getAllValues().get(0);
        when(repository.findTopByEmailOrderByCreatedAtDesc("admin@example.com")).thenReturn(Optional.of(challenge));

        AuthTokenService.SessionToken result = service.verify("ADMIN@example.com", codeCaptor.getValue());

        assertEquals("admin@example.com", result.email());
        assertEquals("ADMIN", result.role());
        assertNotNull(challenge.getConsumedAt());
        assertThrows(EmailOtpService.InvalidCodeException.class,
                () -> service.verify("admin@example.com", codeCaptor.getValue()));
    }

    @Test
    void rejectsMalformedEmailAndWrongCode() {
        assertThrows(IllegalArgumentException.class, () -> service.send("not-an-email", "127.0.0.1"));
        service.send("user@example.com", "127.0.0.1");
        ArgumentCaptor<EmailOtpChallenge> captor = ArgumentCaptor.forClass(EmailOtpChallenge.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        EmailOtpChallenge challenge = captor.getAllValues().get(0);
        when(repository.findTopByEmailOrderByCreatedAtDesc("user@example.com")).thenReturn(Optional.of(challenge));
        assertThrows(EmailOtpService.InvalidCodeException.class,
                () -> service.verify("user@example.com", "111111"));
        assertEquals(1, challenge.getAttemptCount());
    }

    @Test
    void invalidatesPreviousChallengeOnResend() {
        EmailOtpChallenge previous = mock(EmailOtpChallenge.class);
        when(repository.findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull("user@example.com"))
                .thenReturn(List.of(previous));
        service.send("user@example.com", "127.0.0.1");
        verify(previous).invalidate();
        verify(repository).save(previous);
    }

    @Test
    void rateLimitsEmail() {
        when(repository.findByEmailAndCreatedAtAfter(eq("user@example.com"), any()))
                .thenReturn(List.of(mock(), mock(), mock(), mock(), mock()));
        assertThrows(EmailOtpService.RateLimitException.class,
                () -> service.send("user@example.com", "127.0.0.1"));
        verifyNoInteractions(sender);
    }
}
