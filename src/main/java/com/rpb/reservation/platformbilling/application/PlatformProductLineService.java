package com.rpb.reservation.platformbilling.application;

import com.rpb.reservation.platformbilling.persistence.PlatformProductLineRepository;
import com.rpb.reservation.platformbilling.persistence.PlatformProductLinePriceRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformProductLineService {
    private static final String ACTIVE = "active";
    private static final String DISABLED = "disabled";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Pattern APP_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final Set<String> CONTROLLED_ENTRY_ROUTES = Set.of("", "/stores/:storeId/staff");

    private final PlatformProductLineRepository repository;
    private final PlatformProductLinePriceRepository prices;

    public PlatformProductLineService(PlatformProductLineRepository repository, PlatformProductLinePriceRepository prices) {
        this.repository = repository;
        this.prices = prices;
    }

    public List<PlatformProductLine> listProductLines() {
        return attachPrices(repository.findAll());
    }

    public PlatformProductLinePage searchProductLines(PlatformProductLineQuery query) {
        PlatformProductLineQuery normalized = normalized(query);
        PlatformProductLinePage page = repository.search(normalized);
        return new PlatformProductLinePage(
            attachPrices(page.items()),
            page.total(),
            page.page(),
            page.size()
        );
    }

    public PlatformProductLine createProductLine(PlatformProductLineCreateCommand command) {
        PlatformProductLineCreateCommand normalized = normalized(command);
        repository.findByAppKey(normalized.appKey()).ifPresent(existing -> {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.PRODUCT_LINE_CONFLICT);
        });
        return attachPrices(repository.create(normalized));
    }

    public PlatformProductLine updateProductLine(String appKey, PlatformProductLineMutationCommand command) {
        if (appKey == null || appKey.isBlank() || command == null) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        validate(command);
        PlatformProductLine existing = repository.findByAppKey(appKey.trim())
            .orElseThrow(() -> new PlatformBillingServiceException(PlatformBillingServiceErrorCode.PRODUCT_LINE_NOT_FOUND));
        PlatformProductLineMutationCommand normalized = normalized(command, existing.defaultEntryRoute());
        return attachPrices(repository.update(appKey.trim(), normalized));
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

    private static PlatformProductLineQuery normalized(PlatformProductLineQuery query) {
        if (query == null) {
            return new PlatformProductLineQuery(null, null, DEFAULT_PAGE, DEFAULT_SIZE);
        }
        int page = Math.max(DEFAULT_PAGE, query.page());
        int size = query.size() <= 0 ? DEFAULT_SIZE : Math.min(query.size(), MAX_SIZE);
        return new PlatformProductLineQuery(
            textOrNull(query.keyword()),
            textOrNull(query.status()),
            page,
            size
        );
    }

    private static PlatformProductLineCreateCommand normalized(PlatformProductLineCreateCommand command) {
        if (command == null) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        String appKey = command.appKey() == null ? "" : command.appKey().trim();
        if (!APP_KEY_PATTERN.matcher(appKey).matches()) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        String status = normalizedStatus(command.status(), DISABLED);
        String entryRoute = normalizedEntryRoute(command.defaultEntryRoute(), "");
        String displayName = normalizedDisplayName(command.displayName());
        return new PlatformProductLineCreateCommand(
            appKey,
            displayName,
            status,
            entryRoute,
            textOrNull(command.description()),
            normalizedSortOrder(command.sortOrder())
        );
    }

    private static PlatformProductLineMutationCommand normalized(
        PlatformProductLineMutationCommand command,
        String existingDefaultEntryRoute
    ) {
        if (command == null) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        return new PlatformProductLineMutationCommand(
            normalizedDisplayName(command.displayName()),
            normalizedStatus(command.status(), null),
            normalizedEntryRoute(command.defaultEntryRoute(), existingDefaultEntryRoute),
            textOrNull(command.description()),
            normalizedSortOrder(command.sortOrder())
        );
    }

    private static String normalizedDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        return displayName.trim();
    }

    private static String normalizedStatus(String status, String defaultStatus) {
        String normalizedStatus = status == null || status.isBlank() ? defaultStatus : status.trim();
        if (!ACTIVE.equals(normalizedStatus) && !DISABLED.equals(normalizedStatus)) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        return normalizedStatus;
    }

    private static String normalizedEntryRoute(String defaultEntryRoute, String fallback) {
        String entryRoute = defaultEntryRoute == null ? fallback : defaultEntryRoute.trim();
        if (!CONTROLLED_ENTRY_ROUTES.contains(entryRoute)) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        return entryRoute;
    }

    private static int normalizedSortOrder(Integer sortOrder) {
        if (sortOrder != null && sortOrder < 0) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        return sortOrder == null ? 0 : sortOrder;
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
        if (command.defaultEntryRoute() != null && !CONTROLLED_ENTRY_ROUTES.contains(command.defaultEntryRoute().trim())) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static String textOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
