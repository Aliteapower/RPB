package com.rpb.reservation.wallet.integration;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.customer.application.port.out.CustomerLifecycleGuard;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.wallet.persistence.WalletAccountRepository;
import org.springframework.stereotype.Component;

@Component
public class WalletCustomerLifecycleGuard implements CustomerLifecycleGuard {
    private final WalletAccountRepository accounts;
    public WalletCustomerLifecycleGuard(WalletAccountRepository accounts) {this.accounts=accounts;}
    public void beforeArchive(TenantScope scope,CustomerId customer) {
        if(accounts.retainsCustomer(scope,customer.value()))throw new CustomerManagementException(CustomerManagementError.CUSTOMER_HAS_RETAINED_ASSETS);
    }
}
