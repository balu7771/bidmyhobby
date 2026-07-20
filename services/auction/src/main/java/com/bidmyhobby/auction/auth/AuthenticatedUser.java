package com.bidmyhobby.auction.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthenticatedUser {
    private final Algorithm algorithm;
    public AuthenticatedUser(@Value("${auth.token.secret:local-development-secret-change-me-123456789}") String secret) {
        if (secret == null || secret.length() < 32) throw new IllegalStateException("AUTH_TOKEN_SECRET must be at least 32 characters");
        algorithm = Algorithm.HMAC256(secret);
    }
    public Identity require(String header) {
        try {
            if (header == null || header.isBlank()) throw new IllegalArgumentException();
            DecodedJWT jwt = JWT.require(algorithm).withIssuer("bidmyhobby").build().verify(header);
            return new Identity(jwt.getSubject(), jwt.getClaim("role").asString());
        } catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"); }
    }
    public record Identity(String email, String role) { public boolean isAdmin() { return "ADMIN".equals(role); } }
}
