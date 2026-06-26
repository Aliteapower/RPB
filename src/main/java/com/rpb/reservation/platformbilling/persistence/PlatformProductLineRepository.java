package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.PlatformProductLine;
import com.rpb.reservation.platformbilling.application.PlatformProductLineCreateCommand;
import com.rpb.reservation.platformbilling.application.PlatformProductLineMutationCommand;
import com.rpb.reservation.platformbilling.application.PlatformProductLinePage;
import com.rpb.reservation.platformbilling.application.PlatformProductLineQuery;
import java.util.List;
import java.util.Optional;

public interface PlatformProductLineRepository {
    List<PlatformProductLine> findAll();

    PlatformProductLinePage search(PlatformProductLineQuery query);

    Optional<PlatformProductLine> findByAppKey(String appKey);

    PlatformProductLine create(PlatformProductLineCreateCommand command);

    PlatformProductLine update(String appKey, PlatformProductLineMutationCommand command);
}
