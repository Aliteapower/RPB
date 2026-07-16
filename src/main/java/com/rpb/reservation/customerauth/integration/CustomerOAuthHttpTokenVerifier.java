package com.rpb.reservation.customerauth.integration;

import com.rpb.reservation.customerauth.application.CustomerAuthError;
import com.rpb.reservation.customerauth.application.CustomerAuthServiceException;
import com.rpb.reservation.customerauth.application.CustomerOAuthProviderSettings;
import com.rpb.reservation.customerauth.application.CustomerOAuthVerificationRequest;
import com.rpb.reservation.customerauth.application.CustomerOAuthVerifiedIdentity;
import com.rpb.reservation.customerauth.application.port.out.CustomerOAuthTokenVerifierPort;
import java.net.URI;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CustomerOAuthHttpTokenVerifier implements CustomerOAuthTokenVerifierPort {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final RestClient restClient;

    public CustomerOAuthHttpTokenVerifier(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public CustomerOAuthVerifiedIdentity verify(
        CustomerOAuthVerificationRequest request,
        CustomerOAuthProviderSettings settings
    ) {
        return switch (request.provider()) {
            case "google" -> verifyGoogle(request, settings);
            case "facebook" -> verifyFacebook(request, settings);
            default -> throw new CustomerAuthServiceException(CustomerAuthError.PROVIDER_UNSUPPORTED);
        };
    }

    private CustomerOAuthVerifiedIdentity verifyGoogle(
        CustomerOAuthVerificationRequest request,
        CustomerOAuthProviderSettings settings
    ) {
        Map<String, Object> payload = getJson(UriComponentsBuilder
            .fromUriString("https://oauth2.googleapis.com/tokeninfo")
            .queryParam("id_token", request.token())
            .build()
            .toUri());
        String audience = string(payload.get("aud"));
        String issuer = string(payload.get("iss"));
        String subject = string(payload.get("sub"));
        String email = string(payload.get("email"));
        if (
            !settings.clientId().equals(audience)
                || !isGoogleIssuer(issuer)
                || !hasText(subject)
                || !hasText(email)
                || !truthy(payload.get("email_verified"))
        ) {
            throw new CustomerAuthServiceException(CustomerAuthError.PROVIDER_TOKEN_INVALID);
        }
        return new CustomerOAuthVerifiedIdentity(subject, email, string(payload.get("name")));
    }

    private CustomerOAuthVerifiedIdentity verifyFacebook(
        CustomerOAuthVerificationRequest request,
        CustomerOAuthProviderSettings settings
    ) {
        String appAccessToken = settings.clientId() + "|" + settings.clientSecret();
        Map<String, Object> debugPayload = getJson(UriComponentsBuilder
            .fromUriString("https://graph.facebook.com/debug_token")
            .queryParam("input_token", request.token())
            .queryParam("access_token", appAccessToken)
            .build()
            .toUri());
        Object rawData = debugPayload.get("data");
        if (!(rawData instanceof Map<?, ?> data)) {
            throw new CustomerAuthServiceException(CustomerAuthError.PROVIDER_TOKEN_INVALID);
        }
        String userId = string(data.get("user_id"));
        if (!truthy(data.get("is_valid")) || !settings.clientId().equals(string(data.get("app_id"))) || !hasText(userId)) {
            throw new CustomerAuthServiceException(CustomerAuthError.PROVIDER_TOKEN_INVALID);
        }

        Map<String, Object> profile = getJson(UriComponentsBuilder
            .fromUriString("https://graph.facebook.com/me")
            .queryParam("fields", "id,email,name")
            .queryParam("access_token", request.token())
            .build()
            .toUri());
        String email = string(profile.get("email"));
        if (!userId.equals(string(profile.get("id"))) || !hasText(email)) {
            throw new CustomerAuthServiceException(CustomerAuthError.PROVIDER_TOKEN_INVALID);
        }
        return new CustomerOAuthVerifiedIdentity(userId, email, string(profile.get("name")));
    }

    private Map<String, Object> getJson(URI uri) {
        try {
            Map<String, Object> body = restClient.get().uri(uri).retrieve().body(MAP_TYPE);
            if (body == null) {
                throw new CustomerAuthServiceException(CustomerAuthError.PROVIDER_TOKEN_INVALID);
            }
            return body;
        } catch (CustomerAuthServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CustomerAuthServiceException(CustomerAuthError.PROVIDER_TOKEN_INVALID);
        }
    }

    private static boolean isGoogleIssuer(String issuer) {
        return "https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer);
    }

    private static boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(string(value));
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
