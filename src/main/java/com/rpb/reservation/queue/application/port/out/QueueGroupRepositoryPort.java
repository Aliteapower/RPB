package com.rpb.reservation.queue.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.queue.domain.QueueGroup;
import java.util.Optional;

public interface QueueGroupRepositoryPort {

    Optional<QueueGroup> findActiveByCode(StoreScope scope, String groupCode);

    Optional<QueueGroup> findActiveByPartySize(StoreScope scope, PartySize partySize);
}
