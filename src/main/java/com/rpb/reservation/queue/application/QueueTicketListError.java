package com.rpb.reservation.queue.application;

public enum QueueTicketListError {
    INVALID_QUERY,
    INVALID_STATUS,
    INVALID_LIMIT,
    INVALID_OFFSET,
    STORE_NOT_FOUND,
    STORE_SCOPE_MISMATCH,
    STORE_ACCESS_DENIED,
    PERSISTENCE_ERROR
}
