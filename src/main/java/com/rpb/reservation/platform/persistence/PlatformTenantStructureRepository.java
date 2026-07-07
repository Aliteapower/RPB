package com.rpb.reservation.platform.persistence;

import com.rpb.reservation.platform.application.PlatformOperatingEntity;
import com.rpb.reservation.platform.application.PlatformStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformTenantStructureRepository {
    private final JdbcTemplate jdbc;

    public PlatformTenantStructureRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlatformOperatingEntity> listOperatingEntities(UUID tenantId) {
        return jdbc.query(
            """
            select id, tenant_id, entity_code, display_name, status,
                   default_locale, contact_phone, address, principal_name,
                   created_at, updated_at, deleted_at
            from operating_entities
            where tenant_id = ?
              and deleted_at is null
            order by lower(display_name), lower(entity_code), id
            """,
            (rs, rowNum) -> operatingEntity(rs),
            tenantId
        );
    }

    public Optional<PlatformOperatingEntity> findOperatingEntity(UUID tenantId, UUID operatingEntityId) {
        return jdbc.query(
            """
            select id, tenant_id, entity_code, display_name, status,
                   default_locale, contact_phone, address, principal_name,
                   created_at, updated_at, deleted_at
            from operating_entities
            where tenant_id = ?
              and id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> operatingEntity(rs),
            tenantId,
            operatingEntityId
        ).stream().findFirst();
    }

    public boolean activeOperatingEntityExists(UUID tenantId, UUID operatingEntityId) {
        Integer count = jdbc.queryForObject(
            """
            select count(*)
            from operating_entities
            where tenant_id = ?
              and id = ?
              and status = 'active'
              and deleted_at is null
            """,
            Integer.class,
            tenantId,
            operatingEntityId
        );
        return count != null && count == 1;
    }

    public PlatformOperatingEntity insertOperatingEntity(
        UUID operatingEntityId,
        UUID tenantId,
        String entityCode,
        String displayName,
        String status,
        String defaultLocale,
        String contactPhone,
        String address,
        String principalName
    ) {
        return jdbc.queryForObject(
            """
            insert into operating_entities (
                id, tenant_id, entity_code, display_name, status,
                default_locale, contact_phone, address, principal_name
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            returning id, tenant_id, entity_code, display_name, status,
                      default_locale, contact_phone, address, principal_name,
                      created_at, updated_at, deleted_at
            """,
            (rs, rowNum) -> operatingEntity(rs),
            operatingEntityId,
            tenantId,
            entityCode,
            displayName,
            status,
            defaultLocale,
            contactPhone,
            address,
            principalName
        );
    }

    public Optional<PlatformOperatingEntity> updateOperatingEntity(
        UUID tenantId,
        UUID operatingEntityId,
        String entityCode,
        String displayName,
        String status,
        String defaultLocale,
        String contactPhone,
        String address,
        String principalName
    ) {
        return jdbc.query(
            """
            update operating_entities
            set entity_code = ?,
                display_name = ?,
                status = ?,
                default_locale = ?,
                contact_phone = ?,
                address = ?,
                principal_name = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and id = ?
              and deleted_at is null
            returning id, tenant_id, entity_code, display_name, status,
                      default_locale, contact_phone, address, principal_name,
                      created_at, updated_at, deleted_at
            """,
            (rs, rowNum) -> operatingEntity(rs),
            entityCode,
            displayName,
            status,
            defaultLocale,
            contactPhone,
            address,
            principalName,
            tenantId,
            operatingEntityId
        ).stream().findFirst();
    }

    public List<PlatformStore> listStores(UUID tenantId) {
        return jdbc.query(
            storeSelect("""
            where store.tenant_id = ?
              and store.deleted_at is null
            order by lower(store.display_name), lower(store.store_code), store.id
            """),
            (rs, rowNum) -> store(rs),
            tenantId
        );
    }

    public Optional<PlatformStore> findStore(UUID tenantId, UUID storeId) {
        return jdbc.query(
            storeSelect("""
            where store.tenant_id = ?
              and store.id = ?
              and store.deleted_at is null
            """),
            (rs, rowNum) -> store(rs),
            tenantId,
            storeId
        ).stream().findFirst();
    }

    public PlatformStore insertStore(
        UUID storeId,
        UUID tenantId,
        UUID operatingEntityId,
        String storeCode,
        String storeName,
        String status,
        String timezone,
        String locale,
        String dateFormat,
        String timeFormat,
        String currency
    ) {
        return jdbc.queryForObject(
            """
            with inserted as (
                insert into stores (
                    id, tenant_id, operating_entity_id, store_code, display_name, status,
                    timezone, locale, date_format, time_format, currency
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning *
            )
            %s
            """.formatted(storeSelectFrom("inserted")),
            (rs, rowNum) -> store(rs),
            storeId,
            tenantId,
            operatingEntityId,
            storeCode,
            storeName,
            status,
            timezone,
            locale,
            dateFormat,
            timeFormat,
            currency
        );
    }

    public Optional<PlatformStore> updateStore(
        UUID tenantId,
        UUID storeId,
        UUID operatingEntityId,
        String storeCode,
        String storeName,
        String status,
        String timezone,
        String locale,
        String dateFormat,
        String timeFormat,
        String currency
    ) {
        return jdbc.query(
            """
            with updated as (
                update stores
                set operating_entity_id = ?,
                    store_code = ?,
                    display_name = ?,
                    status = ?,
                    timezone = ?,
                    locale = ?,
                    date_format = ?,
                    time_format = ?,
                    currency = ?,
                    updated_at = now(),
                    version = version + 1
                where tenant_id = ?
                  and id = ?
                  and deleted_at is null
                returning *
            )
            %s
            """.formatted(storeSelectFrom("updated")),
            (rs, rowNum) -> store(rs),
            operatingEntityId,
            storeCode,
            storeName,
            status,
            timezone,
            locale,
            dateFormat,
            timeFormat,
            currency,
            tenantId,
            storeId
        ).stream().findFirst();
    }

    private static String storeSelect(String tailSql) {
        return storeSelectFrom("stores") + " " + tailSql;
    }

    private static String storeSelectFrom(String tableName) {
        return """
            select
                store.id,
                store.tenant_id,
                store.operating_entity_id,
                entity.entity_code as operating_entity_code,
                entity.display_name as operating_entity_name,
                store.store_code,
                store.display_name,
                store.status,
                store.timezone,
                store.locale,
                store.date_format,
                store.time_format,
                store.currency,
                store.created_at,
                store.updated_at,
                store.deleted_at
            from %s store
            left join operating_entities entity
              on entity.id = store.operating_entity_id
             and entity.tenant_id = store.tenant_id
             and entity.deleted_at is null
            """.formatted(tableName);
    }

    private static PlatformOperatingEntity operatingEntity(ResultSet rs) throws SQLException {
        return new PlatformOperatingEntity(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("entity_code"),
            rs.getString("display_name"),
            rs.getString("status"),
            rs.getString("default_locale"),
            rs.getString("contact_phone"),
            rs.getString("address"),
            rs.getString("principal_name"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("deleted_at", OffsetDateTime.class)
        );
    }

    private static PlatformStore store(ResultSet rs) throws SQLException {
        return new PlatformStore(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("operating_entity_id", UUID.class),
            rs.getString("operating_entity_code"),
            rs.getString("operating_entity_name"),
            rs.getString("store_code"),
            rs.getString("display_name"),
            rs.getString("status"),
            rs.getString("timezone"),
            rs.getString("locale"),
            rs.getString("date_format"),
            rs.getString("time_format"),
            rs.getString("currency"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("deleted_at", OffsetDateTime.class)
        );
    }
}
