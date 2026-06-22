package com.rpb.reservation.customer.persistence.mapper;

import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.persistence.entity.CustomerEntity;

public interface CustomerMapper {

    Customer toDomain(CustomerEntity entity);

    CustomerEntity toEntity(Customer domain);
}
