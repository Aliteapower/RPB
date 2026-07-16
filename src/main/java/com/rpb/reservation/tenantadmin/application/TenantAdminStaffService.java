package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.persistence.TenantAdminStaffRepository;
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
public class TenantAdminStaffService {
    private static final Set<String> STATUSES = Set.of("active", "disabled", "locked");
    private static final String PASSWORD_PATTERN = "^[A-Za-z0-9]{6}$";

    private final TenantAdminStaffRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TenantAdminStaffAuditService auditService;

    public TenantAdminStaffService(
        TenantAdminStaffRepository repository,
        PasswordEncoder passwordEncoder,
        TenantAdminStaffAuditService auditService
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public TenantAdminStaffListResult listStaff(StoreScope scope, TenantAdminSearchCommand command) {
        TenantAdminSearchCriteria criteria = TenantAdminSearchNormalizer.normalize(command);
        return new TenantAdminStaffListResult(
            repository.list(scope, criteria),
            new TenantAdminPage(criteria.limit(), criteria.offset(), repository.count(scope, criteria))
        );
    }

    @Transactional(readOnly = true)
    public TenantAdminStaff getStaff(StoreScope scope, UUID staffId) {
        return repository.findById(scope, staffId)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.STAFF_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public TenantAdminStaff getCurrentTenantAdmin(StoreScope scope, UUID accountId) {
        return repository.findTenantAdminById(scope, accountId)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.STAFF_NOT_FOUND));
    }

    @Transactional
    public TenantAdminStaff createStaff(StoreScope scope, TenantAdminStaffMutationCommand command) {
        NormalizedStaffInput input = normalizeCreate(scope, command);
        validateStoreAccess(scope, input.storeIds(), input.defaultStoreId());
        try {
            return repository.insert(
                scope,
                UUID.randomUUID(),
                input.employeeNo(),
                input.name(),
                input.phone(),
                input.email(),
                passwordHash(input.password()),
                input.defaultStoreId(),
                input.storeIds()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.STAFF_CODE_CONFLICT);
        }
    }

