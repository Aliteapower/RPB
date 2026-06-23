package com.rpb.reservation.customer.persistence.adapter;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.CustomerPhoneLookupCustomer;
import com.rpb.reservation.customer.application.port.out.CustomerLookupReadPort;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.persistence.entity.CustomerEntity;
import com.rpb.reservation.customer.persistence.mapper.CustomerMapper;
import com.rpb.reservation.customer.persistence.repository.CustomerJpaRepository;
import com.rpb.reservation.customer.value.CustomerId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerPersistenceAdapter implements CustomerRepositoryPort, CustomerLookupReadPort {

    private final CustomerJpaRepository repository;
    private final CustomerMapper mapper;
    private final EntityManager entityManager;

    public CustomerPersistenceAdapter(CustomerJpaRepository repository, CustomerMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Customer> findById(TenantScope scope, CustomerId customerId) {
        return repository.findByIdAndTenantIdAndDeletedAtIsNull(
            customerId.value(),
            scope.tenantId().value()
        ).map(mapper::toDomain);
    }

    @Override
    public Optional<Customer> findByCode(TenantScope scope, String customerCode) {
        return repository.findByTenantIdAndCustomerCodeAndDeletedAtIsNull(
            scope.tenantId().value(),
            customerCode
        ).map(mapper::toDomain);
    }

    @Override
    public Optional<Customer> findByPhone(TenantScope scope, E164Phone phone) {
        if (phone == null || !phone.isPresent()) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndPhoneE164AndDeletedAtIsNull(
            scope.tenantId().value(),
            phone.value()
        ).map(mapper::toDomain);
    }

    @Override
    public Optional<CustomerPhoneLookupCustomer> findActiveByPhone(TenantScope scope, E164Phone phone) {
        if (phone == null || !phone.isPresent()) {
            return Optional.empty();
        }
        return repository.findByTenantIdAndPhoneE164AndDeletedAtIsNull(
            scope.tenantId().value(),
            phone.value()
        ).map(CustomerPersistenceAdapter::toLookupCustomer);
    }

    @Override
    public List<Customer> searchNoPhoneCandidates(TenantScope scope, String lookupText) {
        return repository.searchNoPhoneCandidates(scope.tenantId().value(), lookupText)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Customer save(TenantScope scope, Customer customer) {
        CustomerEntity entity = mapper.toEntity(customer);
        Optional<CustomerEntity> existing = repository.findByIdAndTenantIdAndDeletedAtIsNull(
            customer.id().value(),
            scope.tenantId().value()
        );
        if (existing.isPresent()) {
            return mapper.toDomain(repository.save(mergeExisting(existing.get(), entity)));
        }
        CustomerEntity newEntity = newEntity(entity);
        entityManager.persist(newEntity);
        return mapper.toDomain(newEntity);
    }

    private static CustomerEntity mergeExisting(CustomerEntity existing, CustomerEntity updated) {
        return CustomerEntity.of(
            existing.getId(),
            existing.getTenantId(),
            existing.getCustomerCode(),
            existing.getCustomerType(),
            updated.getDisplayName(),
            updated.getNickname(),
            updated.getPhoneE164(),
            existing.getEmail(),
            existing.getLookupNote(),
            existing.getStatus(),
            existing.getMergedIntoCustomerId(),
            existing.getCreatedAt(),
            existing.getUpdatedAt(),
            existing.getDeletedAt(),
            existing.getVersion()
        );
    }

    private static CustomerEntity newEntity(CustomerEntity entity) {
        return CustomerEntity.of(
            entity.getId(),
            entity.getTenantId(),
            entity.getCustomerCode(),
            entity.getCustomerType(),
            entity.getDisplayName(),
            entity.getNickname(),
            entity.getPhoneE164(),
            entity.getEmail(),
            entity.getLookupNote(),
            entity.getStatus(),
            entity.getMergedIntoCustomerId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt(),
            null
        );
    }

    private static CustomerPhoneLookupCustomer toLookupCustomer(CustomerEntity entity) {
        return new CustomerPhoneLookupCustomer(
            entity.getId(),
            entity.getDisplayName(),
            entity.getNickname(),
            entity.getPhoneE164()
        );
    }
}
