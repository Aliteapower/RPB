package com.rpb.reservation.customer.application;

import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CustomerProfileResolver {
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final CustomerRepositoryPort customerRepository;

    public CustomerProfileResolver(CustomerRepositoryPort customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer resolve(TenantScope scope, CustomerProfileCommand command) {
        if (command == null) {
            return createCustomer(scope, null, null, E164Phone.empty(), null);
        }
        E164Phone phone = parsePhone(command.phoneE164());
        String email = normalizeEmail(command.email());
        Customer customer = findExisting(scope, command.customerId(), phone)
            .orElseGet(() -> createCustomer(
                scope,
                command.displayName(),
                command.nickname(),
                phone,
                email
            ));
        Customer refreshed = customer.refreshProfile(
            phone,
            command.displayName(),
            command.nickname(),
            email
        );
        return refreshed.equals(customer) ? customer : customerRepository.save(scope, refreshed);
    }

    private Optional<Customer> findExisting(TenantScope scope, UUID customerId, E164Phone phone) {
        if (customerId != null) {
            return Optional.of(customerRepository.findById(scope, new CustomerId(customerId))
                .orElseThrow(() -> new CustomerManagementException(CustomerManagementError.CUSTOMER_NOT_FOUND)));
        }
        if (phone != null && phone.isPresent()) {
            return customerRepository.findByPhone(scope, phone);
        }
        return Optional.empty();
    }

    private Customer createCustomer(
        TenantScope scope,
        String displayName,
        String nickname,
        E164Phone phone,
        String email
    ) {
        return customerRepository.save(
            scope,
            new Customer(
                new CustomerId(UUID.randomUUID()),
                scope,
                nextCustomerCode(),
                "regular",
                phone == null ? E164Phone.empty() : phone,
                "active",
                blankToNull(displayName),
                blankToNull(nickname),
                blankToNull(email)
            )
        );
    }

    private static E164Phone parsePhone(String phoneE164) {
        String normalized = blankToNull(phoneE164);
        return normalized == null ? E164Phone.empty() : new E164Phone(normalized);
    }

    private static String nextCustomerCode() {
        return "C-" + LocalDate.now().format(CODE_DATE) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeEmail(String email) {
        String normalized = blankToNull(email);
        if (normalized != null && !EMAIL.matcher(normalized).matches()) {
            throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);
        }
        return normalized;
    }
}
