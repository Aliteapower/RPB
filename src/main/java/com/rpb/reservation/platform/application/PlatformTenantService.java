package com.rpb.reservation.platform.application;

import com.rpb.reservation.platform.persistence.PlatformTenantAdminAccountRepository;
import com.rpb.reservation.platform.persistence.PlatformTenantRepository;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaAsset;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformTenantService {
    private static final Set<String> STATUSES = Set.of("created", "active", "suspended", "closed");
    private static final Set<String> LIST_STATUSES = Set.of("all", "active", "deleted", "created", "suspended", "closed");
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_OFFSET = 0;
    private static final String PASSWORD_PATTERN = "^[A-Za-z0-9]{6}$";
    private static final String ONBOARDING_SINGLE_STORE = "single_store";
    private static final String ONBOARDING_GROUP_MULTI_STORE = "group_multi_store";

    private final PlatformTenantRepository repository;
    private final PlatformTenantAdminAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTenantAuditService auditService;
    private final CallScreenMediaService mediaService;

    public PlatformTenantService(
        PlatformTenantRepository repository,
        PlatformTenantAdminAccountRepository accountRepository,
        PasswordEncoder passwordEncoder,
        PlatformTenantAuditService auditService,
        CallScreenMediaService mediaService
    ) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.mediaService = mediaService;
    }

    @Transactional(readOnly = true)
    public PlatformTenantListResult listTenants(PlatformTenantSearchCommand command) {
        PlatformTenantSearchCriteria criteria = normalizeSearch(command);
        return new PlatformTenantListResult(
            repository.list(criteria),
            new PlatformTenantPage(criteria.limit(), criteria.offset(), repository.count(criteria))
        );
    }

    @Transactional(readOnly = true)
    public PlatformTenant getTenant(UUID tenantId) {
        return repository.findById(tenantId, true)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PlatformTenantAdminStoreAccess getTenantAdminStoreAccess(UUID tenantId) {
        repository.findById(tenantId, true)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
        return accountRepository.tenantAdminStoreAccess(tenantId);
    }

    @Transactional
    public PlatformTenant createTenant(PlatformTenantMutationCommand request, PlatformOperator operator) {
        NormalizedTenantInput input = normalizeCreate(request);
        try {
            PlatformTenant tenant = repository.insert(
                UUID.randomUUID(),
                input.tenantCode(),
                input.displayName(),
                input.status(),
                input.defaultLocale(),
                input.contactPhone(),
                input.address(),
                input.principalName()
            );
            repository.upsertTenantHostAlias(tenant.id(), tenant.tenantCode(), tenant.status());
            UUID defaultStoreId = null;
            if (ONBOARDING_SINGLE_STORE.equals(input.onboardingMode())
                || ONBOARDING_GROUP_MULTI_STORE.equals(input.onboardingMode())) {
                UUID defaultOperatingEntityId = repository.ensureDefaultOperatingEntity(
                    tenant.id(),
                    tenant.tenantCode(),
                    tenant.displayName(),
                    tenant.status(),
                    tenant.defaultLocale(),
                    tenant.contactPhone(),
                    tenant.address(),
                    tenant.principalName()
                );
                if (ONBOARDING_SINGLE_STORE.equals(input.onboardingMode())) {
                    defaultStoreId = repository.ensureDefaultStore(
                        tenant.id(),
                        tenant.tenantCode(),
                        tenant.displayName(),
                        tenant.status(),
                        tenant.defaultLocale(),
                        defaultOperatingEntityId
                    );
                }
            }
            accountRepository.upsertTenantAdminAccount(
                tenant.id(),
                tenant.tenantCode(),
                tenantAdminDisplayName(tenant),
                passwordHash(input.initialPassword()),
                defaultStoreId
            );
            auditService.recordCreated(tenant, operator);
            return tenant;
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_CODE_CONFLICT);
        }
    }

    @Transactional
    public PlatformTenant updateTenant(UUID tenantId, PlatformTenantMutationCommand request, PlatformOperator operator) {
        PlatformTenant existing = repository.findById(tenantId, true)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
        NormalizedTenantInput input = normalizeUpdate(existing, request);
        validateTenantAdminStoreAccess(tenantId, input.adminStoreIds(), input.defaultAdminStoreId());
        try {
            PlatformTenant tenant = repository.update(
                    tenantId,
                    input.tenantCode(),
                    input.displayName(),
                    input.status(),
                    input.defaultLocale(),
                    input.contactPhone(),
                    input.address(),
                    input.principalName()
                )
                .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
            repository.upsertTenantHostAlias(tenant.id(), tenant.tenantCode(), tenant.status());
            accountRepository.upsertTenantAdminAccount(
                tenant.id(),
                tenant.tenantCode(),
                tenantAdminDisplayName(tenant),
                input.password() == null ? null : passwordHash(input.password()),
                input.defaultAdminStoreId() == null
                    ? repository.findDefaultStoreId(tenant.id()).orElse(null)
                    : input.defaultAdminStoreId()
            );
            if (input.adminStoreIds() != null && !accountRepository.replaceTenantAdminStoreAccess(
                tenant.id(),
                input.adminStoreIds(),
                input.defaultAdminStoreId()
            )) {
                throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
            }
            auditService.recordUpdated(existing, tenant, operator, input.password() != null);
            return tenant;
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_CODE_CONFLICT);
        }
    }

    @Transactional
    public PlatformTenant deleteTenant(UUID tenantId, PlatformOperator operator) {
        PlatformTenant tenant = repository.softDelete(tenantId)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
        repository.archiveTenantHostAlias(tenant.id());
        auditService.recordDeleted(tenant, operator);
        return tenant;
    }

    @Transactional
    public PlatformTenant restoreTenant(UUID tenantId, PlatformOperator operator) {
        try {
            PlatformTenant tenant = repository.restore(tenantId)
                .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
            repository.upsertTenantHostAlias(tenant.id(), tenant.tenantCode(), tenant.status());
            auditService.recordRestored(tenant, operator);
            return tenant;
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_CODE_CONFLICT);
        }
    }

    @Transactional
    public PlatformTenant uploadTenantLogo(UUID tenantId, org.springframework.web.multipart.MultipartFile file, PlatformOperator operator) {
        PlatformTenant existing = repository.findById(tenantId, false)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
        CallScreenMediaAsset asset = mediaService.uploadTenantLogoMedia(tenantId, file);
        PlatformTenant tenant = repository.updateLogoMediaAsset(tenantId, asset.id())
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
        auditService.recordUpdated(existing, tenant, operator, false);
        return tenant;
    }

    @Transactional
    public PlatformTenant clearTenantLogo(UUID tenantId, PlatformOperator operator) {
        PlatformTenant existing = repository.findById(tenantId, false)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
        PlatformTenant tenant = repository.updateLogoMediaAsset(tenantId, null)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
        auditService.recordUpdated(existing, tenant, operator, false);
        return tenant;
    }

    @Transactional(readOnly = true)
    public CallScreenMediaContent readTenantLogoMedia(UUID tenantId, UUID assetId) {
        repository.findById(tenantId, false)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
        return mediaService.readTenantLogoMedia(tenantId, assetId);
    }

    private static NormalizedTenantInput normalizeCreate(PlatformTenantMutationCommand request) {
        if (request == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        String tenantCode = requiredText(request.tenantCode());
        String displayName = requiredText(request.displayName());
        String status = normalizeStatus(firstText(request.status(), "active"));
        String initialPassword = requiredPassword(request.initialPassword());
        return new NormalizedTenantInput(
            tenantCode,
            displayName,
            status,
            optionalText(request.defaultLocale()),
            optionalText(request.contactPhone()),
            optionalText(request.address()),
            optionalText(request.principalName()),
            initialPassword,
            null,
            normalizeOnboardingMode(request.onboardingMode()),
            null,
            null
        );
    }

    private static NormalizedTenantInput normalizeUpdate(PlatformTenant existing, PlatformTenantMutationCommand request) {
        if (request == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        String requestedTenantCode = optionalText(request.tenantCode());
        if (requestedTenantCode != null && !requestedTenantCode.equals(existing.tenantCode())) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        String displayName = firstText(request.displayName(), existing.displayName());
        String status = normalizeStatus(firstText(request.status(), existing.status()));
        List<UUID> adminStoreIds = normalizeStoreIds(request.adminStoreIds());
        UUID defaultAdminStoreId = request.defaultAdminStoreId();
        if (adminStoreIds == null && defaultAdminStoreId != null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return new NormalizedTenantInput(
            existing.tenantCode(),
            displayName,
            status,
            optionalTextOrExisting(request.defaultLocale(), existing.defaultLocale()),
            optionalTextOrExisting(request.contactPhone(), existing.contactPhone()),
            optionalTextOrExisting(request.address(), existing.address()),
            optionalTextOrExisting(request.principalName(), existing.principalName()),
            null,
            optionalPassword(request.password()),
            null,
            adminStoreIds,
            defaultAdminStoreId
        );
    }

    private void validateTenantAdminStoreAccess(UUID tenantId, List<UUID> storeIds, UUID defaultStoreId) {
        if (storeIds == null) {
            return;
        }
        if (storeIds.isEmpty() || defaultStoreId == null || !storeIds.contains(defaultStoreId)) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        Set<UUID> activeStoreIds = accountRepository.activeTenantStoreIds(tenantId, storeIds);
        if (activeStoreIds.size() != storeIds.size()) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static PlatformTenantSearchCriteria normalizeSearch(PlatformTenantSearchCommand command) {
        PlatformTenantSearchCommand safeCommand = command == null
            ? new PlatformTenantSearchCommand(null, null, false, null, null)
            : command;
        String keyword = optionalText(safeCommand.keyword());
        String status = optionalText(safeCommand.status());
        if (status == null) {
            status = "all";
        } else {
            status = status.toLowerCase(Locale.ROOT);
        }
        if (!LIST_STATUSES.contains(status)) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return new PlatformTenantSearchCriteria(
            keyword == null ? null : keyword.toLowerCase(Locale.ROOT),
            status,
            safeCommand.includeDeleted(),
            resolveLimit(safeCommand.limit()),
            resolveOffset(safeCommand.offset())
        );
    }

    private static String normalizeStatus(String status) {
        String normalized = requiredText(status).toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String normalizeOnboardingMode(String onboardingMode) {
        String normalized = optionalText(onboardingMode);
        if (normalized == null) {
            return ONBOARDING_SINGLE_STORE;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!ONBOARDING_SINGLE_STORE.equals(normalized) && !ONBOARDING_GROUP_MULTI_STORE.equals(normalized)) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String firstText(String first, String fallback) {
        String normalized = optionalText(first);
        return normalized == null ? requiredText(fallback) : normalized;
    }

    private static String optionalTextOrExisting(String value, String existing) {
        String normalized = optionalText(value);
        return normalized == null ? existing : normalized;
    }

    private static int resolveLimit(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            return DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(normalized);
            if (limit <= 0 || limit > MAX_LIMIT) {
                throw new NumberFormatException("invalid_limit");
            }
            return limit;
        } catch (NumberFormatException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static int resolveOffset(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            return DEFAULT_OFFSET;
        }
        try {
            int offset = Integer.parseInt(normalized);
            if (offset < 0) {
                throw new NumberFormatException("invalid_offset");
            }
            return offset;
        } catch (NumberFormatException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static String requiredPassword(String password) {
        String normalized = optionalPassword(password);
        if (normalized == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String optionalPassword(String password) {
        String normalized = optionalText(password);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches(PASSWORD_PATTERN)) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static List<UUID> normalizeStoreIds(List<UUID> storeIds) {
        if (storeIds == null) {
            return null;
        }
        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();
        for (UUID storeId : storeIds) {
            if (storeId == null) {
                throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
            }
            normalized.add(storeId);
        }
        return List.copyOf(normalized);
    }

    private String passwordHash(String password) {
        return passwordEncoder.encode(password.toLowerCase(Locale.ROOT));
    }

    private static String tenantAdminDisplayName(PlatformTenant tenant) {
        return firstText(tenant.principalName(), tenant.displayName());
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requiredText(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private record NormalizedTenantInput(
        String tenantCode,
        String displayName,
        String status,
        String defaultLocale,
        String contactPhone,
        String address,
        String principalName,
        String initialPassword,
        String password,
        String onboardingMode,
        List<UUID> adminStoreIds,
        UUID defaultAdminStoreId
    ) {
    }
}
