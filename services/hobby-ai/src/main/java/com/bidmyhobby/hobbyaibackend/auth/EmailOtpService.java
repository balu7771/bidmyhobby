package com.bidmyhobby.hobbyaibackend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class EmailOtpService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final EmailOtpChallengeRepository repository; private final EmailSender sender;
    private final AuthTokenService tokens; private final SecureRandom random = new SecureRandom(); private final byte[] hashSecret;
    public EmailOtpService(EmailOtpChallengeRepository repository, EmailSender sender, AuthTokenService tokens,
            @Value("${auth.otp.hash-secret:${auth.token.secret}}") String hashSecret) {
        this.repository = repository; this.sender = sender; this.tokens = tokens;
        this.hashSecret = hashSecret.getBytes(StandardCharsets.UTF_8);
    }
    public String normalize(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 320 || !EMAIL.matcher(value).matches()) throw new IllegalArgumentException("Enter a valid email address");
        return value;
    }
    @Transactional
    public void send(String rawEmail, String sourceIp) {
        String email = normalize(rawEmail), ipHash = hmac(sourceIp == null ? "unknown" : sourceIp);
        LocalDateTime hourAgo = LocalDateTime.now().minusHours(1);
        if (repository.findByEmailAndCreatedAtAfter(email, hourAgo).size() >= 5
                || repository.findBySourceIpHashAndCreatedAtAfter(ipHash, hourAgo).size() >= 20) throw new RateLimitException();
        repository.findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull(email).forEach(c -> { c.invalidate(); repository.save(c); });
        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailOtpChallenge challenge = repository.save(new EmailOtpChallenge(email, hmac(email + ":" + code), ipHash,
                LocalDateTime.now().plusMinutes(10)));
        try { sender.sendLoginCode(email, code); }
        catch (RuntimeException e) { challenge.invalidate(); repository.save(challenge); throw e; }
    }
    @Transactional
    public AuthTokenService.SessionToken verify(String rawEmail, String code) {
        String email = normalize(rawEmail);
        if (code == null || !code.matches("\\d{6}")) throw new InvalidCodeException();
        EmailOtpChallenge challenge = repository.findTopByEmailOrderByCreatedAtDesc(email).orElseThrow(InvalidCodeException::new);
        if (!challenge.isActive() || challenge.getAttemptCount() >= 5) throw new InvalidCodeException();
        challenge.incrementAttemptCount();
        if (!MessageDigest.isEqual(challenge.getOtpHash().getBytes(StandardCharsets.UTF_8),
                hmac(email + ":" + code).getBytes(StandardCharsets.UTF_8))) {
            repository.save(challenge); throw new InvalidCodeException();
        }
        challenge.consume(); repository.save(challenge);
        String token = tokens.issue(email); AuthTokenService.Session session = tokens.verify(token);
        return new AuthTokenService.SessionToken(token, session.email(), session.role());
    }
    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(hashSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Unable to hash authentication data", e); }
    }
    public static class RateLimitException extends RuntimeException {}
    public static class InvalidCodeException extends RuntimeException {}
}
