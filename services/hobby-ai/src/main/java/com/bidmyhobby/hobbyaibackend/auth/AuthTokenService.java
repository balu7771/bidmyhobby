package com.bidmyhobby.hobbyaibackend.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuthTokenService {
    private final Algorithm algorithm;
    private final Set<String> adminEmails;
    public AuthTokenService(@Value("${auth.token.secret}") String secret, @Value("${auth.admin.emails:}") String admins) {
        if (secret == null || secret.length() < 32) throw new IllegalStateException("AUTH_TOKEN_SECRET must be at least 32 characters");
        algorithm = Algorithm.HMAC256(secret);
        adminEmails = Arrays.stream(admins.split(",")).map(String::trim).map(v -> v.toLowerCase(Locale.ROOT))
                .filter(v -> !v.isBlank()).collect(Collectors.toSet());
    }
    public String issue(String email) {
        Instant now = Instant.now();
        return JWT.create().withIssuer("bidmyhobby").withSubject(email)
                .withClaim("role", adminEmails.contains(email) ? "ADMIN" : "USER")
                .withIssuedAt(now).withExpiresAt(now.plus(30, ChronoUnit.MINUTES)).sign(algorithm);
    }
    public Session verify(String token) {
        DecodedJWT jwt = JWT.require(algorithm).withIssuer("bidmyhobby").build().verify(token);
        return new Session(jwt.getSubject(), jwt.getClaim("role").asString());
    }
    public record Session(String email, String role) {}
    public record SessionToken(String token, String email, String role) {}
}
