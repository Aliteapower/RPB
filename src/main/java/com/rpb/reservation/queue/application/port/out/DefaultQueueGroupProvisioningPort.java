package com.rpb.reservation.queue.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;

public interface DefaultQueueGroupProvisioningPort {

    void provisionDefaults(StoreScope scope);
}
