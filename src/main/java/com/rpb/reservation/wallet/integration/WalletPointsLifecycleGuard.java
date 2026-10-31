package com.rpb.reservation.wallet.integration;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.customer.application.port.out.CustomerLifecycleGuard;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.loyalty.application.PointsService;
import org.springframework.stereotype.Component;
/** Customer owns lifecycle; loyalty only reports retained rights through its application boundary. */
@Component
public class WalletPointsLifecycleGuard implements CustomerLifecycleGuard {
 private final PointsService points;
 public WalletPointsLifecycleGuard(PointsService points){this.points=points;}
 public void beforeArchive(TenantScope scope,CustomerId customer){if(points.retainsCustomer(scope,customer.value()))throw new CustomerManagementException(CustomerManagementError.CUSTOMER_HAS_RETAINED_ASSETS);}
}
