package com.rpb.reservation.platformbilling.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpb.reservation.platformbilling.persistence.PlatformProductLineRepository;
import com.rpb.reservation.platformbilling.persistence.PlatformProductLinePriceRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformProductLineServiceTest {
    private PlatformProductLineRepository repository;
    private PlatformProductLinePriceRepository prices;
    private PlatformProductLineService service;

    @BeforeEach
    void setUp() {
        repository = mock(PlatformProductLineRepository.class);
        prices = mock(PlatformProductLinePriceRepository.class);
        service = new PlatformProductLineService(repository, prices);
    }

    @Test
    void listsReservationQueueAsFirstProductLine() {
        PlatformProductLine reservationQueue = productLine("reservation_queue", "预约排队叫号产线", "active");
        when(repository.findAll()).thenReturn(List.of(reservationQueue));
        when(prices.findByAppKeys(List.of("reservation_queue"))).thenReturn(priceList());

        List<PlatformProductLine> productLines = service.listProductLines();

        assertThat(productLines.getFirst().appKey()).isEqualTo("reservation_queue");
        assertThat(productLines.getFirst().displayName()).isEqualTo("预约排队叫号产线");
        assertThat(productLines.getFirst().prices()).hasSize(2);
    }

    @Test
    void searchesProductLinesWithPaginationAndPrices() {
        PlatformProductLine reservationQueue = productLine("reservation_queue", "预约排队叫号产线", "active");
        PlatformProductLinePage page = new PlatformProductLinePage(List.of(reservationQueue), 3, 1, 2);
        PlatformProductLineQuery query = new PlatformProductLineQuery("queue", "active", 1, 2);
        when(repository.search(query)).thenReturn(page);
        when(prices.findByAppKeys(List.of("reservation_queue"))).thenReturn(priceList());

        PlatformProductLinePage result = service.searchProductLines(query);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().prices()).hasSize(2);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    void createsDisabledProductLineWithControlledAppKeyAndEntryRoute() {
        PlatformProductLine created = productLine("crm_suite", "CRM 系统", "disabled", "");
        PlatformProductLineCreateCommand command = new PlatformProductLineCreateCommand(
            "crm_suite",
            "CRM 系统",
            "disabled",
            "",
            "先登记，后续开发",
            20
        );
        when(repository.findByAppKey("crm_suite")).thenReturn(Optional.empty());
        when(repository.create(command)).thenReturn(created);
        when(prices.findByAppKeys(List.of("crm_suite"))).thenReturn(List.of());

        PlatformProductLine result = service.createProductLine(command);

        assertThat(result.appKey()).isEqualTo("crm_suite");
        assertThat(result.status()).isEqualTo("disabled");
        assertThat(result.defaultEntryRoute()).isEmpty();
        verify(repository).create(command);
    }

    @Test
    void rejectsInvalidAppKeyDuplicateAndUncontrolledEntryRoute() {
        when(repository.findByAppKey("reservation_queue")).thenReturn(Optional.of(productLine("reservation_queue", "预约排队叫号产线", "active")));

        assertThatThrownBy(() -> service.createProductLine(new PlatformProductLineCreateCommand(
            "Bad Key",
            "Bad",
            "disabled",
            "",
            null,
            1
        )))
            .isInstanceOf(PlatformBillingServiceException.class)
            .hasMessageContaining(PlatformBillingServiceErrorCode.REQUEST_INVALID.name());

        assertThatThrownBy(() -> service.createProductLine(new PlatformProductLineCreateCommand(
            "reservation_queue",
            "重复",
            "disabled",
            "",
            null,
            1
        )))
            .isInstanceOf(PlatformBillingServiceException.class)
            .hasMessageContaining(PlatformBillingServiceErrorCode.PRODUCT_LINE_CONFLICT.name());

        assertThatThrownBy(() -> service.createProductLine(new PlatformProductLineCreateCommand(
            "crm_suite",
            "CRM 系统",
            "disabled",
            "/random/free-text",
            null,
            1
        )))
            .isInstanceOf(PlatformBillingServiceException.class)
            .hasMessageContaining(PlatformBillingServiceErrorCode.REQUEST_INVALID.name());
    }

    @Test
    void updatesProductLineWithoutChangingAppKey() {
        PlatformProductLine updated = productLine("reservation_queue", "预约排队叫号产线", "active");
        when(repository.findByAppKey("reservation_queue")).thenReturn(Optional.of(productLine("reservation_queue", "旧名称", "active")));
        when(repository.update("reservation_queue", new PlatformProductLineMutationCommand(
            "预约排队叫号产线",
            "active",
            "/stores/:storeId/staff",
            "预约、排队、叫号一体化产线",
            10
        ))).thenReturn(updated);
        when(prices.findByAppKeys(List.of("reservation_queue"))).thenReturn(priceList());

        PlatformProductLine result = service.updateProductLine(
            "reservation_queue",
            new PlatformProductLineMutationCommand("预约排队叫号产线", "active", "预约、排队、叫号一体化产线", 10)
        );

        assertThat(result.appKey()).isEqualTo(updated.appKey());
        assertThat(result.displayName()).isEqualTo(updated.displayName());
        assertThat(result.prices()).hasSize(2);
        verify(repository).update("reservation_queue", new PlatformProductLineMutationCommand(
            "预约排队叫号产线",
            "active",
            "/stores/:storeId/staff",
            "预约、排队、叫号一体化产线",
            10
        ));
    }

    @Test
    void updatesMonthlyAndYearlyPrices() {
        PlatformProductLine productLine = productLine("reservation_queue", "预约排队叫号产线", "active");
        List<PlatformProductLinePriceUpdate> updates = List.of(
            new PlatformProductLinePriceUpdate("monthly", new BigDecimal("128.00"), "SGD", "active", 0),
            new PlatformProductLinePriceUpdate("yearly", new BigDecimal("1200.00"), "SGD", "active", 0)
        );
        when(repository.findByAppKey("reservation_queue")).thenReturn(Optional.of(productLine));
        when(prices.replacePrices("reservation_queue", updates)).thenReturn(priceList());
        when(prices.findByAppKeys(List.of("reservation_queue"))).thenReturn(priceList());

        PlatformProductLine result = service.updateProductLinePrices("reservation_queue", updates);

        assertThat(result.prices()).hasSize(2);
        assertThat(result.prices()).extracting(PlatformProductLinePrice::billingCycle).containsExactly("monthly", "yearly");
        verify(prices).replacePrices("reservation_queue", updates);
    }

    @Test
    void rejectsUnknownProductLineAndInvalidStatus() {
        when(repository.findByAppKey("missing_app")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProductLine(
            "missing_app",
            new PlatformProductLineMutationCommand("Missing", "active", "Missing", 99)
        ))
            .isInstanceOf(PlatformBillingServiceException.class)
            .hasMessageContaining(PlatformBillingServiceErrorCode.PRODUCT_LINE_NOT_FOUND.name());

        assertThatThrownBy(() -> service.updateProductLine(
            "reservation_queue",
            new PlatformProductLineMutationCommand("预约排队叫号产线", "archived", "Invalid", 10)
        ))
            .isInstanceOf(PlatformBillingServiceException.class)
            .hasMessageContaining(PlatformBillingServiceErrorCode.REQUEST_INVALID.name());
    }

    private static PlatformProductLine productLine(String appKey, String displayName, String status) {
        return productLine(appKey, displayName, status, "/stores/:storeId/staff");
    }

    private static PlatformProductLine productLine(String appKey, String displayName, String status, String defaultEntryRoute) {
        return new PlatformProductLine(
            appKey,
            displayName,
            status,
            defaultEntryRoute,
            "预约、排队、叫号一体化产线",
            10,
            OffsetDateTime.parse("2026-06-26T00:00:00Z"),
            OffsetDateTime.parse("2026-06-26T00:00:00Z")
        );
    }

    private static List<PlatformProductLinePrice> priceList() {
        return List.of(
            new PlatformProductLinePrice("reservation_queue", "monthly", new BigDecimal("128.00"), "SGD", "active", 0),
            new PlatformProductLinePrice("reservation_queue", "yearly", new BigDecimal("1200.00"), "SGD", "active", 0)
        );
    }
}
