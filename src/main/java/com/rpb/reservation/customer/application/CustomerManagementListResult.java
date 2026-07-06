package com.rpb.reservation.customer.application;

import java.util.List;

public record CustomerManagementListResult(
    List<CustomerManagementItem> customers,
    int limit,
    int offset,
    int total
) {
}
