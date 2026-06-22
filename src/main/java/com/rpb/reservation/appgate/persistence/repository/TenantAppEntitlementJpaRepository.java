package com.rpb.reservation.appgate.persistence.repository;

import com.rpb.reservation.appgate.persistence.entity.TenantAppEntitlementEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantAppEntitlementJpaRepository extends JpaRepository<TenantAppEntitlementEntity, UUID> {
    Optional<TenantAppEntitlementEntity> findByTenantIdAndAppKey(UUID tenantId, String appKey);

    List<TenantAppEntitlementEntity> findAllByTenantId(UUID tenantId);
}
