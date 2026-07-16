package com.rpb.reservation.tenantadmin.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.application.TenantAdminSearchCriteria;
import com.rpb.reservation.tenantadmin.application.TenantAdminTable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantAdminTableRepository {
    private final JdbcTemplate jdbc;

    public TenantAdminTableRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TenantAdminTable> list(StoreScope scope, TenantAdminSearchCriteria criteria) {
        WhereClause where = whereClause(scope, criteria.keyword());
        List<Object> args = new ArrayList<>(where.args());
        args.add(criteria.limit());
        args.add(criteria.offset());
        return jdbc.query(
            """
            select table_record.id, area.id as area_id, area.display_name as area_name,
                   area.sort_order as area_sort_order, table_record.table_code,
                   table_record.sort_order as table_sort_order, table_record.capacity_max, table_record.status,
                   table_record.created_at, table_record.updated_at
            from dining_tables table_record
            join store_areas area on area.id = table_record.area_id
             and area.tenant_id = table_record.tenant_id
             and area.store_id = table_record.store_id
            %s
            order by area.sort_order, area.display_name, table_record.sort_order, table_record.table_code
            limit ? offset ?
            """.formatted(where.sql()),
            (rs, rowNum) -> table(rs),
            args.toArray()
        );
    }

    public List<TenantAdminTable> listAll(StoreScope scope) {
        return jdbc.query(
            """
            select table_record.id, area.id as area_id, area.display_name as area_name,
                   area.sort_order as area_sort_order, table_record.table_code,
                   table_record.sort_order as table_sort_order, table_record.capacity_max, table_record.status,
                   table_record.created_at, table_record.updated_at
            from dining_tables table_record
            join store_areas area on area.id = table_record.area_id
             and area.tenant_id = table_record.tenant_id
             and area.store_id = table_record.store_id
            where table_record.tenant_id = ?
              and table_record.store_id = ?
              and table_record.deleted_at is null
              and area.deleted_at is null
            order by area.sort_order, area.display_name, table_record.sort_order, table_record.table_code
            """,
            (rs, rowNum) -> table(rs),
            scope.tenantId().value(),
            scope.storeId().value()
        );
    }

    public int count(StoreScope scope, TenantAdminSearchCriteria criteria) {
        WhereClause where = whereClause(scope, criteria.keyword());
        Integer total = jdbc.queryForObject(
            """
            select count(*)
            from dining_tables table_record
            join store_areas area on area.id = table_record.area_id
             and area.tenant_id = table_record.tenant_id
             and area.store_id = table_record.store_id
            %s
            """.formatted(where.sql()),
            Integer.class,
            where.args().toArray()
        );
        return total == null ? 0 : total;
    }

    public Optional<TenantAdminTable> findById(StoreScope scope, UUID tableId) {
        return jdbc.query(
            """
            select table_record.id, area.id as area_id, area.display_name as area_name,
                   area.sort_order as area_sort_order, table_record.table_code,
                   table_record.sort_order as table_sort_order, table_record.capacity_max, table_record.status,
                   table_record.created_at, table_record.updated_at
            from dining_tables table_record
            join store_areas area on area.id = table_record.area_id
             and area.tenant_id = table_record.tenant_id
             and area.store_id = table_record.store_id
            where table_record.id = ?
              and table_record.tenant_id = ?
              and table_record.store_id = ?
              and table_record.deleted_at is null
              and area.deleted_at is null
            """,
            (rs, rowNum) -> table(rs),
            tableId,
            scope.tenantId().value(),
            scope.storeId().value()
        ).stream().findFirst();
    }

    public Optional<TenantAdminTable> findByTableCode(StoreScope scope, String tableCode) {
        return jdbc.query(
            """
            select table_record.id, area.id as area_id, area.display_name as area_name,
                   area.sort_order as area_sort_order, table_record.table_code,
                   table_record.sort_order as table_sort_order, table_record.capacity_max, table_record.status,
                   table_record.created_at, table_record.updated_at
            from dining_tables table_record
            join store_areas area on area.id = table_record.area_id
             and area.tenant_id = table_record.tenant_id
             and area.store_id = table_record.store_id
            where table_record.table_code = ?
              and table_record.tenant_id = ?
              and table_record.store_id = ?
              and table_record.deleted_at is null
              and area.deleted_at is null
            """,
            (rs, rowNum) -> table(rs),
            tableCode,
            scope.tenantId().value(),
            scope.storeId().value()
        ).stream().findFirst();
    }

    public TenantAdminTable insert(
        StoreScope scope,
        UUID tableId,
        String areaName,
        String areaCode,
        Integer areaSortOrder,
        String tableCode,
        Integer tableSortOrder,
        int capacity,
        String status
    ) {
        UUID areaId = findOrCreateArea(scope, areaName, areaCode, areaSortOrder).id();
        int resolvedTableSortOrder = tableSortOrder == null ? nextTableSortOrder(scope, areaId) : tableSortOrder;
        jdbc.update(
            """
            insert into dining_tables (
                id, tenant_id, store_id, area_id, table_code, display_name,
                capacity_min, capacity_max, status, sort_order, is_combinable
            )
            values (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, true)
            """,
            tableId,
            scope.tenantId().value(),
            scope.storeId().value(),
            areaId,
            tableCode,
            tableCode,
            capacity,
            status,
            resolvedTableSortOrder
        );
        return findById(scope, tableId).orElseThrow();
    }

    public Optional<TenantAdminTable> update(
        StoreScope scope,
        UUID tableId,
        String areaName,
        String areaCode,
        Integer areaSortOrder,
        String tableCode,
        int tableSortOrder,
        int capacity,
        String status
    ) {
        UUID areaId = findOrCreateArea(scope, areaName, areaCode, areaSortOrder).id();
        jdbc.update(
            """
            update dining_tables
            set area_id = ?,
                table_code = ?,
                display_name = ?,
                capacity_min = 1,
                capacity_max = ?,
                status = ?,
                sort_order = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
              and tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            areaId,
            tableCode,
            tableCode,
            capacity,
            status,
            tableSortOrder,
            tableId,
            scope.tenantId().value(),
            scope.storeId().value()
        );
        return findById(scope, tableId);
    }

    private TenantAdminArea findOrCreateArea(StoreScope scope, String areaName, String areaCode, Integer sortOrder) {
        Optional<TenantAdminArea> existing = findArea(scope, areaCode);
        if (existing.isPresent()) {
            if (sortOrder != null) {
                updateAreaSort(scope, existing.get().id(), sortOrder);
            }
            return existing.get();
        }
        UUID areaId = UUID.randomUUID();
        try {
            jdbc.update(
                """
                insert into store_areas (
                    id, tenant_id, store_id, area_code, display_name, status, sort_order
                )
                values (?, ?, ?, ?, ?, 'active', ?)
                """,
                areaId,
                scope.tenantId().value(),
                scope.storeId().value(),
                areaCode,
                areaName,
                sortOrder == null ? nextAreaSortOrder(scope) : sortOrder
            );
        } catch (DataIntegrityViolationException ignored) {
            return findArea(scope, areaCode).orElseThrow();
        }
        return new TenantAdminArea(areaId, areaCode, areaName);
    }

    private int nextAreaSortOrder(StoreScope scope) {
        Integer maxSortOrder = jdbc.queryForObject(
            """
            select max(sort_order)
            from store_areas
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            Integer.class,
            scope.tenantId().value(),
            scope.storeId().value()
        );
        return maxSortOrder == null ? 0 : maxSortOrder + 1;
    }

    private int nextTableSortOrder(StoreScope scope, UUID areaId) {
        Integer maxSortOrder = jdbc.queryForObject(
            """
            select max(sort_order)
            from dining_tables
            where tenant_id = ?
              and store_id = ?
              and area_id = ?
              and deleted_at is null
            """,
            Integer.class,
            scope.tenantId().value(),
            scope.storeId().value(),
            areaId
        );
        return maxSortOrder == null ? 0 : maxSortOrder + 1;
    }

    private Optional<TenantAdminArea> findArea(StoreScope scope, String areaCode) {
        return jdbc.query(
            """
            select id, area_code, display_name
            from store_areas
            where tenant_id = ?
              and store_id = ?
              and area_code = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> new TenantAdminArea(
                rs.getObject("id", UUID.class),
                rs.getString("area_code"),
                rs.getString("display_name")
            ),
            scope.tenantId().value(),
            scope.storeId().value(),
            areaCode
        ).stream().findFirst();
    }

    private void updateAreaSort(StoreScope scope, UUID areaId, int sortOrder) {
        jdbc.update(
            """
            update store_areas
            set sort_order = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
              and tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            sortOrder,
            areaId,
            scope.tenantId().value(),
            scope.storeId().value()
        );
    }

    private static WhereClause whereClause(StoreScope scope, String keyword) {
        StringBuilder sql = new StringBuilder("""
            where table_record.tenant_id = ?
              and table_record.store_id = ?
              and table_record.deleted_at is null
              and area.deleted_at is null
            """);
        List<Object> args = new ArrayList<>();
        args.add(scope.tenantId().value());
        args.add(scope.storeId().value());
        if (keyword != null) {
            String pattern = "%" + keyword + "%";
            sql.append("""
                  and (
                    lower(table_record.table_code) like ?
                    or lower(table_record.display_name) like ?
                    or lower(area.display_name) like ?
                  )
                """);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        return new WhereClause(sql.toString(), args);
    }

    private static TenantAdminTable table(ResultSet rs) throws SQLException {
        String status = rs.getString("status");
        return new TenantAdminTable(
            rs.getObject("id", UUID.class),
            rs.getObject("area_id", UUID.class),
            rs.getString("area_name"),
            rs.getInt("area_sort_order"),
            rs.getString("table_code"),
            rs.getInt("table_sort_order"),
            rs.getInt("capacity_max"),
            status,
            !"inactive".equals(status),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private record TenantAdminArea(UUID id, String areaCode, String areaName) {
    }

    private record WhereClause(String sql, List<Object> args) {
    }
}
