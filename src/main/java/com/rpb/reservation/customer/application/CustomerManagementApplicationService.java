package com.rpb.reservation.customer.application;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerManagementApplicationService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final CustomerRepositoryPort customerRepository;

    public CustomerManagementApplicationService(CustomerRepositoryPort customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public CustomerManagementListResult list(TenantScope scope, String keyword, Integer limit, Integer offset) {
        int safeLimit = normalizeLimit(limit);
        int safeOffset = normalizeOffset(offset);
        String normalizedKeyword = blankToNull(keyword);
        return new CustomerManagementListResult(
            customerRepository.listActive(scope, normalizedKeyword, safeLimit, safeOffset),
            safeLimit,
            safeOffset,
            customerRepository.countActive(scope, normalizedKeyword)
        );
    }

    @Transactional(readOnly = true)
    public CustomerManagementItem get(TenantScope scope, UUID customerId) {
        return customerRepository.findManagementItem(scope, new CustomerId(Objects.requireNonNull(customerId)))
            .orElseThrow(() -> new CustomerManagementException(CustomerManagementError.CUSTOMER_NOT_FOUND));
    }

    @Transactional
    public CustomerManagementItem create(TenantScope scope, CustomerManagementCommand command) {
        NormalizedCustomerInput input = normalize(command);
        ensureHasIdentityHint(input);
        E164Phone phone = parsePhone(input.phoneE164());
        if (phone.isPresent() && customerRepository.findByPhone(scope, phone).isPresent()) {
            throw new CustomerManagementException(CustomerManagementError.CUSTOMER_PHONE_CONFLICT);
        }
        Customer saved = customerRepository.save(
            scope,
            new Customer(
                new CustomerId(UUID.randomUUID()),
                scope,
                "C-" + UUID.randomUUID().toString().substring(0, 12),
                "regular",
                phone,
                "active",
                input.displayName(),
                input.nickname(),
                input.email()
            )
        );
        return customerRepository.findManagementItem(scope, saved.id()).orElseGet(() -> itemFrom(saved));
    }

    @Transactional
    public CustomerManagementItem update(TenantScope scope, UUID customerId, CustomerManagementCommand command) {
        Customer existing = customerRepository.findById(scope, new CustomerId(Objects.requireNonNull(customerId)))
            .orElseThrow(() -> new CustomerManagementException(CustomerManagementError.CUSTOMER_NOT_FOUND));
        NormalizedCustomerInput input = normalize(command);
        ensureHasIdentityHint(input);
        E164Phone phone = parsePhone(input.phoneE164());
        if (phone.isPresent()) {
            customerRepository.findByPhone(scope, phone)
                .filter(customer -> !customer.id().equals(existing.id()))
                .ifPresent(customer -> {
                    throw new CustomerManagementException(CustomerManagementError.CUSTOMER_PHONE_CONFLICT);
                });
        }
        Customer updated = new Customer(
            existing.id(),
            existing.scope(),
            existing.customerCode(),
            existing.customerType(),
            phone,
            existing.status(),
            input.displayName(),
            input.nickname(),
            input.email()
        );
        Customer saved = customerRepository.save(scope, updated);
        return customerRepository.findManagementItem(scope, saved.id()).orElseGet(() -> itemFrom(saved));
    }

    @Transactional
    public void archive(TenantScope scope, UUID customerId) {
        CustomerId id = new CustomerId(Objects.requireNonNull(customerId));
        if (customerRepository.findManagementItem(scope, id).isEmpty()) {
            throw new CustomerManagementException(CustomerManagementError.CUSTOMER_NOT_FOUND);
        }
        customerRepository.archive(scope, id);
    }

    private static NormalizedCustomerInput normalize(CustomerManagementCommand command) {
        if (command == null) {
            throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);
        }
        String email = blankToNull(command.email());
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);
        }
        return new NormalizedCustomerInput(
            blankToNull(command.displayName()),
            blankToNull(command.nickname()),
            blankToNull(command.phoneE164()),
            email
        );
    }

    private static void ensureHasIdentityHint(NormalizedCustomerInput input) {
        if (input.displayName() == null && input.phoneE164() == null && input.email() == null) {
            throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);
        }
    }

    private static E164Phone parsePhone(String phoneE164) {
        try {
            return phoneE164 == null ? E164Phone.empty() : new E164Phone(phoneE164);
        } catch (IllegalArgumentException exception) {
            throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);
        }
    }

    private static CustomerManagementItem itemFrom(Customer customer) {
        return new CustomerManagementItem(
            customer.id().value(),
            customer.customerCode(),
            customer.displayName(),
            customer.nickname(),
            customer.phone().value(),
            customer.email(),
            customer.status(),
            null,
            null
        );
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);
        }
        return limit;
    }

    private static int normalizeOffset(Integer offset) {
        if (offset == null) {
            return 0;
        }
        if (offset < 0) {
            throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);
        }
        return offset;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record NormalizedCustomerInput(
        String displayName,
        String nickname,
        String phoneE164,
        String email
    ) {
    }
}
