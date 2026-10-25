package com.rpb.reservation.platform.application;

import com.rpb.reservation.platform.persistence.PlatformTenantStructureRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Narrow shared-organization read boundary; consumers do not query platform-owned tables. */
@Service
public class SharedStoreIdentityQuery {
    private final PlatformTenantStructureRepository stores;
    public SharedStoreIdentityQuery(PlatformTenantStructureRepository stores) { this.stores=stores; }
    public Optional<PlatformStore> active(UUID tenant,UUID store) {
        return stores.findStore(tenant,store).filter(s -> !s.deleted() && "active".equals(s.status()));
    }
    public java.util.List<PlatformStore> activeStores(UUID tenant){return stores.listStores(tenant).stream().filter(s->!s.deleted()&&"active".equals(s.status())).toList();}
}
