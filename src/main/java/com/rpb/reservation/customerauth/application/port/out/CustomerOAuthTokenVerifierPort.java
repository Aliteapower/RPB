package com.rpb.reservation.customerauth.application.port.out;

import com.rpb.reservation.customerauth.application.CustomerOAuthProviderSettings;
import com.rpb.reservation.customerauth.application.CustomerOAuthVerificationRequest;
import com.rpb.reservation.customerauth.application.CustomerOAuthVerifiedIdentity;

public interface CustomerOAuthTokenVerifierPort {
    CustomerOAuthVerifiedIdentity verify(
        CustomerOAuthVerificationRequest request,
        CustomerOAuthProviderSettings settings
    );
}
