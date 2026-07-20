package com.bidmyhobby.hobbyaibackend.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final EmailOtpService otp; private final AuthTokenService tokens;
    @Value("${auth.cookie.secure:false}") private boolean secureCookie;
    public AuthController(EmailOtpService otp, AuthTokenService tokens) { this.otp = otp; this.tokens = tokens; }
    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, Object>> send(@RequestBody Map<String, String> request, HttpServletRequest servletRequest) {
        try {
            otp.send(request.get("email"), clientIp(servletRequest));
            return ResponseEntity.accepted().body(Map.of("message", "If the address can receive email, a login code has been sent.",
                    "expiresInSeconds", 600, "resendAfterSeconds", 60));
        } catch (EmailOtpService.RateLimitException e) {
            return ResponseEntity.status(429).header("Retry-After", "3600").body(Map.of("message", "Too many requests. Please try again later."));
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).body(Map.of("message", "We could not send a login code right now. Please try again."));
        }
    }
    @PostMapping("/otp/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, String> request) {
        try {
            AuthTokenService.SessionToken result = otp.verify(request.get("email"), request.get("otp"));
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, sessionCookie(result.token(), 1800).toString())
                    .body(Map.of("email", result.email(), "role", result.role()));
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (EmailOtpService.InvalidCodeException e) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid or expired login code."));
        }
    }
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(
            @CookieValue(value = "bmh_session", required = false) String cookieToken) {
        try {
            AuthTokenService.Session session = tokens.verify(cookieToken);
            return ResponseEntity.ok(Map.of("authenticated", true, "email", session.email(), "role", session.role()));
        } catch (RuntimeException e) { return ResponseEntity.status(401).body(Map.of("authenticated", false)); }
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessionCookie("", 0).toString()).build();
    }
    private ResponseCookie sessionCookie(String value, long maxAge) {
        return ResponseCookie.from("bmh_session", value).httpOnly(true).secure(secureCookie)
                .sameSite("Strict").path("/").maxAge(maxAge).build();
    }
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
