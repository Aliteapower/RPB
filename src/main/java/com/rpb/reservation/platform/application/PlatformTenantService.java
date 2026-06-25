package com.rpb.reservation.platform.application;

import com.rpb.reservation.platform.persistence.PlatformTenantAdminAccountRepository;
import com.rpb.reservation.platform.persistence.PlatformTenantRepository;
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

    private final PlatformTenantRepository repository;
    private final PlatformTenantAdminAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTenantAuditService auditService;

    public PlatformTenantService(
        PlatformTenantRepository repository,
        PlatformTenantAdminAccountRepository accountRepository,
        PasswordEncoder passwordEncoder,
        PlatformTenantAuditService auditService
    ) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
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
            accountRepository.upsertTenantAdminAccount(
                tenant.id(),
                tenant.tenantCode(),
                tenantAdminDisplayName(tenant),
                passwordHash(input.initialPassword())
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
            accountRepository.upsertTenantAdminAccount(
                tenant.id(),
                tenant.tenantCode(),
                tenantAdminDisplayName(tenant),
                input.password() == null ? null : passwordHash(input.password())
            );
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
        auditService.recordDeleted(tenant, operator);
        return tenant;
    }

    @Transactional
    public PlatformTenant restoreTenant(UUID tenantId, PlatformOperator operator) {
        try {
            PlatformTenant tenant = repository.restore(tenantId)
                .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
            auditService.recordRestored(tenant, operator);
            return tenant;
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_CODE_CONFLICT);
        }
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
        return new NormalizedTenantInput(
            existing.tenantCode(),
            displayName,
            status,
            optionalTextOrExisting(request.defaultLocale(), existing.defaultLocale()),
            optionalTextOrExisting(request.contactPhone(), existing.contactPhone()),
            optionalTextOrExisting(request.address(), existing.address()),
            optionalTextOrExisting(request.principalName(), existing.principalName()),
            null,
            optionalPassword(request.password())
        );
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
        String password
    ) {
    }
}
