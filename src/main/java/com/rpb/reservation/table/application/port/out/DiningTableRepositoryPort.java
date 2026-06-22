package com.rpb.reservation.table.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.value.PartySize;
import com.rpb.reservation.table.domain.DiningTable;
import com.rpb.reservation.table.value.TableId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiningTableRepositoryPort {

    Optional<DiningTable> findById(StoreScope scope, TableId tableId);

    List<DiningTable> findActiveByArea(StoreScope scope, UUID areaId);

    List<DiningTable> findCandidates(StoreScope scope, PartySize partySize, BusinessDate businessDate);

    DiningTable save(StoreScope scope, DiningTable table);
}
