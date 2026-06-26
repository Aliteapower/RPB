package com.rpb.reservation.platformbilling.application;

import com.rpb.reservation.platformbilling.persistence.PlatformProductLineRepository;
import com.rpb.reservation.platformbilling.persistence.PlatformProductLinePriceRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformProductLineService {
    private static final String ACTIVE = "active";
    private static final String DISABLED = "disabled";

    private final PlatformProductLineRepository repository;
    private final PlatformProductLinePriceRepository prices;

    public PlatformProductLineService(PlatformProductLineRepository repository, PlatformProductLinePriceRepository prices) {
        this.repository = repository;
        this.prices = prices;
    }

    public List<PlatformProductLine> listProductLines() {
        return attachPrices(repository.findAll());
    }

    public PlatformProductLine updateProductLine(String appKey, PlatformProductLineMutationCommand command) {
        if (appKey == null || appKey.isBlank() || command == null) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        validate(command);
        repository.findByAppKey(appKey.trim())
            .orElseThrow(() -> new PlatformBillingServiceException(PlatformBillingServiceErrorCode.PRODUCT_LINE_NOT_FOUND));
        return attachPrices(repository.update(appKey.trim(), normalized(command)));
    }

    @Transactional
    public PlatformProductLine updateProductLinePrices(String appKey, List<PlatformProductLinePriceUpdate> priceUpdates) {
        if (appKey == null || appKey.isBlank() || priceUpdates == null || priceUpdates.isEmpty()) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        String normalizedAppKey = appKey.trim();
        repository.findByAppKey(normalizedAppKey)
            .orElseThrow(() -> new PlatformBillingServiceException(PlatformBillingServiceErrorCode.PRODUCT_LINE_NOT_FOUND));
        prices.replacePrices(normalizedAppKey, priceUpdates);
        return attachPrices(repository.findByAppKey(normalizedAppKey).orElseThrow());
    }

    private PlatformProductLine attachPrices(PlatformProductLine productLine) {
        return attachPrices(List.of(productLine)).getFirst();
    }

    private List<PlatformProductLine> attachPrices(List<PlatformProductLine> productLines) {
        Map<String, List<PlatformProductLinePrice>> pricesByAppKey = prices.findByAppKeys(
            productLines.stream().map(PlatformProductLine::appKey).toList()
        ).stream().collect(Collectors.groupingBy(PlatformProductLinePrice::appKey));
        return productLines.stream()
            .map(productLine -> productLine.withPrices(pricesByAppKey
                .getOrDefault(productLine.appKey(), List.of())
                .stream()
                .sorted(Comparator.comparing(PlatformProductLinePrice::billingCycle))
                .toList()))
            .toList();
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
