package com.rpb.reservation.platformbilling.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.platformbilling.application.PlatformProductLine;
import com.rpb.reservation.platformbilling.application.PlatformProductLineCreateCommand;
import com.rpb.reservation.platformbilling.application.PlatformProductLineMutationCommand;
import com.rpb.reservation.platformbilling.application.PlatformProductLinePage;
import com.rpb.reservation.platformbilling.application.PlatformProductLineQuery;
import com.rpb.reservation.platformbilling.application.PlatformProductLinePriceUpdate;
import com.rpb.reservation.platformbilling.application.PlatformProductLineService;
import com.rpb.reservation.walkin.auth.LocalAuthProperties;
import com.rpb.reservation.walkin.auth.LocalRuntimeCurrentActorProvider;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import java.time.OffsetDateTime;
import java.util.List;
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

@WebMvcTest(PlatformProductLineController.class)
@Import({
    AppGateApiErrorMapper.class,
    LocalRuntimeCurrentActorProvider.class,
    LocalRuntimeSecurityConfiguration.class
})
@EnableConfigurationProperties(LocalAuthProperties.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "rpb.local-auth.enabled=true",
    "rpb.local-auth.tenant-id=10000000-0000-0000-0000-000000000983",
    "rpb.local-auth.actor-id=30000000-0000-0000-0000-000000000901",
    "rpb.local-auth.actor-type=platform_admin",
    "rpb.local-auth.roles[0]=platform_admin",
    "rpb.local-auth.permissions[0]=platform.product_line.manage"
})
class PlatformProductLineControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformProductLineService productLineService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void listsReservationQueueProductLine() throws Exception {
        when(productLineService.searchProductLines(any(PlatformProductLineQuery.class)))
            .thenReturn(new PlatformProductLinePage(List.of(productLine()), 1, 0, 20));

        mockMvc.perform(get("/api/v1/platform/product-lines"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.productLines[0].appKey").value("reservation_queue"))
            .andExpect(jsonPath("$.productLines[0].displayName").value("预约排队叫号产线"))
            .andExpect(jsonPath("$.items[0].appKey").value("reservation_queue"))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void listsProductLinesWithPaginationFilters() throws Exception {
        when(productLineService.searchProductLines(any(PlatformProductLineQuery.class)))
            .thenReturn(new PlatformProductLinePage(List.of(productLine()), 12, 1, 5));

        mockMvc.perform(get("/api/v1/platform/product-lines")
                .param("keyword", "reservation")
                .param("status", "active")
                .param("page", "1")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.total").value(12))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void createsDisabledProductLineWithControlledRoute() throws Exception {
        when(productLineService.createProductLine(any(PlatformProductLineCreateCommand.class)))
            .thenReturn(productLine("crm_suite", "CRM 系统", "disabled", ""));

        mockMvc.perform(post("/api/v1/platform/product-lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "appKey":"crm_suite",
                      "displayName":"CRM 系统",
                      "status":"disabled",
                      "defaultEntryRoute":"",
                      "description":"后续开发 CRM 产品线前登记",
                      "sortOrder":20
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.productLine.appKey").value("crm_suite"))
            .andExpect(jsonPath("$.productLine.status").value("disabled"))
            .andExpect(jsonPath("$.productLine.defaultEntryRoute").value(""));
    }

    @Test
    void updatesProductLineWhenDedicatedOrTenantManagePermissionIsPresent() throws Exception {
        when(productLineService.updateProductLine(any(), any(PlatformProductLineMutationCommand.class)))
            .thenReturn(productLine());
        when(productLineService.searchProductLines(any(PlatformProductLineQuery.class)))
            .thenReturn(new PlatformProductLinePage(List.of(productLine()), 1, 0, 20));

        mockMvc.perform(patch("/api/v1/platform/product-lines/reservation_queue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName":"预约排队叫号产线",
                      "status":"active",
                      "description":"预约、排队、叫号一体化产线",
                      "sortOrder":10
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.productLine.displayName").value("预约排队叫号产线"));

        mockMvc.perform(get("/api/v1/platform/product-lines")
                .header("X-Test-Permissions", "platform.tenant.manage"))
            .andExpect(status().isOk());
    }

    @Test
    void updatesProductLinePrices() throws Exception {
        when(productLineService.updateProductLinePrices(any(), any()))
            .thenReturn(productLine());

        mockMvc.perform(patch("/api/v1/platform/product-lines/reservation_queue/prices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "prices": [
                        {"billingCycle":"monthly","amount":128.00,"currency":"SGD","status":"active","version":0},
                        {"billingCycle":"yearly","amount":1200.00,"currency":"SGD","status":"active","version":0}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.productLine.appKey").value("reservation_queue"));
    }

    @Test
    void rejectsPlatformProductLineApiWithoutPlatformBillingBoundaryPermission() throws Exception {
        mockMvc.perform(get("/api/v1/platform/product-lines")
                .header("X-Test-Permissions", "reservation.today_view"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private static PlatformProductLine productLine() {
        return productLine("reservation_queue", "预约排队叫号产线", "active", "/stores/:storeId/staff");
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
}
