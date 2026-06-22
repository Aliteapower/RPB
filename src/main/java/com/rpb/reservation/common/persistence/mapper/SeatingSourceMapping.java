package com.rpb.reservation.common.persistence.mapper;

import java.util.UUID;

/**
 * Mapper boundary placeholder for Seating reservation/queue/walk-in source XOR mapping.
 */
public record SeatingSourceMapping(String sourceType, UUID sourceId) {
}
