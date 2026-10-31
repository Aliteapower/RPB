package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import java.util.*;
import static com.rpb.reservation.wallet.application.WalletViews.*;

public interface WalletDirectory {
    StoreIdentity store(StoreScope scope);
    Customer customer(TenantScope scope, UUID customer);
    List<Customer> search(TenantScope scope,String query);
    CallScreenMediaContent logo(StoreScope scope,UUID asset);
}
