package com.rpb.reservation.publicbooking.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.publicbooking.application.PublicBookingQuotaOverride;
import com.rpb.reservation.publicbooking.application.PublicBookingSettings;
import java.time.LocalDate;

public interface PublicBookingSettingsManagementPort {

    PublicBookingSettings saveSettings(StoreScope scope, PublicBookingSettings settings);

    PublicBookingQuotaOverride saveQuotaOverride(
        StoreScope scope,
        LocalDate businessDate,
        PublicBookingQuotaOverride override
    );
}
