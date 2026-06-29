package com.rpb.reservation.customerauth.application.port.out;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerEmailSettings;
import com.rpb.reservation.customerauth.application.CustomerOAuthProviderSettings;

public interface CustomerAuthIntegrationManagementPort {
    CustomerEmailSettings saveEmailSettings(StoreScope scope, CustomerEmailSettings settings);

    CustomerOAuthProviderSettings saveOAuthProviderSettings(StoreScope scope, CustomerOAuthProviderSettings settings);
}
