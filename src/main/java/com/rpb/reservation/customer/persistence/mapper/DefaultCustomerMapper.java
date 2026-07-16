package com.rpb.reservation.customer.persistence.mapper;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.persistence.entity.CustomerEntity;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class DefaultCustomerMapper implements CustomerMapper {

    @Override
    public Customer toDomain(CustomerEntity entity) {
        return new Customer(
            new CustomerId(entity.getId()),
            new TenantScope(new TenantId(entity.getTenantId())),
            entity.getCustomerCode(),
            entity.getCustomerType(),
            new E164Phone(entity.getPhoneE164()),
            entity.getStatus(),
            entity.getDisplayName(),
            entity.getNickname(),
            entity.getEmail()
        );
    }

    @Override
    public CustomerEntity toEntity(Customer domain) {
        OffsetDateTime now = OffsetDateTime.now();
        return CustomerEntity.of(
            domain.id().value(),
            domain.scope().tenantId().value(),
            domain.customerCode(),
            domain.customerType(),
            displayName(domain),
            domain.nickname(),
            domain.phone().value(),
            domain.email(),
            null,
            domain.status(),
            null,
            now,
            now,
            null,
            0
        );
    }

    private static String displayName(Customer domain) {
        return domain.displayName() == null ? domain.customerCode() : domain.displayName();
    }
}
