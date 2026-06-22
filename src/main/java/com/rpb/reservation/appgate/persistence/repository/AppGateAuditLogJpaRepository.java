package com.rpb.reservation.appgate.persistence.repository;

import com.rpb.reservation.appgate.persistence.entity.AppGateAuditLogEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppGateAuditLogJpaRepository extends JpaRepository<AppGateAuditLogEntity, UUID> {
}
