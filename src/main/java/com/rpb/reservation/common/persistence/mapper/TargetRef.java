package com.rpb.reservation.common.persistence.mapper;

import java.util.UUID;

/**
 * Mapper boundary placeholder for generic target_type/target_id persistence fields.
 */
public record TargetRef(String targetType, UUID targetId) {
}
