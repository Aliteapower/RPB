package com.rpb.reservation.platform.persistence;

import com.rpb.reservation.platform.application.PlatformOperatingEntity;
import com.rpb.reservation.platform.application.PlatformStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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

    public boolean lockActiveOperatingEntity(UUID tenantId, UUID operatingEntityId) {
        return !jdbc.query(
            """
            select id
            from operating_entities
            where tenant_id = ?
              and id = ?
              and status = 'active'
              and deleted_at is null
            for key share
            """,
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            tenantId,
            operatingEntityId
        ).isEmpty();
    }

    public Optional<PlatformOperatingEntity> findOperatingEntityForUpdate(UUID tenantId, UUID operatingEntityId) {
        return jdbc.query(
            """
            select id, tenant_id, entity_code, display_name, status,
                   default_locale, contact_phone, address, principal_name,
                   created_at, updated_at, deleted_at
            from operating_entities
            where tenant_id = ?
              and id = ?
              and deleted_at is null
            for update
            """,
            (rs, rowNum) -> operatingEntity(rs),
            tenantId,
            operatingEntityId
        ).stream().findFirst();
    }

    public boolean currentStoreExists(UUID tenantId, UUID operatingEntityId) {
        Integer count = jdbc.queryForObject(
            """
            select count(*)
            from stores
            where tenant_id = ?
              and operating_entity_id = ?
              and deleted_at is null
            """,
            Integer.class,
            tenantId,
            operatingEntityId
        );
        return count != null && count > 0;
    }

    public Optional<PlatformOperatingEntity> softDeleteOperatingEntity(UUID tenantId, UUID operatingEntityId) {
        return jdbc.query(
            """
            update operating_entities
            set status = 'archived',
                deleted_at = coalesce(deleted_at, now()),
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
            tenantId,
            operatingEntityId
        ).stream().findFirst();
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

    public Optional<PlatformStore> softDeleteStore(UUID tenantId, UUID storeId) {
        return jdbc.query(
            """
            with deleted as (
                update stores
                set status = 'inactive',
                    deleted_at = coalesce(deleted_at, now()),
                    updated_at = now(),
                    version = version + 1
                where tenant_id = ?
                  and id = ?
                  and deleted_at is null
                returning *
            )
            %s
            """.formatted(storeSelectFrom("deleted")),
            (rs, rowNum) -> store(rs),
            tenantId,
            storeId
        ).stream().findFirst();
    }

    public UUID upsertStoreHostAlias(UUID tenantId, UUID storeId, String storeCode, String storeStatus) {
        if (activeTenantCodeConflictExists(tenantId, storeCode)) {
            throw new DataIntegrityViolationException("tenant_host_alias_conflicts_with_tenant_code");
        }
        String aliasStatus = "active".equals(storeStatus) ? "active" : "inactive";
        List<UUID> updated = jdbc.query(
            """
            update tenant_host_aliases
            set alias_code = ?,
                status = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and default_store_id = ?
              and alias_type = 'store'
              and deleted_at is null
            returning id
            """,
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            storeCode,
            aliasStatus,
            tenantId,
            storeId
        );
        if (!updated.isEmpty()) {
            return updated.getFirst();
        }
        return jdbc.queryForObject(
            """
            insert into tenant_host_aliases (
                tenant_id, alias_code, alias_type, default_store_id, status
            )
            values (?, ?, 'store', ?, ?)
            returning id
            """,
            UUID.class,
            tenantId,
            storeCode,
            storeId,
            aliasStatus
        );
    }

    public List<UUID> archiveStoreHostAliases(UUID tenantId, UUID storeId) {
        return jdbc.query(
            """
            update tenant_host_aliases
            set status = 'archived',
                deleted_at = coalesce(deleted_at, now()),
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and default_store_id = ?
              and alias_type = 'store'
              and deleted_at is null
            returning id
            """,
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            tenantId,
            storeId
        );
    }

    public void archiveStoreAccountAccess(UUID tenantId, UUID storeId) {
        jdbc.update(
            """
            update auth_account_store_access
            set deleted_at = coalesce(deleted_at, now())
            where tenant_id = ?
              and store_id = ?
              and deleted_at is null
            """,
            tenantId,
            storeId
        );
    }

    public void refreshDefaultStoreReferences(UUID tenantId, UUID deletedStoreId) {
        jdbc.update(
            """
            update auth_accounts account
            set default_store_id = (
                    select access.store_id
                    from auth_account_store_access access
                    join stores store
                      on store.id = access.store_id
                     and store.tenant_id = access.tenant_id
                     and store.status = 'active'
                     and store.deleted_at is null
                    where access.account_id = account.id
                      and access.tenant_id = account.tenant_id
                      and access.deleted_at is null
                    order by lower(store.display_name), lower(store.store_code), store.id
                    limit 1
                ),
                updated_at = now(),
                version = version + 1
            where account.tenant_id = ?
              and account.default_store_id = ?
              and account.deleted_at is null
            """,
            tenantId,
            deletedStoreId
        );
    }

    public void cancelStoreSubscriptionItems(UUID tenantId, UUID storeId) {
        jdbc.update(
            """
            update tenant_product_subscription_items
            set status = 'cancelled',
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and store_id = ?
              and scope_type = 'store'
              and status in ('active', 'suspended')
            """,
            tenantId,
            storeId
        );
    }

    public void refreshStoreSubscriptionAggregates(UUID tenantId, UUID storeId) {
        jdbc.update(
            """
            with affected as (
                select distinct subscription_id
                from tenant_product_subscription_items
                where tenant_id = ?
                  and store_id = ?
                  and scope_type = 'store'
            ),
            active_totals as (
                select
                    affected.subscription_id,
                    coalesce(sum(case when active_store.id is not null then item.amount else 0 end), 0) as amount,
                    min(case when active_store.id is not null then item.current_period_start end) as current_period_start,
                    max(case when active_store.id is not null then item.current_period_end end) as current_period_end,
                    max(case when active_store.id is not null then item.currency end) as currency
                from affected
                left join tenant_product_subscription_items item
                  on item.subscription_id = affected.subscription_id
                 and item.tenant_id = ?
                 and item.scope_type = 'store'
                 and item.status = 'active'
                left join stores active_store
                  on active_store.id = item.store_id
                 and active_store.tenant_id = item.tenant_id
                 and active_store.status = 'active'
                 and active_store.deleted_at is null
                group by affected.subscription_id
            )
            update tenant_product_subscriptions subscription
            set amount = active_totals.amount,
                current_period_start = coalesce(active_totals.current_period_start, subscription.current_period_start),
                current_period_end = coalesce(active_totals.current_period_end, subscription.current_period_end),
                currency = coalesce(active_totals.currency, subscription.currency),
                updated_at = now(),
                version = subscription.version + 1
            from active_totals
            where subscription.tenant_id = ?
              and subscription.id = active_totals.subscription_id
            """,
            tenantId,
            storeId,
            tenantId,
            tenantId
        );
    }

    private boolean activeTenantCodeConflictExists(UUID tenantId, String aliasCode) {
        Integer count = jdbc.queryForObject(
            """
            select count(*)
            from tenants
            where lower(tenant_code) = lower(?)
              and id <> ?
              and status = 'active'
              and deleted_at is null
            """,
            Integer.class,
            aliasCode,
            tenantId
        );
        return count != null && count > 0;
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
