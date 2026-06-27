package com.rpb.reservation.platform.persistence;

import com.rpb.reservation.platform.application.PlatformTenant;
import com.rpb.reservation.platform.application.PlatformTenantSearchCriteria;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformTenantRepository {
    private final JdbcTemplate jdbc;

    public PlatformTenantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlatformTenant> list(PlatformTenantSearchCriteria criteria) {
        WhereClause where = whereClause(criteria);
        List<Object> args = new ArrayList<>(where.args());
        args.add(criteria.limit());
        args.add(criteria.offset());
        return jdbc.query(
            """
            select id, tenant_code, display_name, status, default_locale,
                   contact_phone, address, principal_name,
                   logo_media_asset_id,
                   created_at, updated_at, deleted_at
            from tenants
            %s
            order by deleted_at nulls first, tenant_code
            limit ? offset ?
            """.formatted(where.sql()),
            (rs, rowNum) -> tenant(rs),
            args.toArray()
        );
    }

    public int count(PlatformTenantSearchCriteria criteria) {
        WhereClause where = whereClause(criteria);
        Integer total = jdbc.queryForObject(
            "select count(*) from tenants %s".formatted(where.sql()),
            Integer.class,
            where.args().toArray()
        );
        return total == null ? 0 : total;
    }

    public Optional<PlatformTenant> findById(UUID tenantId, boolean includeDeleted) {
        return jdbc.query(
            """
            select id, tenant_code, display_name, status, default_locale,
                   contact_phone, address, principal_name,
                   logo_media_asset_id,
                   created_at, updated_at, deleted_at
            from tenants
            where id = ?
              and (? = true or deleted_at is null)
            """,
            (rs, rowNum) -> tenant(rs),
            tenantId,
            includeDeleted
        ).stream().findFirst();
    }

    public PlatformTenant insert(
        UUID tenantId,
        String tenantCode,
        String displayName,
        String status,
        String defaultLocale,
        String contactPhone,
        String address,
        String principalName
    ) {
        return jdbc.queryForObject(
            """
            insert into tenants (
                id, tenant_code, display_name, status, default_locale,
                contact_phone, address, principal_name
            )
            values (?, ?, ?, ?, ?, ?, ?, ?)
            returning id, tenant_code, display_name, status, default_locale,
                      contact_phone, address, principal_name,
                      logo_media_asset_id,
                      created_at, updated_at, deleted_at
            """,
            (rs, rowNum) -> tenant(rs),
            tenantId,
            tenantCode,
            displayName,
            status,
            defaultLocale,
            contactPhone,
            address,
            principalName
        );
    }

    public Optional<PlatformTenant> update(
        UUID tenantId,
        String tenantCode,
        String displayName,
        String status,
        String defaultLocale,
        String contactPhone,
        String address,
        String principalName
    ) {
        return jdbc.query(
            """
            update tenants
            set tenant_code = ?,
                display_name = ?,
                status = ?,
                default_locale = ?,
                contact_phone = ?,
                address = ?,
                principal_name = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
            returning id, tenant_code, display_name, status, default_locale,
                      contact_phone, address, principal_name,
                      logo_media_asset_id,
                      created_at, updated_at, deleted_at
            """,
            (rs, rowNum) -> tenant(rs),
            tenantCode,
            displayName,
            status,
            defaultLocale,
            contactPhone,
            address,
            principalName,
            tenantId
        ).stream().findFirst();
    }

    public Optional<PlatformTenant> softDelete(UUID tenantId) {
        return jdbc.query(
            """
            update tenants
            set deleted_at = coalesce(deleted_at, now()),
                updated_at = now(),
                version = version + 1
            where id = ?
            returning id, tenant_code, display_name, status, default_locale,
                      contact_phone, address, principal_name,
                      logo_media_asset_id,
                      created_at, updated_at, deleted_at
            """,
            (rs, rowNum) -> tenant(rs),
            tenantId
        ).stream().findFirst();
    }

    public Optional<PlatformTenant> restore(UUID tenantId) {
        return jdbc.query(
            """
            update tenants
            set deleted_at = null,
                updated_at = now(),
                version = version + 1
            where id = ?
            returning id, tenant_code, display_name, status, default_locale,
                      contact_phone, address, principal_name,
                      logo_media_asset_id,
                      created_at, updated_at, deleted_at
            """,
            (rs, rowNum) -> tenant(rs),
            tenantId
        ).stream().findFirst();
    }

    public Optional<PlatformTenant> updateLogoMediaAsset(UUID tenantId, UUID logoMediaAssetId) {
        return jdbc.query(
            """
            update tenants
            set logo_media_asset_id = ?,
                updated_at = now(),
                version = version + 1
            where id = ?
              and deleted_at is null
            returning id, tenant_code, display_name, status, default_locale,
                      contact_phone, address, principal_name,
                      logo_media_asset_id,
                      created_at, updated_at, deleted_at
            """,
            (rs, rowNum) -> tenant(rs),
            logoMediaAssetId,
            tenantId
        ).stream().findFirst();
    }

    private static PlatformTenant tenant(ResultSet rs) throws SQLException {
        return new PlatformTenant(
            rs.getObject("id", UUID.class),
            rs.getString("tenant_code"),
            rs.getString("display_name"),
            rs.getString("status"),
            rs.getString("default_locale"),
            rs.getString("contact_phone"),
            rs.getString("address"),
            rs.getString("principal_name"),
            rs.getObject("logo_media_asset_id", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("deleted_at", OffsetDateTime.class)
        );
    }

    private static WhereClause whereClause(PlatformTenantSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("where 1 = 1");
        List<Object> args = new ArrayList<>();

        String status = criteria.status();
        if ("deleted".equals(status)) {
            sql.append(" and deleted_at is not null");
        } else {
            if (!criteria.includeDeleted()) {
                sql.append(" and deleted_at is null");
            }
            if (status != null && !"all".equals(status)) {
                sql.append(" and status = ?");
                args.add(status);
            }
        }

        if (criteria.keyword() != null) {
            String keyword = "%" + criteria.keyword().toLowerCase() + "%";
            sql.append("""
                 and (
                    lower(tenant_code) like ?
                    or lower(display_name) like ?
                    or lower(coalesce(contact_phone, '')) like ?
                    or lower(coalesce(address, '')) like ?
                    or lower(coalesce(principal_name, '')) like ?
                 )
                """);
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }

        return new WhereClause(sql.toString(), args);
    }

    private record WhereClause(String sql, List<Object> args) {
    }
}
