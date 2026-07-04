package com.rpb.reservation.customerauth.application;

public record CustomerOAuthVerifiedIdentity(
    String providerSubject,
    String email,
    String displayName
) {
}
