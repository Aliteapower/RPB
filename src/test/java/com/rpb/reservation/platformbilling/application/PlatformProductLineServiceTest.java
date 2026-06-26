package com.rpb.reservation.platformbilling.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rpb.reservation.platformbilling.persistence.PlatformProductLineRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformProductLineServiceTest {
    private PlatformProductLineRepository repository;
    private PlatformProductLineService service;

    @BeforeEach
    void setUp() {
        repository = mock(PlatformProductLineRepository.class);
        service = new PlatformProductLineService(repository);
    }

    @Test
    void listsReservationQueueAsFirstProductLine() {
        PlatformProductLine reservationQueue = productLine("reservation_queue", "预约排队叫号产线", "active");
        when(repository.findAll()).thenReturn(List.of(reservationQueue));

        List<PlatformProductLine> productLines = service.listProductLines();

        assertThat(productLines).containsExactly(reservationQueue);
        assertThat(productLines.getFirst().appKey()).isEqualTo("reservation_queue");
        assertThat(productLines.getFirst().displayName()).isEqualTo("预约排队叫号产线");
    }

    @Test
    void updatesProductLineWithoutChangingAppKey() {
        PlatformProductLine updated = productLine("reservation_queue", "预约排队叫号产线", "active");
        when(repository.findByAppKey("reservation_queue")).thenReturn(Optional.of(productLine("reservation_queue", "旧名称", "active")));
        when(repository.update("reservation_queue", new PlatformProductLineMutationCommand(
            "预约排队叫号产线",
            "active",
            "预约、排队、叫号一体化产线",
            10
        ))).thenReturn(updated);

        PlatformProductLine result = service.updateProductLine(
            "reservation_queue",
            new PlatformProductLineMutationCommand("预约排队叫号产线", "active", "预约、排队、叫号一体化产线", 10)
        );

        assertThat(result).isEqualTo(updated);
        verify(repository).update("reservation_queue", new PlatformProductLineMutationCommand(
            "预约排队叫号产线",
            "active",
            "预约、排队、叫号一体化产线",
            10
        ));
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
        return new PlatformProductLine(
            appKey,
            displayName,
            status,
            "/stores/:storeId/staff",
            "预约、排队、叫号一体化产线",
            10,
            OffsetDateTime.parse("2026-06-26T00:00:00Z"),
            OffsetDateTime.parse("2026-06-26T00:00:00Z")
        );
    }
}
