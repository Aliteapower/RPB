package com.rpb.reservation.walkin.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateAccessRequest;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.reservation.api.ReservationTableAssignmentApiErrorMapper;
import com.rpb.reservation.reservation.api.ReservationTableAssignmentApiMapper;
import com.rpb.reservation.reservation.api.ReservationTableAssignmentController;
import com.rpb.reservation.reservation.application.AssignableReservationTable;
import com.rpb.reservation.reservation.application.AssignableReservationTablesResult;
import com.rpb.reservation.reservation.application.ReservationTableAssignmentResult;
import com.rpb.reservation.reservation.application.service.ReservationTableAssignmentApplicationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReservationTableAssignmentController.class)
@Import({
    AppGateApiErrorMapper.class,
    ReservationTableAssignmentApiMapper.class,
    ReservationTableAssignmentApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000009951",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000009951",
    "rpb.local-auth.actor-type=staff",
    "rpb.local-auth.roles[0]=store_staff",
    "rpb.local-auth.permissions[0]=table.view",
    "rpb.local-auth.permissions[1]=reservation.create",
    "rpb.local-auth.store-ids[0]=20000000-0000-0000-0000-000000009951"
})
class LocalRuntimeReservationTableAssignmentSecurityTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000009951");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000009951");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000009951");
    private static final UUID TABLE_ID = UUID.fromString("70000000-0000-0000-0000-000000009951");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationTableAssignmentApplicationService applicationService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void localEmployeeCanQueryAndAssignWithConfiguredPermissions() throws Exception {
        when(appGateService.evaluate(any())).thenAnswer(invocation -> {
            String permission = invocation.<AppGateAccessRequest>getArgument(0).requiredPermission();
            return AppGateDecision.allow("reservation_queue", TENANT_ID, STORE_ID, permission);
        });
        when(applicationService.listAssignableTables(any())).thenReturn(AssignableReservationTablesResult.success(
            RESERVATION_ID,
            2,
            List.of(new AssignableReservationTable(TABLE_ID, "A01", "A01", "Main", 1, 4))
        ));
        when(applicationService.assignTable(any())).thenReturn(ReservationTableAssignmentResult.success(
            RESERVATION_ID,
            TABLE_ID,
            "A01",
            "completed",
            UUID.randomUUID(),
            UUID.randomUUID()
        ));

        mockMvc.perform(get(
                "/api/v1/stores/{storeId}/reservations/{reservationId}/assignable-tables",
                STORE_ID,
                RESERVATION_ID
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tables[0].tableCode").value("A01"));

        mockMvc.perform(put(
                "/api/v1/stores/{storeId}/reservations/{reservationId}/table-assignment",
                STORE_ID,
                RESERVATION_ID
            )
                .header("Idempotency-Key", "local-table-assignment-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":\"%s\"}".formatted(TABLE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tableCode").value("A01"));

        verify(applicationService).listAssignableTables(any());
        verify(applicationService).assignTable(any());
    }
}
