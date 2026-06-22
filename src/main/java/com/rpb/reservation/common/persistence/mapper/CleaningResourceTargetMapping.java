package com.rpb.reservation.common.persistence.mapper;

import java.util.UUID;

/**
 * Mapper boundary placeholder for Cleaning table/table-group resource XOR mapping.
 */
public record CleaningResourceTargetMapping(String resourceType, UUID tableId, UUID tableGroupId) {
}
