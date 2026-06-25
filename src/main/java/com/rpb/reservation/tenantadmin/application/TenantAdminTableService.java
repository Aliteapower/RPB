package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.persistence.TenantAdminTableRepository;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAdminTableService {
    private static final Set<String> MAINTAINABLE_STATUSES = Set.of("available", "inactive");
    private static final int MAX_CAPACITY = 999;

    private final TenantAdminTableRepository repository;

    public TenantAdminTableService(TenantAdminTableRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public TenantAdminTableListResult listTables(StoreScope scope, TenantAdminSearchCommand command) {
        TenantAdminSearchCriteria criteria = TenantAdminSearchNormalizer.normalize(command);
        return new TenantAdminTableListResult(
            repository.list(scope, criteria),
            new TenantAdminPage(criteria.limit(), criteria.offset(), repository.count(scope, criteria))
        );
    }

    @Transactional(readOnly = true)
    public TenantAdminTable getTable(StoreScope scope, UUID tableId) {
        return repository.findById(scope, tableId)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.TABLE_NOT_FOUND));
    }

    @Transactional
    public TenantAdminTable createTable(StoreScope scope, TenantAdminTableMutationCommand command) {
        NormalizedTableInput input = normalizeCreate(command);
        try {
            return repository.insert(
                scope,
                UUID.randomUUID(),
                input.areaName(),
                areaCode(input.areaName()),
                input.tableCode(),
                input.capacity(),
                statusFromEnabled(input.enabled())
            );
        } catch (DataIntegrityViolationException exception) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.TABLE_CODE_CONFLICT);
        }
    }

    @Transactional
    public TenantAdminTable updateTable(StoreScope scope, UUID tableId, TenantAdminTableMutationCommand command) {
        TenantAdminTable existing = repository.findById(scope, tableId)
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.TABLE_NOT_FOUND));
        if (!MAINTAINABLE_STATUSES.contains(existing.status())) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.TABLE_IN_USE);
        }
        NormalizedTableInput input = normalizeUpdate(existing, command);
        try {
            return repository.update(
                    scope,
                    tableId,
                    input.areaName(),
                    areaCode(input.areaName()),
                    input.tableCode(),
                    input.capacity(),
                    statusFromEnabled(input.enabled())
                )
                .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.TABLE_NOT_FOUND));
        } catch (DataIntegrityViolationException exception) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.TABLE_CODE_CONFLICT);
        }
    }

    private static NormalizedTableInput normalizeCreate(TenantAdminTableMutationCommand command) {
        if (command == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return new NormalizedTableInput(
            requiredText(command.areaName()),
            requiredText(command.tableCode()),
            requiredCapacity(command.capacity()),
            command.enabled() == null || command.enabled()
        );
    }

    private static NormalizedTableInput normalizeUpdate(
        TenantAdminTable existing,
        TenantAdminTableMutationCommand command
    ) {
        if (command == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return new NormalizedTableInput(
            firstText(command.areaName(), existing.areaName()),
            firstText(command.tableCode(), existing.tableCode()),
            command.capacity() == null ? existing.capacity() : requiredCapacity(command.capacity()),
            command.enabled() == null ? existing.enabled() : command.enabled()
        );
    }

    private static int requiredCapacity(Integer capacity) {
        if (capacity == null || capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return capacity;
    }

    private static String statusFromEnabled(boolean enabled) {
        return enabled ? "available" : "inactive";
    }

    private static String areaCode(String areaName) {
        String normalized = areaName.trim();
        String ascii = normalized.toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
        if (ascii.isBlank()) {
            ascii = "NAME";
        }
        String hash = Integer.toHexString(normalized.toLowerCase(Locale.ROOT).hashCode()).toUpperCase(Locale.ROOT);
        return "AREA-" + ascii + "-" + hash;
    }

    private static String firstText(String first, String fallback) {
        String normalized = optionalText(first);
        return normalized == null ? requiredText(fallback) : normalized;
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

    private record NormalizedTableInput(
        String areaName,
        String tableCode,
        int capacity,
        boolean enabled
    ) {
    }
}
