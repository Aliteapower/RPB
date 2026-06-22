package com.rpb.reservation.common.value;

/**
 * Usable capacity range for DiningTable and TableGroup resources.
 */
public record CapacityRange(int min, int max) {

    public CapacityRange {
        if (min <= 0) {
            throw new IllegalArgumentException("capacity_min_must_be_positive");
        }
        if (max < min) {
            throw new IllegalArgumentException("capacity_max_must_be_greater_than_or_equal_to_min");
        }
    }

    public boolean includes(PartySize partySize) {
        int size = partySize.value();
        return size >= min && size <= max;
    }
}
