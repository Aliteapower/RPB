package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.PlatformProductLinePrice;
import com.rpb.reservation.platformbilling.application.PlatformProductLinePriceUpdate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlatformProductLinePriceRepository {
    List<PlatformProductLinePrice> findByAppKeys(Collection<String> appKeys);

    List<PlatformProductLinePrice> replacePrices(String appKey, List<PlatformProductLinePriceUpdate> prices);

    Optional<PlatformProductLinePrice> findActivePrice(String appKey, String billingCycle);
}
