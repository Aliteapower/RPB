package com.rpb.reservation.platformbilling.application;

import com.rpb.reservation.platformbilling.persistence.PlatformProductLineRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlatformProductLineService {
    private static final String ACTIVE = "active";
    private static final String DISABLED = "disabled";

    private final PlatformProductLineRepository repository;

    public PlatformProductLineService(PlatformProductLineRepository repository) {
        this.repository = repository;
    }

    public List<PlatformProductLine> listProductLines() {
        return repository.findAll();
    }

    public PlatformProductLine updateProductLine(String appKey, PlatformProductLineMutationCommand command) {
        if (appKey == null || appKey.isBlank() || command == null) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        validate(command);
        repository.findByAppKey(appKey.trim())
            .orElseThrow(() -> new PlatformBillingServiceException(PlatformBillingServiceErrorCode.PRODUCT_LINE_NOT_FOUND));
        return repository.update(appKey.trim(), normalized(command));
    }

    private static void validate(PlatformProductLineMutationCommand command) {
        if (command.displayName() == null || command.displayName().isBlank()) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        String status = command.status() == null ? "" : command.status().trim();
        if (!ACTIVE.equals(status) && !DISABLED.equals(status)) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        if (command.sortOrder() != null && command.sortOrder() < 0) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static PlatformProductLineMutationCommand normalized(PlatformProductLineMutationCommand command) {
        return new PlatformProductLineMutationCommand(
            command.displayName().trim(),
            command.status().trim(),
            textOrNull(command.description()),
            command.sortOrder() == null ? 0 : command.sortOrder()
        );
    }

    private static String textOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
