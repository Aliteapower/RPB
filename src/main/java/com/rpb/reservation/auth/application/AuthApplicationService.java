package com.rpb.reservation.auth.application;

import com.rpb.reservation.auth.api.AuthApiErrorCode;
import com.rpb.reservation.auth.api.AuthApiException;
import com.rpb.reservation.auth.api.LoginRequest;
import com.rpb.reservation.auth.api.SliderCaptchaResponse;
import com.rpb.reservation.auth.persistence.AuthRepository;
import com.rpb.reservation.auth.persistence.AuthRepository.AuthAccountRecord;
import com.rpb.reservation.auth.persistence.AuthRepository.AuthSessionRecord;
import com.rpb.reservation.auth.persistence.AuthRepository.SliderChallengeRecord;
import com.rpb.reservation.common.web.HostPrefixContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthApplicationService {
    private static final int IMAGE_WIDTH = 320;
    private static final int IMAGE_HEIGHT = 160;
    private static final int PIECE_SIZE = 44;
    private static final int TOLERANCE_PX = 7;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final String PASSWORD_PATTERN = "^[A-Za-z0-9]{6}$";
    private static final String ENTRY_PLATFORM_ADMIN = "platform_admin";
    private static final String ENTRY_TENANT_ADMIN = "tenant_admin";
    private static final String ENTRY_STAFF = "staff";

    private final AuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthApplicationService(AuthRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SliderCaptchaResponse createSliderCaptcha(String remoteAddr, String userAgent) {
        UUID challengeId = UUID.randomUUID();
        int targetX = 64 + secureRandom.nextInt(IMAGE_WIDTH - PIECE_SIZE - 64);
        int targetY = 34 + secureRandom.nextInt(IMAGE_HEIGHT - PIECE_SIZE - 48);
        Instant expiresAt = Instant.now().plus(CAPTCHA_TTL);
        SliderChallengeRecord challenge = new SliderChallengeRecord(
            challengeId,
            targetX,
            targetY,
            PIECE_SIZE,
            IMAGE_WIDTH,
            IMAGE_HEIGHT,
            TOLERANCE_PX,
            "created",
            0,
            MAX_ATTEMPTS,
            expiresAt,
            remoteAddr,
            userAgent
        );
        repository.insertChallenge(challenge);
        return new SliderCaptchaResponse(
            true,
            new SliderCaptchaResponse.ChallengeBody(
                challengeId.toString(),
                "image_slider",
                IMAGE_WIDTH,
                IMAGE_HEIGHT,
                PIECE_SIZE,
                targetY,
                expiresAt,
                backgroundImage(challenge),
                pieceImage(challenge),
                "拖动滑块完成校验"
            )
        );
    }

    @Transactional
    public AuthLoginResult login(
        LoginRequest request,
        HostPrefixContext hostPrefixContext,
        String remoteAddr,
        String userAgent
    ) {
        if (request == null) {
            throw new AuthApiException(AuthApiErrorCode.REQUEST_INVALID);
        }
        String username = requiredText(request.username(), AuthApiErrorCode.INVALID_CREDENTIALS);
        String password = requiredText(request.password(), AuthApiErrorCode.PASSWORD_POLICY_VIOLATION);
        verifyPasswordShape(password);
        verifyCaptcha(request.captchaId(), request.captchaX());

        AuthAccountRecord account = resolveLoginAccount(request, hostPrefixContext, username)
            .orElseThrow(() -> new AuthApiException(AuthApiErrorCode.INVALID_CREDENTIALS));
        if (!"active".equals(account.status())) {
            throw new AuthApiException(AuthApiErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(normalizePassword(password), account.passwordHash())) {
            repository.recordFailedLogin(account.id());
            throw new AuthApiException(AuthApiErrorCode.INVALID_CREDENTIALS);
        }

        repository.recordSuccessfulLogin(account.id());
        String sessionToken = newSessionToken();
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        repository.createSession(
            UUID.randomUUID(),
            account,
            hashSessionToken(sessionToken),
            expiresAt,
            remoteAddr,
            userAgent
        );
        return new AuthLoginResult(repository.principalFor(account), sessionToken, expiresAt);
    }

    private Optional<AuthAccountRecord> resolveLoginAccount(
        LoginRequest request,
        HostPrefixContext hostPrefixContext,
        String username
    ) {
        HostPrefixContext context = hostPrefixContext == null ? HostPrefixContext.none() : hostPrefixContext;
        String requestedEntry = normalizedEntry(request.loginEntry());
        String requestedTenantCode = trimToNull(request.tenantCode());

        if (context.isPlatform()) {
            if (requestedEntry != null && !ENTRY_PLATFORM_ADMIN.equals(requestedEntry)) {
                return Optional.empty();
            }
            return repository.findActivePlatformAccountByUsername(username);
        }

        if (context.isTenant()) {
            if (requestedTenantCode != null && !requestedTenantCode.equals(context.tenantCode())) {
                return Optional.empty();
            }
            String entry = requestedEntry == null ? ENTRY_TENANT_ADMIN : requestedEntry;
            if (!ENTRY_TENANT_ADMIN.equals(entry) && !ENTRY_STAFF.equals(entry)) {
                return Optional.empty();
            }
            return repository.findActiveTenantAccountByTenantCodeAndUsername(context.tenantCode(), entry, username);
        }

        if (requestedEntry == null) {
            return repository.findActiveAccountByUsername(username);
        }
        if (ENTRY_PLATFORM_ADMIN.equals(requestedEntry)) {
            return repository.findActivePlatformAccountByUsername(username);
        }
        if (ENTRY_TENANT_ADMIN.equals(requestedEntry) || ENTRY_STAFF.equals(requestedEntry)) {
            if (requestedTenantCode == null) {
                return Optional.empty();
            }
            return repository.findActiveTenantAccountByTenantCodeAndUsername(requestedTenantCode, requestedEntry, username);
        }
        return Optional.empty();
    }

    @Transactional
    public AuthPrincipal currentUser(String sessionToken) {
        return authenticateSession(sessionToken)
            .map(AuthSessionAuthentication::principal)
            .orElseThrow(() -> new AuthApiException(AuthApiErrorCode.UNAUTHENTICATED));
    }

    @Transactional
    public Optional<AuthSessionAuthentication> authenticateSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthSessionRecord> session = repository.findActiveSessionByHash(hashSessionToken(sessionToken));
        if (session.isEmpty()) {
            return Optional.empty();
        }
        repository.touchSession(session.get().sessionId());
        return Optional.of(new AuthSessionAuthentication(
            repository.principalFor(session.get().account()),
            session.get().expiresAt()
        ));
    }

    @Transactional
    public void logout(String sessionToken) {
        if (sessionToken != null && !sessionToken.isBlank()) {
            repository.revokeSession(hashSessionToken(sessionToken));
        }
    }

    public static String hashSessionToken(String sessionToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sessionToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("session_hash_failed", exception);
        }
    }

    private void verifyCaptcha(String captchaId, Integer captchaX) {
        if (captchaId == null || captchaId.isBlank() || captchaX == null) {
            throw new AuthApiException(AuthApiErrorCode.CAPTCHA_REQUIRED);
        }
        UUID challengeId = parseUuid(captchaId);
        SliderChallengeRecord challenge = repository.findChallenge(challengeId)
            .orElseThrow(() -> new AuthApiException(AuthApiErrorCode.CAPTCHA_EXPIRED));
        if (!"created".equals(challenge.status()) || challenge.attemptCount() >= challenge.maxAttempts()) {
            throw new AuthApiException(AuthApiErrorCode.CAPTCHA_EXPIRED);
        }
        if (challenge.expiresAt().isBefore(Instant.now())) {
            repository.markChallengeExpired(challenge.id());
            throw new AuthApiException(AuthApiErrorCode.CAPTCHA_EXPIRED);
        }
        if (Math.abs(challenge.targetX() - captchaX) > challenge.tolerancePx()) {
            repository.markChallengeFailed(challenge.id());
            throw new AuthApiException(AuthApiErrorCode.CAPTCHA_MISMATCH);
        }
        repository.markChallengeConsumed(challenge.id());
    }

    private static UUID parseUuid(String captchaId) {
        try {
            return UUID.fromString(captchaId);
        } catch (IllegalArgumentException exception) {
            throw new AuthApiException(AuthApiErrorCode.CAPTCHA_REQUIRED, Map.of("captchaId", "invalid"));
        }
    }

    private static String requiredText(String value, AuthApiErrorCode code) {
        if (value == null || value.isBlank()) {
            throw new AuthApiException(code);
        }
        return value.trim();
    }

    private static String normalizedEntry(String value) {
        String entry = trimToNull(value);
        if (entry == null) {
            return null;
        }
        entry = entry.toLowerCase(Locale.ROOT);
        return switch (entry) {
            case ENTRY_PLATFORM_ADMIN, ENTRY_TENANT_ADMIN, ENTRY_STAFF -> entry;
            default -> "invalid";
        };
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void verifyPasswordShape(String password) {
        if (!password.matches(PASSWORD_PATTERN)) {
            throw new AuthApiException(AuthApiErrorCode.PASSWORD_POLICY_VIOLATION);
        }
    }

    private static String normalizePassword(String password) {
        return password.toLowerCase(Locale.ROOT);
    }

    private String newSessionToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String backgroundImage(SliderChallengeRecord challenge) {
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
              <defs>
                <linearGradient id="bg" x1="0" x2="1" y1="0" y2="1">
                  <stop offset="0" stop-color="#d8f3f0"/>
                  <stop offset="0.52" stop-color="#edf3ff"/>
                  <stop offset="1" stop-color="#fff3d6"/>
                </linearGradient>
              </defs>
              <rect width="100%%" height="100%%" fill="url(#bg)"/>
              <circle cx="62" cy="48" r="24" fill="#2f6f73" opacity="0.18"/>
              <rect x="132" y="28" width="82" height="22" rx="6" fill="#2563eb" opacity="0.15"/>
              <rect x="36" y="100" width="230" height="18" rx="9" fill="#111827" opacity="0.09"/>
              <rect x="%d" y="%d" width="%d" height="%d" rx="9" fill="#0f172a" opacity="0.2" stroke="#ffffff" stroke-width="3"/>
            </svg>
            """.formatted(
            challenge.imageWidth(),
            challenge.imageHeight(),
            challenge.imageWidth(),
            challenge.imageHeight(),
            challenge.targetX(),
            challenge.targetY(),
            challenge.pieceSize(),
            challenge.pieceSize()
        );
        return dataSvg(svg);
    }

    private static String pieceImage(SliderChallengeRecord challenge) {
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
              <rect x="2" y="2" width="%d" height="%d" rx="9" fill="#ffffff" stroke="#2563eb" stroke-width="4"/>
              <path d="M14 25h16m-8-8 8 8-8 8" stroke="#2563eb" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            </svg>
            """.formatted(
            challenge.pieceSize(),
            challenge.pieceSize(),
            challenge.pieceSize(),
            challenge.pieceSize(),
            challenge.pieceSize() - 4,
            challenge.pieceSize() - 4
        );
        return dataSvg(svg);
    }

    private static String dataSvg(String svg) {
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }
}
