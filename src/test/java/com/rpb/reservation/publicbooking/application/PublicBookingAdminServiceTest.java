package com.rpb.reservation.publicbooking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingSettingsManagementPort;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingSettingsRepositoryPort;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicBookingAdminServiceTest {

    private static final StoreScope SCOPE = new StoreScope(
        new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000983")),
        UUID.fromString("20000000-0000-0000-0000-000000000983")
    );
    private static final UUID RULE_ID = UUID.fromString("30000000-0000-0000-0000-000000000983");

    @Test
    void deleteAvailabilityRuleDelegatesScopedRuleId() {
        FakeManagementPort management = new FakeManagementPort();
        PublicBookingAdminService service = new PublicBookingAdminService(new FakeSettingsRepository(), management);

        service.deleteAvailabilityRule(SCOPE, RULE_ID);

        assertThat(management.deletedScope).isEqualTo(SCOPE);
        assertThat(management.deletedRuleId).isEqualTo(RULE_ID);
    }

    @Test
    void deleteAvailabilityRuleRejectsMissingId() {
        PublicBookingAdminService service = new PublicBookingAdminService(
            new FakeSettingsRepository(),
            new FakeManagementPort()
        );

        assertThatThrownBy(() -> service.deleteAvailabilityRule(SCOPE, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("availability_rule_id_required");
    }

    private static final class FakeSettingsRepository implements PublicBookingSettingsRepositoryPort {
        @Override
        public Optional<PublicBookingSettings> findSettings(StoreScope scope) {
            return Optional.empty();
        }

        @Override
        public List<PublicBookingAvailabilityRule> findAvailabilityRules(StoreScope scope) {
            return List.of();
        }

        @Override
        public Optional<PublicBookingQuotaOverride> findQuotaOverride(
            StoreScope scope,
            BusinessDate businessDate,
            String periodKey
        ) {
            return Optional.empty();
        }
    }

    private static final class FakeManagementPort implements PublicBookingSettingsManagementPort {
        StoreScope deletedScope;
        UUID deletedRuleId;

        @Override
        public PublicBookingSettings saveSettings(StoreScope scope, PublicBookingSettings settings) {
            return settings;
        }

        @Override
        public PublicBookingQuotaOverride saveQuotaOverride(
            StoreScope scope,
            LocalDate businessDate,
            PublicBookingQuotaOverride override
        ) {
            return override;
        }

        @Override
        public PublicBookingAvailabilityRule saveAvailabilityRule(
            StoreScope scope,
            PublicBookingAvailabilityRule rule
        ) {
            return rule;
        }

        @Override
        public void deleteAvailabilityRule(StoreScope scope, UUID ruleId) {
            deletedScope = scope;
            deletedRuleId = ruleId;
        }
    }
}
