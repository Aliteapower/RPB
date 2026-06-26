package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.PlatformProductLine;
import com.rpb.reservation.platformbilling.application.PlatformProductLineMutationCommand;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    public PlatformProductLine update(String appKey, PlatformProductLineMutationCommand command) {
        jdbc.update(
            """
            update platform_apps
            set app_name = ?,
                status = ?,
                description = ?,
                sort_order = ?,
                updated_at = now()
            where app_key = ?
            """,
            command.displayName(),
            command.status(),
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
