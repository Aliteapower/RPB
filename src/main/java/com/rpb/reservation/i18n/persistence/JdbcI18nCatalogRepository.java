package com.rpb.reservation.i18n.persistence;

import com.rpb.reservation.i18n.application.I18nCatalogKey;
import com.rpb.reservation.i18n.application.I18nCatalogStoredMessage;
import com.rpb.reservation.i18n.application.port.out.I18nCatalogRepository;
import com.rpb.reservation.i18n.application.port.out.I18nRuntimeMessageRepository;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcI18nCatalogRepository implements I18nCatalogRepository, I18nRuntimeMessageRepository {
    private final JdbcTemplate jdbc;

    public JdbcI18nCatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<I18nCatalogKey> findActiveKeys() {
        return findKeys(false);
    }

    @Override
    public List<I18nCatalogKey> findTenantEditableActiveKeys() {
        return findKeys(true);
    }

    @Override
    public List<I18nCatalogStoredMessage> findMessages(UUID tenantId, UUID storeId) {
        ScopeQuery scope = ScopeQuery.of(tenantId, storeId);
        return jdbc.query(
            """
            select id, i18n_key, locale, message, status, version
            from i18n_message_catalog
            where %s
              and deleted_at is null
            order by i18n_key asc, locale asc
            """.formatted(scope.predicate()),
            (rs, rowNum) -> message(rs),
            scope.args()
        );
    }

    @Override
    public List<I18nCatalogStoredMessage> findMessages(UUID tenantId, UUID storeId, Collection<String> i18nKeys) {
        List<String> keys = normalizeKeys(i18nKeys);
        if (keys.isEmpty()) {
            return List.of();
        }

        ScopeQuery scope = ScopeQuery.of(tenantId, storeId);
        List<Object> args = new ArrayList<>(scope.argList());
        args.addAll(keys);
        String placeholders = String.join(", ", Collections.nCopies(keys.size(), "?"));
        return jdbc.query(
            """
            select id, i18n_key, locale, message, status, version
            from i18n_message_catalog
            where %s
              and i18n_key in (%s)
              and deleted_at is null
            order by i18n_key asc, locale asc
            """.formatted(scope.predicate(), placeholders),
            (rs, rowNum) -> message(rs),
            args.toArray()
        );
    }

    @Override
    public boolean upsertMessage(
        UUID tenantId,
        UUID storeId,
        String i18nKey,
        String locale,
        String message,
        String status,
        Integer expectedVersion
    ) {
        Optional<I18nCatalogStoredMessage> current = findMessage(tenantId, storeId, i18nKey, locale);
        if (current.isPresent()) {
            if (versionMismatch(current.get().version(), expectedVersion)) {
                return false;
            }
            return jdbc.update(
                """
                update i18n_message_catalog
                set message = ?,
                    status = ?,
                    updated_at = now(),
                    version = version + 1
                where id = ?
                  and deleted_at is null
                """,
                message,
                status,
                current.get().id()
            ) == 1;
        }

        if (expectedVersion != null && expectedVersion > 0) {
            return false;
        }

        return jdbc.update(
            """
            insert into i18n_message_catalog (
                tenant_id, store_id, i18n_key, locale, message, status, version
            )
            values (?, ?, ?, ?, ?, ?, 0)
            """,
            tenantId,
            storeId,
            i18nKey,
            locale,
            message,
            status
        ) == 1;
    }

    @Override
    public boolean clearMessage(UUID tenantId, UUID storeId, String i18nKey, String locale, Integer expectedVersion) {
        Optional<I18nCatalogStoredMessage> current = findMessage(tenantId, storeId, i18nKey, locale);
        if (current.isEmpty()) {
            return expectedVersion == null || expectedVersion == 0;
        }
        if (versionMismatch(current.get().version(), expectedVersion)) {
            return false;
        }
        return jdbc.update(
            """
            update i18n_message_catalog
            set deleted_at = now(),
                updated_at = now(),
                version = version + 1
            where id = ?
              and deleted_at is null
            """,
            current.get().id()
        ) == 1;
    }

    private List<I18nCatalogKey> findKeys(boolean tenantEditableOnly) {
        String tenantFilter = tenantEditableOnly ? "and tenant_editable = true" : "";
        return jdbc.query(
            """
            select i18n_key,
                   message_namespace,
                   category,
                   display_name,
                   description,
                   text_kind,
                   tenant_editable,
                   placeholder_names,
                   status,
                   sort_order
            from i18n_message_key_registry
            where status = 'active'
              %s
            order by sort_order asc, message_namespace asc, category asc, i18n_key asc
            """.formatted(tenantFilter),
            (rs, rowNum) -> key(rs)
        );
    }

    private Optional<I18nCatalogStoredMessage> findMessage(UUID tenantId, UUID storeId, String i18nKey, String locale) {
        ScopeQuery scope = ScopeQuery.of(tenantId, storeId);
        List<Object> args = new ArrayList<>(scope.argList());
        args.add(i18nKey);
        args.add(locale);
        return jdbc.query(
            """
            select id, i18n_key, locale, message, status, version
            from i18n_message_catalog
            where %s
              and i18n_key = ?
              and locale = ?
              and deleted_at is null
            """.formatted(scope.predicate()),
            (rs, rowNum) -> message(rs),
            args.toArray()
        ).stream().findFirst();
    }

    private static I18nCatalogKey key(ResultSet rs) throws SQLException {
        return new I18nCatalogKey(
            rs.getString("i18n_key"),
            rs.getString("message_namespace"),
            rs.getString("category"),
            rs.getString("display_name"),
            rs.getString("description"),
            rs.getString("text_kind"),
            rs.getBoolean("tenant_editable"),
            stringArray(rs.getArray("placeholder_names")),
            rs.getString("status"),
            rs.getInt("sort_order")
        );
    }

    private static I18nCatalogStoredMessage message(ResultSet rs) throws SQLException {
        return new I18nCatalogStoredMessage(
            rs.getObject("id", UUID.class),
            rs.getString("i18n_key"),
            rs.getString("locale"),
            rs.getString("message"),
            rs.getString("status"),
            rs.getInt("version")
        );
    }

    private static List<String> stringArray(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object array = sqlArray.getArray();
        if (array instanceof String[] strings) {
            return List.of(strings);
        }
        if (array instanceof Object[] objects) {
            return Arrays.stream(objects)
                .map(String::valueOf)
                .toList();
        }
        return List.of();
    }

    private static boolean versionMismatch(int currentVersion, Integer expectedVersion) {
        return expectedVersion != null && expectedVersion != currentVersion;
    }

    private static List<String> normalizeKeys(Collection<String> i18nKeys) {
        if (i18nKeys == null || i18nKeys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String key : i18nKeys) {
            if (key != null && !key.isBlank()) {
                keys.add(key.trim());
            }
        }
        return List.copyOf(keys);
    }

    private record ScopeQuery(String predicate, List<Object> argList) {
        static ScopeQuery of(UUID tenantId, UUID storeId) {
            if (tenantId == null) {
                return new ScopeQuery("tenant_id is null and store_id is null", List.of());
            }
            if (storeId == null) {
                return new ScopeQuery("tenant_id = ? and store_id is null", List.of(tenantId));
            }
            return new ScopeQuery("tenant_id = ? and store_id = ?", List.of(tenantId, storeId));
        }

        Object[] args() {
            return argList.toArray();
        }
    }
}
