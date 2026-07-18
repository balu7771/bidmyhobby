package com.bidmyhobby.hobbyaibackend.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class OtpCacheService {

    private static final Logger logger = LoggerFactory.getLogger(OtpCacheService.class);

    // OTP expiry time: 10 minutes
    private static final long OTP_EXPIRY_MINUTES = 10;

    // Rate limiting: max 3 OTP requests per phone per hour
    private static final int MAX_OTP_REQUESTS_PER_HOUR = 3;

    private final OtpCacheRepository otpCacheRepository;

    @Autowired
    public OtpCacheService(OtpCacheRepository otpCacheRepository) {
        this.otpCacheRepository = otpCacheRepository;
    }

    /**
     * Generate and store OTP for a phone number with rate limiting
     * @return Map containing success status, OTP code, and rate limit info
     */
    public java.util.Map<String, Object> generateAndStoreOtp(String phoneNumber) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();

        // Check rate limiting
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<OtpCache> recentOtps = otpCacheRepository.findOtpsCreatedAfter(phoneNumber, oneHourAgo);

        if (recentOtps.size() >= MAX_OTP_REQUESTS_PER_HOUR) {
            logger.warn("❌ Rate limit exceeded for phone: {} - {} OTPs requested in past hour", phoneNumber, recentOtps.size());
            result.put("success", false);
            result.put("error", "RATE_LIMIT_EXCEEDED");
            result.put("message", "Too many OTP requests. Please try again in 1 hour.");
            result.put("remainingRequests", 0);
            return result;
        }

        // Generate OTP
        String otpCode = String.format("%06d", new Random().nextInt(999999));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        // Store OTP in database
        OtpCache otp = new OtpCache(phoneNumber, otpCode, expiresAt);
        otpCacheRepository.save(otp);

        logger.info("✅ OTP generated and stored - Phone: {}, OTP ID: {}, Expires at: {}", phoneNumber, otp.getId(), expiresAt);

        result.put("success", true);
        result.put("otp", otpCode);
        result.put("expiresAt", expiresAt);
        result.put("remainingRequests", MAX_OTP_REQUESTS_PER_HOUR - recentOtps.size() - 1);
        result.put("expiryMinutes", OTP_EXPIRY_MINUTES);

        return result;
    }

    /**
     * Verify OTP for a phone number
     * @return true if OTP is valid and not expired, false otherwise
     */
    public boolean verifyOtp(String phoneNumber, String code) {
        Optional<OtpCache> optOtp = otpCacheRepository.findLatestUnverifiedOtp(phoneNumber);

        if (optOtp.isEmpty()) {
            logger.warn("⚠️ No unverified OTP found for phone: {}", phoneNumber);
            return false;
        }

        OtpCache otp = optOtp.get();

        // Check if OTP is expired
        if (otp.isExpired()) {
            logger.warn("⚠️ OTP expired for phone: {} - Expired at: {}", phoneNumber, otp.getExpiresAt());
            return false;
        }

        // Check if OTP matches
        if (!otp.getOtpCode().equals(code)) {
            logger.warn("⚠️ Invalid OTP provided for phone: {}", phoneNumber);
            return false;
        }

        // Mark OTP as verified
        otp.setVerified(true);
        otpCacheRepository.save(otp);

        logger.info("✅ OTP verified successfully for phone: {}", phoneNumber);
        return true;
    }

    /**
     * Get status of OTP verification for a phone number
     * Returns whether a valid OTP exists and is verified
     */
    public java.util.Map<String, Object> getVerificationStatus(String phoneNumber) {
        java.util.Map<String, Object> status = new java.util.HashMap<>();

        Optional<OtpCache> optOtp = otpCacheRepository.findLatestUnverifiedOtp(phoneNumber);

        if (optOtp.isEmpty()) {
            status.put("verified", true); // No pending OTP means already verified or not in progress
            status.put("message", "No pending OTP verification");
            logger.debug("No unverified OTP found for phone: {}", phoneNumber);
            return status;
        }

        OtpCache otp = optOtp.get();

        if (otp.isExpired()) {
            status.put("verified", false);
            status.put("expired", true);
            status.put("message", "OTP has expired");
            status.put("expiresAt", otp.getExpiresAt());
            logger.debug("OTP expired for phone: {}", phoneNumber);
            return status;
        }

        // OTP is still valid and waiting for verification
        status.put("verified", false);
        status.put("expired", false);
        status.put("message", "OTP verification in progress");
        status.put("expiresAt", otp.getExpiresAt());
        status.put("remainingSeconds", calculateRemainingSeconds(otp.getExpiresAt()));
        logger.debug("OTP verification in progress for phone: {}", phoneNumber);

        return status;
    }

    /**
     * Calculate remaining seconds until OTP expires
     */
    private long calculateRemainingSeconds(LocalDateTime expiresAt) {
        return java.time.temporal.ChronoUnit.SECONDS.between(LocalDateTime.now(), expiresAt);
    }

    /**
     * Cleanup expired OTP records periodically (every 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupExpiredOtps() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<OtpCache> expiredOtps = otpCacheRepository.findExpiredOtps(now);

            if (!expiredOtps.isEmpty()) {
                otpCacheRepository.deleteAll(expiredOtps);
                logger.info("✅ Cleaned up {} expired OTP records", expiredOtps.size());
            }
        } catch (Exception e) {
            logger.error("❌ Error cleaning up expired OTPs", e);
        }
    }
}
