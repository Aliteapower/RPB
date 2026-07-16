package com.rpb.reservation.customer.persistence.adapter;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.CustomerManagementItem;
import com.rpb.reservation.customer.application.CustomerPhoneLookupCustomer;
import com.rpb.reservation.customer.application.port.out.CustomerLookupReadPort;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.persistence.entity.CustomerEntity;
import com.rpb.reservation.customer.persistence.mapper.CustomerMapper;
import com.rpb.reservation.customer.persistence.repository.CustomerJpaRepository;
import com.rpb.reservation.customer.value.CustomerId;
import jakarta.persistence.EntityManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

@Repository
public class CustomerPersistenceAdapter implements CustomerRepositoryPort, CustomerLookupReadPort {

    private final CustomerJpaRepository repository;
    private final CustomerMapper mapper;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    public CustomerPersistenceAdapter(
        CustomerJpaRepository repository,
        CustomerMapper mapper,
        EntityManager entityManager,
        JdbcTemplate jdbc
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
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
            CustomerEntity saved = repository.save(mergeExisting(existing.get(), entity));
            entityManager.flush();
            return mapper.toDomain(saved);
        }
        CustomerEntity newEntity = newEntity(entity);
        entityManager.persist(newEntity);
        entityManager.flush();
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
            updated.getEmail(),
            existing.getLookupNote(),
            existing.getStatus(),
            existing.getMergedIntoCustomerId(),
            existing.getCreatedAt(),
            java.time.OffsetDateTime.now(),
            existing.getDeletedAt(),
            existing.getVersion()
        );
    }

    @Override
    public List<CustomerManagementItem> listActive(TenantScope scope, String keyword, int limit, int offset) {
        QueryParts query = activeWhereClause(scope, keyword);
        List<Object> args = new java.util.ArrayList<>(query.args());
        args.add(limit);
        args.add(offset);
        return jdbc.query(
            """
            select id, customer_code, display_name, nickname, phone_e164, email, status, created_at, updated_at
            from customers
            %s
            order by updated_at desc, customer_code
            limit ? offset ?
            """.formatted(query.sql()),
            CustomerPersistenceAdapter::managementItem,
            args.toArray()
        );
    }

    @Override
    public int countActive(TenantScope scope, String keyword) {
        QueryParts query = activeWhereClause(scope, keyword);
        Integer total = jdbc.queryForObject(
            """
            select count(*)
            from customers
            %s
            """.formatted(query.sql()),
            Integer.class,
            query.args().toArray()
        );
        return total == null ? 0 : total;
    }

    @Override
    public Optional<CustomerManagementItem> findManagementItem(TenantScope scope, CustomerId customerId) {
        return jdbc.query(
            """
            select id, customer_code, display_name, nickname, phone_e164, email, status, created_at, updated_at
            from customers
            where tenant_id = ?
              and id = ?
              and status = 'active'
              and deleted_at is null
            """,
            CustomerPersistenceAdapter::managementItem,
            scope.tenantId().value(),
            customerId.value()
        ).stream().findFirst();
    }

    @Override
    public void archive(TenantScope scope, CustomerId customerId) {
        jdbc.update(
            """
            update customers
            set status = 'archived',
                deleted_at = now(),
                updated_at = now(),
                version = version + 1
            where tenant_id = ?
              and id = ?
              and status = 'active'
              and deleted_at is null
            """,
            scope.tenantId().value(),
            customerId.value()
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

    private static QueryParts activeWhereClause(TenantScope scope, String keyword) {
        StringBuilder sql = new StringBuilder("""
            where tenant_id = ?
              and status = 'active'
              and deleted_at is null
            """);
        List<Object> args = new java.util.ArrayList<>();
        args.add(scope.tenantId().value());
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            sql.append("""
                  and (
                    lower(customer_code) like ?
                    or lower(coalesce(display_name, '')) like ?
                    or lower(coalesce(nickname, '')) like ?
                    or lower(coalesce(phone_e164, '')) like ?
                    or lower(coalesce(email, '')) like ?
                  )
                """);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        return new QueryParts(sql.toString(), args);
    }

    private static CustomerManagementItem managementItem(ResultSet rs, int rowNum) throws SQLException {
        return new CustomerManagementItem(
            rs.getObject("id", java.util.UUID.class),
            rs.getString("customer_code"),
            rs.getString("display_name"),
            rs.getString("nickname"),
            rs.getString("phone_e164"),
            rs.getString("email"),
            rs.getString("status"),
            rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }

    private record QueryParts(String sql, List<Object> args) {
    }
}
