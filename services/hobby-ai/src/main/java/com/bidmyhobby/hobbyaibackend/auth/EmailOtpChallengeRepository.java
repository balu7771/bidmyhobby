package com.bidmyhobby.hobbyaibackend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailOtpChallengeRepository extends JpaRepository<EmailOtpChallenge, Long> {
    Optional<EmailOtpChallenge> findTopByEmailOrderByCreatedAtDesc(String email);
    List<EmailOtpChallenge> findByEmailAndCreatedAtAfter(String email, LocalDateTime after);
    List<EmailOtpChallenge> findBySourceIpHashAndCreatedAtAfter(String sourceIpHash, LocalDateTime after);
    List<EmailOtpChallenge> findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull(String email);
}
