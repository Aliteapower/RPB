package com.rpb.reservation.appgate.persistence.repository;

import com.rpb.reservation.appgate.persistence.entity.PlatformAppEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAppJpaRepository extends JpaRepository<PlatformAppEntity, UUID> {
    Optional<PlatformAppEntity> findByAppKey(String appKey);

    List<PlatformAppEntity> findAllByStatusOrderBySortOrderAscAppKeyAsc(String status);
}
