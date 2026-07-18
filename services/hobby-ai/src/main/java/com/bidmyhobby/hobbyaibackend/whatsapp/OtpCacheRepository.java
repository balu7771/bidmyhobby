package com.bidmyhobby.hobbyaibackend.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtpCacheRepository extends JpaRepository<OtpCache, Long> {

    /**
     * Find the most recent unverified OTP for a phone number
     */
    @Query("SELECT o FROM OtpCache o WHERE o.phoneNumber = :phoneNumber AND o.isVerified = false ORDER BY o.createdAt DESC LIMIT 1")
    Optional<OtpCache> findLatestUnverifiedOtp(@Param("phoneNumber") String phoneNumber);

    /**
     * Find an OTP record by its magic login token (for one-tap auto-login from WhatsApp link)
     */
    @Query("SELECT o FROM OtpCache o WHERE o.loginToken = :token AND o.isVerified = false")
    Optional<OtpCache> findByLoginToken(@Param("token") String token);

    /**
     * Find all OTP requests for a phone number created after a specific time
     */
    @Query("SELECT o FROM OtpCache o WHERE o.phoneNumber = :phoneNumber AND o.createdAt > :afterTime")
    List<OtpCache> findOtpsCreatedAfter(@Param("phoneNumber") String phoneNumber, @Param("afterTime") LocalDateTime afterTime);

    /**
     * Find all OTP records for a phone number (for backward compatibility)
     */
    List<OtpCache> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /**
     * Find all OTP requests created after a specific time
     */
    @Query("SELECT o FROM OtpCache o WHERE o.phoneNumber = :phoneNumber AND o.createdAt > :afterTime")
    List<OtpCache> findByPhoneNumberAndCreatedAtAfter(@Param("phoneNumber") String phoneNumber, @Param("afterTime") LocalDateTime afterTime);

    /**
     * Delete expired OTP records
     */
    @Query("DELETE FROM OtpCache o WHERE o.expiresAt < :currentTime")
    void deleteExpiredOtps(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find all expired OTP records
     */
    @Query("SELECT o FROM OtpCache o WHERE o.expiresAt < :currentTime")
    List<OtpCache> findExpiredOtps(@Param("currentTime") LocalDateTime currentTime);
}
