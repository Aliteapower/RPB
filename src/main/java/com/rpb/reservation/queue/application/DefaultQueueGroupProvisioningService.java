package com.rpb.reservation.queue.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.queue.application.port.out.DefaultQueueGroupProvisioningPort;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DefaultQueueGroupProvisioningService {

    private final DefaultQueueGroupProvisioningPort provisioningPort;

    public DefaultQueueGroupProvisioningService(DefaultQueueGroupProvisioningPort provisioningPort) {
        this.provisioningPort = provisioningPort;
    }

    public void provisionDefaults(StoreScope scope) {
        provisioningPort.provisionDefaults(Objects.requireNonNull(scope, "store_scope_required"));
    }
}
