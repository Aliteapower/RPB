package com.rpb.reservation.tenantadmin.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.application.TenantAdminShareProfileUpdate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantAdminShareProfileRepository {
    private final JdbcTemplate jdbc;

    public TenantAdminShareProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Row> find(StoreScope scope) {
        return jdbc.query(
            """
            select display_name as store_display_name,
                   share_display_name,
                   share_address,
                   google_map_url,
                   share_contact_phone,
                   whatsapp_business_phone_e164,
                   reservation_share_note,
                   reservation_share_template
            from stores
            where tenant_id = ?
              and id = ?
              and deleted_at is null
            """,
            (rs, rowNum) -> row(rs),
            scope.tenantId().value(),
            scope.storeId().value()
        ).stream().findFirst();
    }

    public boolean update(StoreScope scope, TenantAdminShareProfileUpdate input) {
        int updated = jdbc.update(
            """
            update stores
            set share_display_name = ?,
                google_map_url = ?,
                whatsapp_business_phone_e164 = ?,
                reservation_share_note = ?,
                reservation_share_template = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and id = ?
              and deleted_at is null
            """,
            input.shareDisplayName(),
            input.googleMapUrl(),
            input.whatsappBusinessPhoneE164(),
            input.reservationShareNote(),
            input.reservationShareTemplate(),
            scope.tenantId().value(),
            scope.storeId().value()
        );
        return updated > 0;
    }

    public boolean updateTemplate(StoreScope scope, String reservationShareTemplate) {
        int updated = jdbc.update(
            """
            update stores
            set reservation_share_template = ?,
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and id = ?
              and deleted_at is null
            """,
            reservationShareTemplate,
            scope.tenantId().value(),
            scope.storeId().value()
        );
        return updated > 0;
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(
            rs.getString("store_display_name"),
            rs.getString("share_display_name"),
            rs.getString("share_address"),
            rs.getString("google_map_url"),
            rs.getString("share_contact_phone"),
            rs.getString("whatsapp_business_phone_e164"),
            rs.getString("reservation_share_note"),
            rs.getString("reservation_share_template")
        );
    }

    public record Row(
        String storeDisplayName,
        String shareDisplayName,
        String shareAddress,
        String googleMapUrl,
        String shareContactPhone,
        String whatsappBusinessPhoneE164,
        String reservationShareNote,
        String reservationShareTemplate
    ) {
    }
}
