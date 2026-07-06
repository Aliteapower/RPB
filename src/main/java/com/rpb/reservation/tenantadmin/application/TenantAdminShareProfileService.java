package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.service.PhoneMaskingPolicy;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateCatalog;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateRenderer;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateTextNormalizer;
import com.rpb.reservation.tenantadmin.persistence.TenantAdminShareProfileRepository;
import com.rpb.reservation.tenantadmin.application.TenantAdminShareProfileTextCatalog.ResolvedShareText;
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
    private final TenantAdminShareProfileTextCatalog textCatalog;
    private final PhoneMaskingPolicy phoneMaskingPolicy;

    public TenantAdminShareProfileService(
        TenantAdminShareProfileRepository repository,
        ReservationShareTemplateRenderer templateRenderer,
        TenantAdminShareProfileTextCatalog textCatalog,
        PhoneMaskingPolicy phoneMaskingPolicy
    ) {
        this.repository = repository;
        this.templateRenderer = templateRenderer;
        this.textCatalog = textCatalog;
        this.phoneMaskingPolicy = phoneMaskingPolicy;
    }

    @Transactional(readOnly = true)
    public TenantAdminShareProfile getProfile(StoreScope scope) {
        return getProfile(scope, null);
    }

    @Transactional(readOnly = true)
    public TenantAdminShareProfile getProfile(StoreScope scope, String locale) {
        TenantAdminShareProfileRepository.Row row = repository.find(scope)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID));
        ResolvedShareText resolvedText = textCatalog.resolve(
            scope,
            locale,
            row.reservationShareNote(),
            row.reservationShareTemplate()
        );
        return toProfile(row, resolvedText);
    }

    @Transactional
    public TenantAdminShareProfile updateProfile(StoreScope scope, TenantAdminShareProfileCommand command) {
        return updateProfile(scope, command, null);
    }

    @Transactional
    public TenantAdminShareProfile updateProfile(StoreScope scope, TenantAdminShareProfileCommand command, String locale) {
        TenantAdminShareProfileUpdate input = normalize(command);
        assertKnownTemplateVariables(input.reservationShareTemplate());
        boolean updated = textCatalog.isFallbackLocale(locale)
            ? repository.update(scope, input)
            : repository.updateContactSettings(scope, input);
        if (!updated) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        textCatalog.saveStoreOverride(scope, locale, TenantAdminShareProfileTextCatalog.ARRIVAL_NOTE_KEY, input.reservationShareNote());
        textCatalog.saveStoreOverride(scope, locale, TenantAdminShareProfileTextCatalog.TEMPLATE_KEY, input.reservationShareTemplate());
        return getProfile(scope, locale);
    }

    @Transactional
    public TenantAdminSharePreview preview(StoreScope scope, TenantAdminShareProfileCommand command) {
        return preview(scope, command, null);
    }

    @Transactional
    public TenantAdminSharePreview preview(StoreScope scope, TenantAdminShareProfileCommand command, String locale) {
        TenantAdminShareProfile current = getProfile(scope, locale);
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
        return restoreDefaultTemplate(scope, null);
    }

    @Transactional
    public TenantAdminShareProfile restoreDefaultTemplate(StoreScope scope, String locale) {
        textCatalog.clearStoreOverride(scope, locale, TenantAdminShareProfileTextCatalog.TEMPLATE_KEY);
        ResolvedShareText defaultText = textCatalog.resolve(scope, locale, null, null);
        if (textCatalog.isFallbackLocale(locale) && !repository.updateTemplate(scope, defaultText.defaultTemplate())) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return getProfile(scope, locale);
    }

    @Transactional
    public TenantAdminShareProfile updateTemplate(StoreScope scope, String reservationShareTemplate) {
        return updateTemplate(scope, reservationShareTemplate, null);
    }

    @Transactional
    public TenantAdminShareProfile updateTemplate(StoreScope scope, String reservationShareTemplate, String locale) {
        String normalizedTemplate = optionalTemplateText(reservationShareTemplate);
        assertKnownTemplateVariables(normalizedTemplate);
        if (textCatalog.isFallbackLocale(locale) && !repository.updateTemplate(scope, normalizedTemplate)) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        textCatalog.saveStoreOverride(scope, locale, TenantAdminShareProfileTextCatalog.TEMPLATE_KEY, normalizedTemplate);
        return getProfile(scope, locale);
    }

    private TenantAdminShareProfile toProfile(TenantAdminShareProfileRepository.Row row, ResolvedShareText resolvedText) {
        return new TenantAdminShareProfile(
            clean(row.storeDisplayName()),
            clean(row.shareDisplayName()),
            clean(row.shareAddress()),
            clean(row.googleMapUrl()),
            clean(row.shareContactPhone()),
            clean(row.shareEmail()),
            clean(row.whatsappBusinessPhoneE164()),
            clean(resolvedText.arrivalNote()),
            clean(resolvedText.template()),
            clean(resolvedText.defaultTemplate()),
            ReservationShareTemplateCatalog.supportedVariables(),
            resolvedText.usesDefaultTemplate()
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
            optionalTemplateText(command.reservationShareTemplate())
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

    private static String optionalTemplateText(String value) {
        return ReservationShareTemplateTextNormalizer.optional(value);
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
