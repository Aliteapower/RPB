package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.publicbooking.application.PublicBookingAdminService;
import com.rpb.reservation.tenantadmin.persistence.TenantEntryLinksRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.tenantadmin.application.TenantEntryLinksContext.*;

@Service
@Transactional(readOnly=true)
public class TenantEntryLinksService {
    private final TenantEntryLinksRepository repository;
    private final AppGateService gate;
    private final PublicBookingAdminService booking;
    public TenantEntryLinksService(TenantEntryLinksRepository repository,AppGateService gate,PublicBookingAdminService booking){this.repository=repository;this.gate=gate;this.booking=booking;}
    public TenantEntryLinksContext get(StoreScope scope) {
        var identity=repository.find(scope).orElseThrow(()->new TenantAdminServiceException(TenantAdminServiceErrorCode.TENANT_PROFILE_NOT_FOUND));
        String inactive=!"active".equals(identity.tenantStatus())?"TENANT_INACTIVE":!"active".equals(identity.storeStatus())?"STORE_INACTIVE":null;
        var products=List.of("reservation_queue","wallet","payment","daily_ledger").stream().map(key->{
            if(inactive!=null)return new Product(key,false,inactive);
            var decision=gate.evaluateCommercial(key,scope.tenantId().value(),scope.storeId().value());
            return new Product(key,decision.allowed(),decision.allowed()?null:decision.denyReason().name());
        }).toList();
        boolean bookingEnabled=products.stream().anyMatch(p->p.appKey().equals("reservation_queue")&&p.available())&&booking.getSettings(scope).enabled();
        return new TenantEntryLinksContext(true,identity,bookingEnabled,products);
    }
}
