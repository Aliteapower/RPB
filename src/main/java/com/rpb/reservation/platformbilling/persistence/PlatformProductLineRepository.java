package com.rpb.reservation.platformbilling.persistence;

import com.rpb.reservation.platformbilling.application.PlatformProductLine;
import com.rpb.reservation.platformbilling.application.PlatformProductLineMutationCommand;
import java.util.List;
import java.util.Optional;

public interface PlatformProductLineRepository {
    List<PlatformProductLine> findAll();

    Optional<PlatformProductLine> findByAppKey(String appKey);

    PlatformProductLine update(String appKey, PlatformProductLineMutationCommand command);
}
