package com.rpb.reservation.common.persistence.mapper;

import java.util.UUID;

/**
 * Mapper boundary placeholder for SeatingResource table/table-group resource XOR mapping.
 */
public record SeatingResourceTargetMapping(String resourceType, UUID tableId, UUID tableGroupId) {
}
