package com.bidmyhobby.hobbyaibackend.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_otp_challenges", indexes = @Index(name = "idx_email_otp_email_created", columnList = "email,created_at"))
public class EmailOtpChallenge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 320) private String email;
    @Column(name = "otp_hash", nullable = false, length = 64) private String otpHash;
    @Column(name = "source_ip_hash", nullable = false, length = 64) private String sourceIpHash;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "consumed_at") private LocalDateTime consumedAt;
    @Column(name = "invalidated_at") private LocalDateTime invalidatedAt;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;

    protected EmailOtpChallenge() {}
    public EmailOtpChallenge(String email, String otpHash, String sourceIpHash, LocalDateTime expiresAt) {
        this.email = email; this.otpHash = otpHash; this.sourceIpHash = sourceIpHash;
        this.createdAt = LocalDateTime.now(); this.expiresAt = expiresAt;
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getOtpHash() { return otpHash; }
    public String getSourceIpHash() { return sourceIpHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public LocalDateTime getInvalidatedAt() { return invalidatedAt; }
    public int getAttemptCount() { return attemptCount; }
    public void incrementAttemptCount() { attemptCount++; }
    public void consume() { consumedAt = LocalDateTime.now(); }
    public void invalidate() { invalidatedAt = LocalDateTime.now(); }
    public boolean isActive() { return consumedAt == null && invalidatedAt == null && LocalDateTime.now().isBefore(expiresAt); }
}
