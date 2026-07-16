package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.common.excel.ExcelColumn;
import com.rpb.reservation.common.excel.ExcelRow;
import com.rpb.reservation.common.excel.ExcelWorkbookService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.persistence.TenantAdminTableRepository;
import java.util.List;
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
    private static final String TABLES_SHEET_NAME = "tables";
    private static final String AREA_SORT_HEADER = "大类排序";
    private static final String TABLE_SORT_HEADER = "桌号排序";
    private static final String AREA_NAME_HEADER = "分区组";
    private static final String TABLE_CODE_HEADER = "桌号";
    private static final String CAPACITY_HEADER = "人数";
    private static final String ENABLED_HEADER = "启用";
    private static final List<String> TABLE_IMPORT_HEADERS = List.of(
        AREA_SORT_HEADER,
        TABLE_SORT_HEADER,
        AREA_NAME_HEADER,
        TABLE_CODE_HEADER,
        CAPACITY_HEADER,
        ENABLED_HEADER
    );

    private final TenantAdminTableRepository repository;
    private final ExcelWorkbookService excelWorkbookService;

    public TenantAdminTableService(TenantAdminTableRepository repository, ExcelWorkbookService excelWorkbookService) {
        this.repository = repository;
        this.excelWorkbookService = excelWorkbookService;
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
                input.areaSortOrder(),
                input.tableCode(),
                input.tableSortOrder(),
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
        NormalizedTableInput input = normalizeUpdate(existing, command);
        try {
            if (!MAINTAINABLE_STATUSES.contains(existing.status())) {
                return updateBusyTableSortOrders(scope, existing, input);
            }
            return repository.update(
                    scope,
                    tableId,
                    input.areaName(),
                    areaCode(input.areaName()),
                    input.areaSortOrder(),
                    input.tableCode(),
                    input.tableSortOrder(),
                    input.capacity(),
                    statusFromEnabled(input.enabled())
                )
                .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.TABLE_NOT_FOUND));
        } catch (DataIntegrityViolationException exception) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.TABLE_CODE_CONFLICT);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportTables(StoreScope scope) {
        List<ExcelColumn<TenantAdminTable>> columns = List.of(
            new ExcelColumn<>(AREA_SORT_HEADER, TenantAdminTable::areaSortOrder),
            new ExcelColumn<>(TABLE_SORT_HEADER, TenantAdminTable::tableSortOrder),
            new ExcelColumn<>(AREA_NAME_HEADER, TenantAdminTable::areaName),
            new ExcelColumn<>(TABLE_CODE_HEADER, TenantAdminTable::tableCode),
            new ExcelColumn<>(CAPACITY_HEADER, TenantAdminTable::capacity),
            new ExcelColumn<>(ENABLED_HEADER, table -> table.enabled() ? "启用" : "停用")
        );
        return excelWorkbookService.writeSheet(TABLES_SHEET_NAME, columns, repository.listAll(scope));
    }

    @Transactional
    public TenantAdminTableImportSummary importTables(StoreScope scope, byte[] content) {
        List<ExcelRow> rows = readRows(content);
        int created = 0;
        int updated = 0;

        for (ExcelRow row : rows) {
            TenantAdminTableExcelRow tableRow = toTableRow(row);
            NormalizedTableInput input = new NormalizedTableInput(
                tableRow.areaName(),
                tableRow.tableCode(),
                tableRow.capacity(),
                tableRow.enabled(),
                tableRow.areaSortOrder(),
                tableRow.tableSortOrder()
            );
            TenantAdminTable existing = repository.findByTableCode(scope, input.tableCode()).orElse(null);
            if (existing == null) {
                repository.insert(
                    scope,
                    UUID.randomUUID(),
                    input.areaName(),
                    areaCode(input.areaName()),
                    input.areaSortOrder(),
                    input.tableCode(),
                    input.tableSortOrder(),
                    input.capacity(),
                    statusFromEnabled(input.enabled())
                );
                created++;
                continue;
            }
            if (!MAINTAINABLE_STATUSES.contains(existing.status())) {
                updateBusyTableSortOrders(scope, existing, input);
                updated++;
                continue;
            }
            repository.update(
                scope,
                existing.id(),
                input.areaName(),
                areaCode(input.areaName()),
                input.areaSortOrder(),
                input.tableCode(),
                input.tableSortOrder(),
                input.capacity(),
                statusFromEnabled(input.enabled())
            );
            updated++;
        }

        return new TenantAdminTableImportSummary(rows.size(), created, updated);
    }

    private TenantAdminTable updateBusyTableSortOrders(
        StoreScope scope,
        TenantAdminTable existing,
        NormalizedTableInput input
    ) {
        if (
            !input.enabled()
                || !existing.areaName().equals(input.areaName())
                || !existing.tableCode().equals(input.tableCode())
                || existing.capacity() != input.capacity()
        ) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.TABLE_IN_USE);
        }
        return repository.update(
                scope,
                existing.id(),
                existing.areaName(),
                areaCode(existing.areaName()),
                input.areaSortOrder(),
                existing.tableCode(),
                input.tableSortOrder(),
                existing.capacity(),
                existing.status()
            )
            .orElseThrow(() -> new TenantAdminServiceException(TenantAdminServiceErrorCode.TABLE_NOT_FOUND));
    }

    private List<ExcelRow> readRows(byte[] content) {
        try {
            return excelWorkbookService.readFirstSheet(content, TABLE_IMPORT_HEADERS);
        } catch (IllegalArgumentException exception) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static TenantAdminTableExcelRow toTableRow(ExcelRow row) {
        return new TenantAdminTableExcelRow(
            requiredInteger(row.cell(AREA_SORT_HEADER)),
            requiredInteger(row.cell(TABLE_SORT_HEADER)),
            requiredText(row.cell(AREA_NAME_HEADER)),
            requiredText(row.cell(TABLE_CODE_HEADER)),
            requiredCapacity(requiredInteger(row.cell(CAPACITY_HEADER))),
            requiredEnabled(row.cell(ENABLED_HEADER))
        );
    }

    private static int requiredInteger(String value) {
        String normalized = requiredText(value);
        try {
            if (normalized.endsWith(".0")) {
                normalized = normalized.substring(0, normalized.length() - 2);
            }
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static boolean requiredEnabled(String value) {
        String normalized = requiredText(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "y", "是", "启用" -> true;
            case "false", "0", "no", "n", "否", "停用" -> false;
            default -> throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        };
    }

    private static NormalizedTableInput normalizeCreate(TenantAdminTableMutationCommand command) {
        if (command == null) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return new NormalizedTableInput(
            requiredText(command.areaName()),
            requiredText(command.tableCode()),
            requiredCapacity(command.capacity()),
            command.enabled() == null || command.enabled(),
            optionalSortOrder(command.areaSortOrder()),
            optionalSortOrder(command.tableSortOrder())
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
            command.enabled() == null ? existing.enabled() : command.enabled(),
            command.areaSortOrder() == null ? existing.areaSortOrder() : optionalSortOrder(command.areaSortOrder()),
            command.tableSortOrder() == null ? existing.tableSortOrder() : optionalSortOrder(command.tableSortOrder())
        );
    }

    private static Integer optionalSortOrder(Integer sortOrder) {
        if (sortOrder == null) {
            return null;
        }
        if (sortOrder < 0) {
            throw new TenantAdminServiceException(TenantAdminServiceErrorCode.REQUEST_INVALID);
        }
        return sortOrder;
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
        boolean enabled,
        Integer areaSortOrder,
        Integer tableSortOrder
    ) {
    }
}
