package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.service.PhoneMaskingPolicy;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateCatalog;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateRenderer;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateSeedService;
import com.rpb.reservation.tenantadmin.persistence.TenantAdminShareProfileRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAdminShareProfileService {
    private static final Pattern E164_PATTERN = Pattern.compile("^[+][1-9][0-9]{1,14}$");
    private static final Pattern SINGAPORE_LOCAL_PHONE_PATTERN = Pattern.compile("^[0-9]{8}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String SINGAPORE_PHONE_PREFIX = "+65";

    private final TenantAdminShareProfileRepository repository;
    private final ReservationShareTemplateRenderer templateRenderer;
    private final ReservationShareTemplateSeedService templateSeedService;
    private final PhoneMaskingPolicy phoneMaskingPolicy;

    public TenantAdminShareProfileService(
        TenantAdminShareProfileRepository repository,
        ReservationShareTemplateRenderer templateRenderer,
        ReservationShareTemplateSeedService templateSeedService,
        PhoneMaskingPolicy phoneMaskingPolicy
    ) {
        this.repository = repository;
        this.templateRenderer = templateRenderer;
        this.templateSeedService = templateSeedService;
        this.phoneMaskingPolicy = phoneMaskingPolicy;
    }

    @Transactional
    public TenantAdminShareProfile getProfile(StoreScope scope) {
        TenantAdminShareProfileRepository.Row row = repository.find(scope)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID));
        String defaultTemplate = templateSeedService.defaultTemplate();
        if (!hasText(row.reservationShareTemplate())) {
            if (!repository.updateTemplate(scope, defaultTemplate)) {
                throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
            }
            row = repository.find(scope)
                .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID));
        }
        return toProfile(row, defaultTemplate);
    }

    @Transactional
    public TenantAdminShareProfile updateProfile(StoreScope scope, TenantAdminShareProfileCommand command) {
        TenantAdminShareProfileUpdate input = normalize(command);
        assertKnownTemplateVariables(input.reservationShareTemplate());
        if (!repository.update(scope, input)) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return getProfile(scope);
    }

    @Transactional
    public TenantAdminSharePreview preview(StoreScope scope, TenantAdminShareProfileCommand command) {
        TenantAdminShareProfile current = getProfile(scope);
        TenantAdminShareProfileUpdate input = normalize(command);
        String template = hasText(input.reservationShareTemplate())
            ? input.reservationShareTemplate()
            : current.reservationShareTemplate();
        assertKnownTemplateVariables(template);
        String shareText = templateRenderer.render(template, previewVariables(current, input));
        return new TenantAdminSharePreview(shareText);
    }

    @Transactional
    public TenantAdminShareProfile restoreDefaultTemplate(StoreScope scope) {
        if (!repository.updateTemplate(scope, templateSeedService.defaultTemplate())) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return getProfile(scope);
    }

    @Transactional
    public TenantAdminShareProfile updateTemplate(StoreScope scope, String reservationShareTemplate) {
        String normalizedTemplate = optionalText(reservationShareTemplate);
        assertKnownTemplateVariables(normalizedTemplate);
        if (!repository.updateTemplate(scope, normalizedTemplate)) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return getProfile(scope);
    }

    private TenantAdminShareProfile toProfile(TenantAdminShareProfileRepository.Row row, String defaultTemplate) {
        boolean usesDefaultTemplate = !hasText(row.reservationShareTemplate())
            || row.reservationShareTemplate().trim().equals(defaultTemplate);
        return new TenantAdminShareProfile(
            clean(row.storeDisplayName()),
            clean(row.shareDisplayName()),
            clean(row.shareAddress()),
            clean(row.googleMapUrl()),
            clean(row.shareContactPhone()),
            clean(row.shareEmail()),
            clean(row.whatsappBusinessPhoneE164()),
            clean(row.reservationShareNote()),
            usesDefaultTemplate ? defaultTemplate : row.reservationShareTemplate().trim(),
            defaultTemplate,
            ReservationShareTemplateCatalog.allowedVariables(),
            usesDefaultTemplate
        );
    }

    private static TenantAdminShareProfileUpdate normalize(TenantAdminShareProfileCommand command) {
        if (command == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return new TenantAdminShareProfileUpdate(
            optionalText(command.shareDisplayName()),
            optionalText(command.googleMapUrl()),
            optionalEmail(command.shareEmail()),
            optionalE164(command.whatsappBusinessPhoneE164()),
            optionalText(command.reservationShareNote()),
            optionalText(command.reservationShareTemplate())
        );
    }

    private void assertKnownTemplateVariables(String template) {
        if (!templateRenderer.unknownVariables(template).isEmpty()) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.TEMPLATE_UNKNOWN_VARIABLE);
        }
    }

    private Map<String, String> previewVariables(TenantAdminShareProfile current, TenantAdminShareProfileUpdate input) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (String variable : ReservationShareTemplateCatalog.supportedVariables()) {
            variables.put(variable, "");
        }
        variables.put("storeName", firstText(input.shareDisplayName(), current.shareDisplayName(), current.storeDisplayName()));
        variables.put("reservationNo", "R-PREVIEW-0001");
        variables.put("reservationDate", "20-06-2030");
        variables.put("reservationTime", "11:30");
        variables.put("partySize", "4");
        variables.put("tableCode", "A01");
        variables.put("holdMinutes", "15");
        variables.put("contactName", "Ada Guest");
        variables.put("maskedPhone", phoneMaskingPolicy.mask("+6591234567"));
        variables.put("storeAddress", clean(current.shareAddress()));
        variables.put("googleMapUrl", firstText(input.googleMapUrl(), current.googleMapUrl()));
        variables.put("storePhone", clean(current.shareContactPhone()));
        variables.put("arrivalNote", firstText(input.reservationShareNote(), current.reservationShareNote()));
        ReservationShareTemplateCatalog.applyLegacyAliases(variables);
        return variables;
    }

    private static String firstText(String first, String second) {
        if (hasText(first)) {
            return first.trim();
        }
        return clean(second);
    }

    private static String firstText(String first, String second, String third) {
        if (hasText(first)) {
            return first.trim();
        }
        if (hasText(second)) {
            return second.trim();
        }
        return clean(third);
    }

    private static String optionalText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static String optionalE164(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            return null;
        }
        if (SINGAPORE_LOCAL_PHONE_PATTERN.matcher(normalized).matches()) {
            return SINGAPORE_PHONE_PREFIX + normalized;
        }
        if (!E164_PATTERN.matcher(normalized).matches()) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String optionalEmail(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            return null;
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String clean(String value) {
        return hasText(value) ? value.trim() : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
