package com.bidmyhobby.hobbyaibackend.whatsapp;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_otp_cache")
public class OtpCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "otp_code", nullable = false)
    private String otpCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    @Column(name = "login_token", unique = true, nullable = true)
    private String loginToken;

    public OtpCache() {
    }

    public OtpCache(String phoneNumber, String otpCode, LocalDateTime expiresAt) {
        this.phoneNumber = phoneNumber;
        this.otpCode = otpCode;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.isVerified = false;
        this.loginToken = UUID.randomUUID().toString();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public String getLoginToken() {
        return loginToken;
    }

    public void setLoginToken(String loginToken) {
        this.loginToken = loginToken;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
