package com.rpb.reservation.appgate.persistence.repository;

import com.rpb.reservation.appgate.persistence.entity.StoreAppSettingEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreAppSettingJpaRepository extends JpaRepository<StoreAppSettingEntity, UUID> {
    Optional<StoreAppSettingEntity> findByTenantIdAndStoreIdAndAppKey(UUID tenantId, UUID storeId, String appKey);

    List<StoreAppSettingEntity> findAllByTenantIdAndStoreId(UUID tenantId, UUID storeId);
}
