package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.service.PhoneMaskingPolicy;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateCatalog;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateRenderer;
import com.rpb.reservation.tenantadmin.persistence.TenantAdminShareProfileRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAdminShareProfileService {
    private final TenantAdminShareProfileRepository repository;
    private final ReservationShareTemplateRenderer templateRenderer;
    private final PhoneMaskingPolicy phoneMaskingPolicy;

    public TenantAdminShareProfileService(
        TenantAdminShareProfileRepository repository,
        ReservationShareTemplateRenderer templateRenderer,
        PhoneMaskingPolicy phoneMaskingPolicy
    ) {
        this.repository = repository;
        this.templateRenderer = templateRenderer;
        this.phoneMaskingPolicy = phoneMaskingPolicy;
    }

    @Transactional(readOnly = true)
    public TenantAdminShareProfile getProfile(StoreScope scope) {
        TenantAdminShareProfileRepository.Row row = repository.find(scope)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID));
        return toProfile(row);
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

    @Transactional(readOnly = true)
    public TenantAdminSharePreview preview(StoreScope scope, TenantAdminShareProfileCommand command) {
        TenantAdminShareProfile current = getProfile(scope);
        TenantAdminShareProfileUpdate input = normalize(command);
        String template = hasText(input.reservationShareTemplate())
            ? input.reservationShareTemplate()
            : ReservationShareTemplateCatalog.defaultTemplate();
        assertKnownTemplateVariables(template);
        String shareText = templateRenderer.render(template, previewVariables(current, input));
        return new TenantAdminSharePreview(shareText);
    }

    @Transactional
    public TenantAdminShareProfile restoreDefaultTemplate(StoreScope scope) {
        if (!repository.clearTemplate(scope)) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return getProfile(scope);
    }

    private TenantAdminShareProfile toProfile(TenantAdminShareProfileRepository.Row row) {
        boolean usesDefaultTemplate = !hasText(row.reservationShareTemplate());
        return new TenantAdminShareProfile(
            clean(row.storeDisplayName()),
            clean(row.shareDisplayName()),
            clean(row.shareAddress()),
            clean(row.googleMapUrl()),
            clean(row.shareContactPhone()),
            clean(row.reservationShareNote()),
            usesDefaultTemplate ? ReservationShareTemplateCatalog.defaultTemplate() : row.reservationShareTemplate().trim(),
            ReservationShareTemplateCatalog.defaultTemplate(),
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
            optionalText(command.shareAddress()),
            optionalText(command.googleMapUrl()),
            optionalText(command.shareContactPhone()),
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
        for (String variable : ReservationShareTemplateCatalog.allowedVariables()) {
            variables.put(variable, "");
        }
        variables.put("storeName", firstText(input.shareDisplayName(), current.shareDisplayName(), current.storeDisplayName()));
        variables.put("reservationNo", "R-PREVIEW-0001");
        variables.put("reservationDate", "20-06-2030");
        variables.put("reservationTime", "11:30");
        variables.put("partySize", "4");
        variables.put("contactName", "Ada Guest");
        variables.put("maskedPhone", phoneMaskingPolicy.mask("+6591234567"));
        variables.put("storeAddress", clean(input.shareAddress()));
        variables.put("googleMapUrl", clean(input.googleMapUrl()));
        variables.put("storePhone", clean(input.shareContactPhone()));
        variables.put("arrivalNote", clean(input.reservationShareNote()));
        return variables;
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

    private static String clean(String value) {
        return hasText(value) ? value.trim() : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
