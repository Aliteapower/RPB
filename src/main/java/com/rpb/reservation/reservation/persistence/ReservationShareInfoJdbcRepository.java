package com.rpb.reservation.reservation.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.port.out.ReservationShareInfoReadPort;
import com.rpb.reservation.reservation.application.port.out.ReservationShareInfoRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationShareInfoJdbcRepository implements ReservationShareInfoReadPort {
    private final JdbcTemplate jdbc;

    public ReservationShareInfoJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReservationShareInfoRow> findByReservationId(StoreScope scope, UUID reservationId) {
        return jdbc.query(
            """
            select reservation.id as reservation_id,
                   reservation.reservation_code as reservation_no,
                   reservation.party_size,
                   reservation.reserved_start_at,
                   customer.display_name as customer_name,
                   customer.nickname as customer_nickname,
                   customer.phone_e164 as customer_phone_e164,
                   store.display_name as store_display_name,
                   store.timezone as store_timezone,
                   store.share_display_name,
                   store.share_address,
                   store.google_map_url,
                   store.share_contact_phone,
                   store.reservation_share_note,
                   store.reservation_share_template
            from reservations reservation
            join stores store on store.tenant_id = reservation.tenant_id
             and store.id = reservation.store_id
             and store.deleted_at is null
            left join customers customer on customer.tenant_id = reservation.tenant_id
             and customer.id = reservation.customer_id
             and customer.deleted_at is null
            where reservation.tenant_id = ?
              and reservation.store_id = ?
              and reservation.id = ?
              and reservation.deleted_at is null
            """,
            (rs, rowNum) -> row(rs),
            scope.tenantId().value(),
            scope.storeId().value(),
            reservationId
        ).stream().findFirst();
    }

    private static ReservationShareInfoRow row(ResultSet rs) throws SQLException {
        return new ReservationShareInfoRow(
            rs.getObject("reservation_id", UUID.class),
            rs.getString("reservation_no"),
            rs.getInt("party_size"),
            toInstant(rs.getObject("reserved_start_at", OffsetDateTime.class)),
            rs.getString("customer_name"),
            rs.getString("customer_nickname"),
            rs.getString("customer_phone_e164"),
            rs.getString("store_display_name"),
            rs.getString("store_timezone"),
            rs.getString("share_display_name"),
            rs.getString("share_address"),
            rs.getString("google_map_url"),
            rs.getString("share_contact_phone"),
            rs.getString("reservation_share_note"),
            rs.getString("reservation_share_template")
        );
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
