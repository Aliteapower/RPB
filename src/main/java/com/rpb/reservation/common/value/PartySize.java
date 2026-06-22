package com.rpb.reservation.common.value;

/**
 * Total guest count value. Keeps party-size validation reusable and away from
 * controllers, repositories, and persistence mapping.
 */
public record PartySize(int value) {

    public PartySize {
        if (value <= 0) {
            throw new IllegalArgumentException("party_size_must_be_positive");
        }
    }
}
