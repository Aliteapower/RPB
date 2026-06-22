package com.rpb.reservation.customer.persistence.repository;

import com.rpb.reservation.customer.persistence.entity.CustomerEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {

    Optional<CustomerEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<CustomerEntity> findByTenantIdAndCustomerCodeAndDeletedAtIsNull(UUID tenantId, String customerCode);

    Optional<CustomerEntity> findByTenantIdAndPhoneE164AndDeletedAtIsNull(UUID tenantId, String phoneE164);

    @Query("""
        select customer from CustomerEntity customer
        where customer.tenantId = :tenantId
          and customer.deletedAt is null
          and customer.phoneE164 is null
          and (
              lower(customer.customerCode) like lower(concat('%', :lookupText, '%'))
              or lower(coalesce(customer.displayName, '')) like lower(concat('%', :lookupText, '%'))
              or lower(coalesce(customer.nickname, '')) like lower(concat('%', :lookupText, '%'))
              or lower(coalesce(customer.lookupNote, '')) like lower(concat('%', :lookupText, '%'))
          )
        order by customer.updatedAt desc
        """)
    List<CustomerEntity> searchNoPhoneCandidates(@Param("tenantId") UUID tenantId, @Param("lookupText") String lookupText);
}
