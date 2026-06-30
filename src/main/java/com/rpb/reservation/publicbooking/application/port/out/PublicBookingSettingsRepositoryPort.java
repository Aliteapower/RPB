package com.rpb.reservation.publicbooking.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.publicbooking.application.PublicBookingAvailabilityRule;
import com.rpb.reservation.publicbooking.application.PublicBookingQuotaOverride;
import com.rpb.reservation.publicbooking.application.PublicBookingSettings;
import java.util.List;
import java.util.Optional;

public interface PublicBookingSettingsRepositoryPort {

    Optional<PublicBookingSettings> findSettings(StoreScope scope);

    List<PublicBookingAvailabilityRule> findAvailabilityRules(StoreScope scope);

    Optional<PublicBookingQuotaOverride> findQuotaOverride(StoreScope scope, BusinessDate businessDate, String periodKey);
}
