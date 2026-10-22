package com.rpb.reservation.wallet.integration;

import com.rpb.reservation.common.scope.*;
import com.rpb.reservation.customerauth.application.CustomerIdentityQuery;
import com.rpb.reservation.platform.application.SharedStoreIdentityQuery;
import com.rpb.reservation.tenantadmin.application.TenantAdminProfileService;
import com.rpb.reservation.queuedisplay.application.CallScreenMediaContent;
import com.rpb.reservation.wallet.application.WalletDirectory;
import com.rpb.reservation.wallet.domain.WalletException;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.rpb.reservation.wallet.application.WalletViews.*;

@Component
public class SharedWalletDirectory implements WalletDirectory {
    private final SharedStoreIdentityQuery stores;
    private final TenantAdminProfileService profiles;
    private final CustomerIdentityQuery customers;
    public SharedWalletDirectory(SharedStoreIdentityQuery stores,TenantAdminProfileService profiles,CustomerIdentityQuery customers) {
        this.stores=stores;this.profiles=profiles;this.customers=customers;
    }
    public StoreIdentity store(StoreScope scope) {
        var s=stores.active(scope.tenantId().value(),scope.storeId().value()).orElseThrow(WalletException::missing);
        var p=profiles.getProfile(scope);
        return new StoreIdentity(p.tenantId(),p.storeId(),s.operatingEntityId(),s.operatingEntityName(),p.displayName(),p.tenantCode(),s.timezone(),s.locale(),p.logoMediaAssetId(),p.contactPhone(),p.address());
    }
    public Customer customer(TenantScope scope,UUID id) {
        if(id==null) throw WalletException.invalid();
        var c=customers.authenticated(scope,id).orElseThrow(WalletException::missing);
        return new Customer(c.id(),c.customerCode(),c.displayName(),c.email(),c.phoneE164());
    }
    public List<Customer> search(TenantScope scope,String query) {
        return customers.search(scope,query).stream().map(c -> new Customer(c.id(),c.customerCode(),c.displayName(),c.email(),c.phoneE164())).toList();
    }
    public CallScreenMediaContent logo(StoreScope scope,UUID asset) {
        if(!asset.equals(profiles.getProfile(scope).logoMediaAssetId())) throw WalletException.missing();
        return profiles.readLogoMedia(scope,asset);
    }
}