    @Transactional
    public TenantAdminStaff updateStaff(StoreScope scope, UUID staffId, TenantAdminStaffMutationCommand command) {
        TenantAdminStaff existing = repository.findById(scope, staffId)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.STAFF_NOT_FOUND));
        NormalizedStaffInput input = normalizeUpdate(existing, command);
        validateStoreAccess(scope, input.storeIds(), input.defaultStoreId());
        return repository.update(
                scope,
                staffId,
                input.name(),
                input.phone(),
                input.email(),
                input.status(),
                input.password() == null ? null : passwordHash(input.password()),
                input.defaultStoreId(),
                input.storeIds(),
                input.replaceStoreAccess()
            )
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.STAFF_NOT_FOUND));
    }

    @Transactional
    public TenantAdminStaff updateCurrentTenantAdmin(
        StoreScope scope,
        UUID accountId,
        TenantAdminStaffMutationCommand command
    ) {
        TenantAdminStaff existing = repository.findTenantAdminById(scope, accountId)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.STAFF_NOT_FOUND));
        NormalizedStaffInput input = normalizeTenantAdminSelfUpdate(existing, command);
        TenantAdminStaff updated = repository.updateTenantAdminSelf(
                scope,
                accountId,
                input.name(),
                input.phone(),
                input.email(),
                input.password() == null ? null : passwordHash(input.password())
            )
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.STAFF_NOT_FOUND));
        auditService.recordSelfUpdated(scope, existing, updated, accountId, input.password() != null);
        return updated;
    }

    private static NormalizedStaffInput normalizeCreate(StoreScope scope, TenantAdminStaffMutationCommand command) {
        if (command == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        List<UUID> storeIds = normalizeStoreIds(command.storeIds(), List.of(scope.storeId().value()));
        UUID defaultStoreId = normalizeDefaultStoreId(command.defaultStoreId(), storeIds, scope.storeId().value());
        return new NormalizedStaffInput(
            requiredText(command.employeeNo()),
            requiredText(command.name()),
            optionalText(command.phone()),
            optionalText(command.email()),
            normalizeStatus(firstText(command.status(), "active")),
            requiredPassword(command.password()),
            defaultStoreId,
            storeIds,
            true
        );
    }

    private static NormalizedStaffInput normalizeUpdate(
        TenantAdminStaff existing,
        TenantAdminStaffMutationCommand command
    ) {
        if (command == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        String requestedEmployeeNo = optionalText(command.employeeNo());
        if (requestedEmployeeNo != null && !requestedEmployeeNo.equals(existing.employeeNo())) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        boolean replaceStoreAccess = command.storeIds() != null;
        List<UUID> storeIds = normalizeStoreIds(command.storeIds(), existing.storeIds());
        UUID defaultStoreId = normalizeDefaultStoreId(command.defaultStoreId(), storeIds, existing.defaultStoreId());
        return new NormalizedStaffInput(
            existing.employeeNo(),
            firstText(command.name(), existing.name()),
            optionalTextOrExisting(command.phone(), existing.phone()),
            optionalTextOrExisting(command.email(), existing.email()),
            normalizeStatus(firstText(command.status(), existing.status())),
            optionalPassword(command.password()),
            defaultStoreId,
            storeIds,
            replaceStoreAccess
        );
    }

    private static NormalizedStaffInput normalizeTenantAdminSelfUpdate(
        TenantAdminStaff existing,
        TenantAdminStaffMutationCommand command
    ) {
        if (command == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        if (
            optionalText(command.employeeNo()) != null
                || optionalText(command.status()) != null
                || command.storeIds() != null
                || command.defaultStoreId() != null
        ) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return new NormalizedStaffInput(
            existing.employeeNo(),
            firstText(command.name(), existing.name()),
            optionalTextOrExisting(command.phone(), existing.phone()),
            optionalTextOrExisting(command.email(), existing.email()),
            existing.status(),
            optionalPassword(command.password()),
            existing.defaultStoreId(),
            existing.storeIds(),
            false
        );
    }

    private void validateStoreAccess(StoreScope scope, List<UUID> storeIds, UUID defaultStoreId) {
        if (storeIds.isEmpty() || defaultStoreId == null || !storeIds.contains(defaultStoreId)) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        Set<UUID> activeStoreIds = repository.activeStoreIds(scope.tenantId().value(), storeIds);
        if (activeStoreIds.size() != storeIds.size()) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static List<UUID> normalizeStoreIds(List<UUID> requestedStoreIds, List<UUID> fallbackStoreIds) {
        List<UUID> source = requestedStoreIds == null ? fallbackStoreIds : requestedStoreIds;
        if (source == null || source.isEmpty()) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        for (UUID storeId : source) {
            if (storeId == null) {
                throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
            }
            unique.add(storeId);
        }
        return List.copyOf(unique);
    }

    private static UUID normalizeDefaultStoreId(UUID requestedDefaultStoreId, List<UUID> storeIds, UUID fallbackDefaultStoreId) {
        UUID defaultStoreId = requestedDefaultStoreId;
        if (defaultStoreId == null) {
            defaultStoreId = fallbackDefaultStoreId != null && storeIds.contains(fallbackDefaultStoreId)
                ? fallbackDefaultStoreId
                : storeIds.get(0);
        }
        if (!storeIds.contains(defaultStoreId)) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return defaultStoreId;
    }

    private static String normalizeStatus(String status) {
        String normalized = requiredText(status).toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String requiredPassword(String password) {
        String normalized = optionalPassword(password);
        if (normalized == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String optionalPassword(String password) {
        String normalized = optionalText(password);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches(PASSWORD_PATTERN)) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private String passwordHash(String password) {
        return passwordEncoder.encode(password.toLowerCase(Locale.ROOT));
    }

    private static String firstText(String first, String fallback) {
        String normalized = optionalText(first);
        return normalized == null ? requiredText(fallback) : normalized;
    }

    private static String optionalTextOrExisting(String value, String existing) {
        String normalized = optionalText(value);
        return normalized == null ? existing : normalized;
    }

    private static String requiredText(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record NormalizedStaffInput(
        String employeeNo,
        String name,
        String phone,
        String email,
        String status,
        String password,
        UUID defaultStoreId,
        List<UUID> storeIds,
        boolean replaceStoreAccess
    ) {
        private NormalizedStaffInput {
            storeIds = storeIds == null ? List.of() : List.copyOf(storeIds);
        }
    }
}
