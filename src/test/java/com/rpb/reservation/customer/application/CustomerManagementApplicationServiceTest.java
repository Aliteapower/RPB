package com.rpb.reservation.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerManagementApplicationServiceTest {

    private static final TenantScope SCOPE = new TenantScope(
        new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000983"))
    );

    @Test
    void createsCustomerWithoutPhoneWhenNameIsPresent() {
        FakeCustomerRepository repository = new FakeCustomerRepository();
        CustomerManagementApplicationService service = new CustomerManagementApplicationService(repository);

        CustomerManagementItem item = service.create(
            SCOPE,
            new CustomerManagementCommand("王小明", "先生", null, null)
        );

        assertThat(item.displayName()).isEqualTo("王小明");
        assertThat(item.nickname()).isEqualTo("先生");
        assertThat(item.phoneE164()).isNull();
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void rejectsDuplicateActivePhoneInTenant() {
        FakeCustomerRepository repository = new FakeCustomerRepository();
        repository.existingByPhone = Optional.of(activeCustomer("+6591234567"));
        CustomerManagementApplicationService service = new CustomerManagementApplicationService(repository);

        assertThatThrownBy(() -> service.create(
            SCOPE,
            new CustomerManagementCommand("王小明", "先生", "+6591234567", null)
        ))
            .isInstanceOf(CustomerManagementException.class)
            .extracting("code")
            .isEqualTo(CustomerManagementError.CUSTOMER_PHONE_CONFLICT);
    }

    @Test
    void profileResolverRefreshesEmailOnExistingPhoneCustomer() {
        FakeCustomerRepository repository = new FakeCustomerRepository();
        repository.existingByPhone = Optional.of(activeCustomer("+6591234567"));
        CustomerProfileResolver resolver = new CustomerProfileResolver(repository);

        Customer customer = resolver.resolve(
            SCOPE,
            new CustomerProfileCommand(null, "王小明", "先生", "+6591234567", "guest@example.com")
        );

        assertThat(customer.email()).isEqualTo("guest@example.com");
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void profileResolverRejectsInvalidEmail() {
        FakeCustomerRepository repository = new FakeCustomerRepository();
        CustomerProfileResolver resolver = new CustomerProfileResolver(repository);

        assertThatThrownBy(() -> resolver.resolve(
            SCOPE,
            new CustomerProfileCommand(null, "王小明", "先生", "+6591234567", "invalid-email")
        ))
            .isInstanceOf(CustomerManagementException.class)
            .extracting("code")
            .isEqualTo(CustomerManagementError.REQUEST_INVALID);
    }

    @Test
    void updateRejectsClearingAllCustomerIdentityHints() {
        FakeCustomerRepository repository = new FakeCustomerRepository();
        Customer customer = activeCustomer("+6591234567");
        repository.customers.add(customer);
        CustomerManagementApplicationService service = new CustomerManagementApplicationService(repository);

        assertThatThrownBy(() -> service.update(
            SCOPE,
            customer.id().value(),
            new CustomerManagementCommand(null, null, null, null)
        ))
            .isInstanceOf(CustomerManagementException.class)
            .extracting("code")
            .isEqualTo(CustomerManagementError.REQUEST_INVALID);
    }

    @Test
    void archiveRemovesCustomerFromActiveList() {
        FakeCustomerRepository repository = new FakeCustomerRepository();
        Customer customer = activeCustomer("+6591234567");
        repository.customers.add(customer);
        CustomerManagementApplicationService service = new CustomerManagementApplicationService(repository);

        service.archive(SCOPE, customer.id().value());

        assertThat(repository.archived).containsExactly(customer.id());
    }

    private static Customer activeCustomer(String phoneE164) {
        return new Customer(
            new CustomerId(UUID.randomUUID()),
            SCOPE,
            "C-20260706-0001",
            "regular",
            new E164Phone(phoneE164),
            "active",
            "旧顾客",
            "女士",
            null
        );
    }

    private static final class FakeCustomerRepository implements CustomerRepositoryPort {
        Optional<Customer> existingByPhone = Optional.empty();
        final List<Customer> customers = new ArrayList<>();
        final List<Customer> saved = new ArrayList<>();
        final List<CustomerId> archived = new ArrayList<>();

        @Override
        public Optional<Customer> findById(TenantScope scope, CustomerId customerId) {
            return customers.stream()
                .filter(customer -> customer.scope().equals(scope))
                .filter(customer -> customer.id().equals(customerId))
                .findFirst();
        }

        @Override
        public Optional<Customer> findByCode(TenantScope scope, String customerCode) {
            return customers.stream()
                .filter(customer -> customer.scope().equals(scope))
                .filter(customer -> customer.customerCode().equals(customerCode))
                .findFirst();
        }

        @Override
        public Optional<Customer> findByPhone(TenantScope scope, E164Phone phone) {
            return existingByPhone
                .filter(customer -> customer.scope().equals(scope))
                .filter(customer -> customer.phone().equals(phone));
        }

        @Override
        public List<Customer> searchNoPhoneCandidates(TenantScope scope, String lookupText) {
            return List.of();
        }

        @Override
        public Customer save(TenantScope scope, Customer customer) {
            saved.add(customer);
            customers.add(customer);
            return customer;
        }

        @Override
        public List<CustomerManagementItem> listActive(TenantScope scope, String keyword, int limit, int offset) {
            return customers.stream()
                .filter(customer -> customer.scope().equals(scope))
                .map(FakeCustomerRepository::toItem)
                .toList();
        }

        @Override
        public int countActive(TenantScope scope, String keyword) {
            return listActive(scope, keyword, 100, 0).size();
        }

        @Override
        public Optional<CustomerManagementItem> findManagementItem(TenantScope scope, CustomerId customerId) {
            return findById(scope, customerId).map(FakeCustomerRepository::toItem);
        }

        @Override
        public void archive(TenantScope scope, CustomerId customerId) {
            archived.add(customerId);
        }

        private static CustomerManagementItem toItem(Customer customer) {
            OffsetDateTime now = OffsetDateTime.parse("2026-07-06T00:00:00Z");
            return new CustomerManagementItem(
                customer.id().value(),
                customer.customerCode(),
                customer.displayName(),
                customer.nickname(),
                customer.phone().value(),
                customer.email(),
                customer.status(),
                now,
                now
            );
        }
    }
}
