package com.rpb.reservation.payment.persistence;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.payment.application.PaymentMethodProfile;
import com.rpb.reservation.payment.application.PaymentMethodProfileCommand;
import com.rpb.reservation.payment.application.PaymentServiceErrorCode;
import com.rpb.reservation.payment.application.PaymentServiceException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaymentMethodProfileRepository implements PaymentMethodProfileRepository {
    private final JdbcTemplate jdbc;

    public JdbcPaymentMethodProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PaymentMethodProfile upsertStoreProfile(StoreScope scope, PaymentMethodProfileCommand command) {
        return jdbc.query(
            """
            insert into payment_method_profiles (
                tenant_id,
                store_id,
                method,
                status,
                paynow_type,
                paynow_mobile,
                paynow_uen,
                merchant_name,
                currency,
                config_json,
                version
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 0)
            on conflict (tenant_id, store_id, method) where store_id is not null do update
            set
                status = excluded.status,
                paynow_type = excluded.paynow_type,
                paynow_mobile = excluded.paynow_mobile,
                paynow_uen = excluded.paynow_uen,
                merchant_name = excluded.merchant_name,
                currency = excluded.currency,
                config_json = excluded.config_json,
                updated_at = now(),
                version = payment_method_profiles.version + 1
            where payment_method_profiles.version = ?
            returning id, tenant_id, store_id, method, status, paynow_type, paynow_mobile, paynow_uen,
                      merchant_name, currency, config_json::text as config_json, version, created_at, updated_at
            """,
            JdbcPaymentMethodProfileRepository::mapProfile,
            scope.tenantId().value(),
            scope.storeId().value(),
            command.method(),
            command.status(),
            command.paynowType(),
            command.paynowMobile(),
            command.paynowUen(),
            command.merchantName(),
            command.currency(),
            command.configJson(),
            command.version()
        ).stream().findFirst().orElseThrow(() -> new PaymentServiceException(PaymentServiceErrorCode.VERSION_CONFLICT));
    }

    @Override
    public Optional<PaymentMethodProfile> findStoreProfile(StoreScope scope, String method) {
        return jdbc.query(
            """
            select id, tenant_id, store_id, method, status, paynow_type, paynow_mobile, paynow_uen,
                   merchant_name, currency, config_json::text as config_json, version, created_at, updated_at
            from payment_method_profiles
            where tenant_id = ?
              and store_id = ?
              and method = ?
            """,
            JdbcPaymentMethodProfileRepository::mapProfile,
            scope.tenantId().value(),
            scope.storeId().value(),
            method
        ).stream().findFirst();
    }

    @Override
    public Optional<PaymentMethodProfile> findTenantDefaultProfile(TenantScope scope, String method) {
        return jdbc.query(
            """
            select id, tenant_id, store_id, method, status, paynow_type, paynow_mobile, paynow_uen,
                   merchant_name, currency, config_json::text as config_json, version, created_at, updated_at
            from payment_method_profiles
            where tenant_id = ?
              and store_id is null
              and method = ?
            """,
            JdbcPaymentMethodProfileRepository::mapProfile,
            scope.tenantId().value(),
            method
        ).stream().findFirst();
    }

    private static PaymentMethodProfile mapProfile(ResultSet resultSet, int rowNum) throws SQLException {
        return new PaymentMethodProfile(
            resultSet.getObject("id", java.util.UUID.class),
            resultSet.getObject("tenant_id", java.util.UUID.class),
            resultSet.getObject("store_id", java.util.UUID.class),
            resultSet.getString("method"),
            resultSet.getString("status"),
            resultSet.getString("paynow_type"),
            resultSet.getString("paynow_mobile"),
            resultSet.getString("paynow_uen"),
            resultSet.getString("merchant_name"),
            resultSet.getString("currency"),
            resultSet.getString("config_json"),
            resultSet.getInt("version"),
            resultSet.getObject("created_at", java.time.OffsetDateTime.class),
            resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
