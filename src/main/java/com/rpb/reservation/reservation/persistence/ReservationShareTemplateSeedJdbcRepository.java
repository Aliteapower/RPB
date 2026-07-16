package com.rpb.reservation.reservation.persistence;

import com.rpb.reservation.reservation.application.PlatformReservationShareTemplateSeed;
import com.rpb.reservation.reservation.application.ReservationShareTemplateSeed;
import com.rpb.reservation.reservation.application.port.out.PlatformReservationShareTemplateSeedRepository;
import com.rpb.reservation.reservation.application.port.out.ReservationShareTemplateSeedPort;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateCatalog;
import java.util.Optional;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationShareTemplateSeedJdbcRepository
    implements ReservationShareTemplateSeedPort, PlatformReservationShareTemplateSeedRepository {
    private final JdbcTemplate jdbc;

    public ReservationShareTemplateSeedJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReservationShareTemplateSeed> findActiveBySeedKey(String seedKey) {
        return jdbc.query(
            """
            select seed_key, template_text
            from platform_reservation_share_template_seeds
            where seed_key = ?
              and status = 'active'
              and deleted_at is null
            """,
            (rs, rowNum) -> new ReservationShareTemplateSeed(
                rs.getString("seed_key"),
                rs.getString("template_text")
            ),
            seedKey
        ).stream().findFirst();
    }

    @Override
    public Optional<PlatformReservationShareTemplateSeed> findBySeedKey(String seedKey) {
        return jdbc.query(
            """
            select seed_key, display_name, locale, template_text, status, version
            from platform_reservation_share_template_seeds
            where seed_key = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> new PlatformReservationShareTemplateSeed(
                rs.getString("seed_key"),
                rs.getString("display_name"),
                rs.getString("locale"),
                rs.getString("template_text"),
                rs.getString("status"),
                rs.getInt("version"),
                ReservationShareTemplateCatalog.allowedVariables()
            ),
            seedKey
        ).stream().findFirst();
    }

    @Override
    public PlatformReservationShareTemplateSeed update(
        String seedKey,
        String displayName,
        String locale,
        String templateText,
        String status
    ) {
        int updated = jdbc.update(
            """
            update platform_reservation_share_template_seeds
            set display_name = ?,
                locale = ?,
                template_text = ?,
                status = ?,
                updated_at = now(),
                version = version + 1
            where seed_key = ?
              and deleted_at is null
            """,
            displayName,
            locale,
            templateText,
            status,
            seedKey
        );
        if (updated != 1) {
            throw new DataRetrievalFailureException("reservation_share_template_seed_update_failed");
        }
        return findBySeedKey(seedKey)
            .orElseThrow(() -> new DataRetrievalFailureException("reservation_share_template_seed_not_found"));
    }
}
