package com.rpb.reservation.customerauth.application;

/** Read-only account eligibility; never contains credentials or activation secrets. */
public enum CustomerActivationState {
    required, verified_pending, activated, email_verified, unavailable, unknown
}
