package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.PlatformProductLine;
import com.rpb.reservation.platformbilling.application.PlatformProductLineCreateCommand;
import com.rpb.reservation.platformbilling.application.PlatformProductLineMutationCommand;
import com.rpb.reservation.platformbilling.application.PlatformProductLinePage;
import com.rpb.reservation.platformbilling.application.PlatformProductLineQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPlatformProductLineRepository implements PlatformProductLineRepository {
    private final JdbcTemplate jdbc;

    public JdbcPlatformProductLineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PlatformProductLine> findAll() {
        return jdbc.query(
            """
            select app_key, app_name, status, default_entry_route, description,
                   sort_order, created_at, updated_at
            from platform_apps
            order by sort_order, app_key
            """,
            JdbcPlatformProductLineRepository::mapProductLine
        );
    }

    @Override
    public PlatformProductLinePage search(PlatformProductLineQuery query) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> parameters = new ArrayList<>();

        if (query.keyword() != null && !query.keyword().isBlank()) {
            where.append(" and (lower(app_key) like ? or lower(app_name) like ? or lower(coalesce(description, '')) like ?)");
            String keyword = "%" + query.keyword().trim().toLowerCase() + "%";
            parameters.add(keyword);
            parameters.add(keyword);
            parameters.add(keyword);
        }
        if (query.status() != null && !query.status().isBlank()) {
            where.append(" and status = ?");
            parameters.add(query.status().trim());
        }

        Long total = jdbc.queryForObject(
            "select count(*) from platform_apps" + where,
            Long.class,
            parameters.toArray()
        );
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(query.size());
        pageParameters.add(query.page() * query.size());

        List<PlatformProductLine> items = jdbc.query(
            """
            select app_key, app_name, status, default_entry_route, description,
                   sort_order, created_at, updated_at
            from platform_apps
            """
                + where
                + " order by sort_order, app_key limit ? offset ?",
            JdbcPlatformProductLineRepository::mapProductLine,
            pageParameters.toArray()
        );
        return new PlatformProductLinePage(items, total == null ? 0 : total, query.page(), query.size());
    }

    @Override
    public Optional<PlatformProductLine> findByAppKey(String appKey) {
        return jdbc.query(
            """
            select app_key, app_name, status, default_entry_route, description,
                   sort_order, created_at, updated_at
            from platform_apps
            where app_key = ?
            """,
            JdbcPlatformProductLineRepository::mapProductLine,
            appKey
        ).stream().findFirst();
    }

    @Override
    public PlatformProductLine create(PlatformProductLineCreateCommand command) {
        jdbc.update(
            """
            insert into platform_apps (app_key, app_name, status, default_entry_route, description, sort_order)
            values (?, ?, ?, ?, ?, ?)
            """,
            command.appKey(),
            command.displayName(),
            command.status(),
            command.defaultEntryRoute(),
            command.description(),
            command.sortOrder()
        );
        return findByAppKey(command.appKey()).orElseThrow();
    }

    @Override
    public PlatformProductLine update(String appKey, PlatformProductLineMutationCommand command) {
        jdbc.update(
            """
            update platform_apps
            set app_name = ?,
                status = ?,
                default_entry_route = ?,
                description = ?,
                sort_order = ?,
                updated_at = now()
            where app_key = ?
            """,
            command.displayName(),
            command.status(),
            command.defaultEntryRoute(),
            command.description(),
            command.sortOrder(),
            appKey
        );
        return findByAppKey(appKey).orElseThrow();
    }

    static PlatformProductLine mapProductLine(ResultSet resultSet, int rowNum) throws SQLException {
        return new PlatformProductLine(
            resultSet.getString("app_key"),
            resultSet.getString("app_name"),
            resultSet.getString("status"),
            resultSet.getString("default_entry_route"),
            resultSet.getString("description"),
            resultSet.getInt("sort_order"),
            resultSet.getObject("created_at", java.time.OffsetDateTime.class),
            resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
