package com.rpb.reservation.platformbilling.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.platformbilling.application.PlatformBillingOperator;
import com.rpb.reservation.platformbilling.application.ProductSubscription;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionCommand;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionItem;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionMutationResult;
import com.rpb.reservation.platformbilling.application.ProductSubscriptionService;
import com.rpb.reservation.walkin.auth.LocalAuthProperties;
import com.rpb.reservation.walkin.auth.LocalRuntimeCurrentActorProvider;
import com.rpb.reservation.walkin.auth.LocalRuntimeSecurityConfiguration;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

@WebMvcTest(PlatformTenantProductSubscriptionController.class)
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
    "rpb.local-auth.permissions[0]=platform.billing.manage"
})
class PlatformTenantProductSubscriptionControllerTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000009301");
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("40000000-0000-0000-0000-000000009301");
    private static final UUID ITEM_ID = UUID.fromString("50000000-0000-0000-0000-000000009301");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductSubscriptionService subscriptionService;

    @MockBean
    private AppGateService appGateService;

    @MockBean
    private AppGateDenialAuditService appGateDenialAuditService;

    @Test
    void listsTenantProductSubscriptionsIncludingLegacyGrant() throws Exception {
        when(subscriptionService.listSubscriptions(TENANT_ID)).thenReturn(List.of(subscription("legacy_grant", "active", null)));

        mockMvc.perform(get("/api/v1/platform/tenants/{tenantId}/product-subscriptions", TENANT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.subscriptions[0].appKey").value("reservation_queue"))
            .andExpect(jsonPath("$.subscriptions[0].billingCycle").value("legacy_grant"))
            .andExpect(jsonPath("$.subscriptions[0].status").value("active"))
            .andExpect(jsonPath("$.subscriptions[0].items[0].storeCode").value("store-main"))
            .andExpect(jsonPath("$.subscriptions[0].items[0].amount").value(128.00))
            .andExpect(jsonPath("$.subscriptions[0].currentPeriodEnd").isEmpty());
    }

    @Test
    void purchaseRenewSuspendCancelAndConvertRequireBillingManagePermissionAndReturnReplayFlag() throws Exception {
        when(subscriptionService.purchase(eq(TENANT_ID), any(ProductSubscriptionCommand.class), any(PlatformBillingOperator.class)))
            .thenReturn(new ProductSubscriptionMutationResult(false, subscription("monthly", "active", OffsetDateTime.parse("2026-07-31T23:59:59Z"))));
        when(subscriptionService.renew(eq(TENANT_ID), eq(SUBSCRIPTION_ID), any(ProductSubscriptionCommand.class), any(PlatformBillingOperator.class)))
            .thenReturn(new ProductSubscriptionMutationResult(true, subscription("monthly", "active", OffsetDateTime.parse("2026-08-31T23:59:59Z"))));
        when(subscriptionService.renewItem(eq(TENANT_ID), eq(SUBSCRIPTION_ID), eq(ITEM_ID), any(ProductSubscriptionCommand.class), any(PlatformBillingOperator.class)))
            .thenReturn(new ProductSubscriptionMutationResult(false, subscription("monthly", "active", OffsetDateTime.parse("2026-09-30T23:59:59Z"))));
        when(subscriptionService.suspend(eq(TENANT_ID), eq(SUBSCRIPTION_ID), any(), any(PlatformBillingOperator.class)))
            .thenReturn(new ProductSubscriptionMutationResult(false, subscription("monthly", "suspended", OffsetDateTime.parse("2026-08-31T23:59:59Z"))));
        when(subscriptionService.cancel(eq(TENANT_ID), eq(SUBSCRIPTION_ID), any(), any(PlatformBillingOperator.class)))
            .thenReturn(new ProductSubscriptionMutationResult(false, subscription("monthly", "cancelled", OffsetDateTime.parse("2026-08-31T23:59:59Z"))));
        when(subscriptionService.convertFromLegacy(eq(TENANT_ID), eq(SUBSCRIPTION_ID), any(ProductSubscriptionCommand.class), any(PlatformBillingOperator.class)))
            .thenReturn(new ProductSubscriptionMutationResult(false, subscription("yearly", "active", OffsetDateTime.parse("2027-06-30T23:59:59Z"))));

        mockMvc.perform(post("/api/v1/platform/tenants/{tenantId}/product-subscriptions/purchase", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mutationJson("purchase-001", "monthly", "2026-07-01T00:00:00Z", "2026-07-31T23:59:59Z", null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.replayed").value(false))
            .andExpect(jsonPath("$.subscription.items[0].storeName").value("Main Store"));

        mockMvc.perform(post("/api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/renew", TENANT_ID, SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mutationJson("renew-001", "monthly", "2026-08-01T00:00:00Z", "2026-08-31T23:59:59Z", 0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replayed").value(true));

        mockMvc.perform(post("/api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/items/{itemId}/renew", TENANT_ID, SUBSCRIPTION_ID, ITEM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(durationMutationJson("renew-item-001", "monthly", 1, 0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.subscription.items[0].billingCycle").value("monthly"))
            .andExpect(jsonPath("$.subscription.items[0].currentPeriodEnd").value("2026-09-30T23:59:59Z"));

        mockMvc.perform(post("/api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/suspend", TENANT_ID, SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusJson("suspend-001", "manual suspend", 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscription.status").value("suspended"));

        mockMvc.perform(post("/api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/cancel", TENANT_ID, SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusJson("cancel-001", "manual cancel", 2)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscription.status").value("cancelled"));

        mockMvc.perform(post("/api/v1/platform/tenants/{tenantId}/product-subscriptions/{subscriptionId}/convert-from-legacy", TENANT_ID, SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mutationJson("convert-001", "yearly", "2026-07-01T00:00:00Z", "2027-06-30T23:59:59Z", 0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscription.billingCycle").value("yearly"));
    }

    @Test
    void rejectsBillingApiWithoutDedicatedOrTenantManagePermission() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenants/{tenantId}/product-subscriptions", TENANT_ID)
                .header("X-Test-Permissions", "reservation.today_view"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        when(subscriptionService.listSubscriptions(TENANT_ID)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/platform/tenants/{tenantId}/product-subscriptions", TENANT_ID)
                .header("X-Test-Permissions", "platform.tenant.manage"))
            .andExpect(status().isOk());
    }

    private static ProductSubscription subscription(String billingCycle, String status, OffsetDateTime periodEnd) {
        return new ProductSubscription(
            SUBSCRIPTION_ID,
            TENANT_ID,
            "reservation_queue",
            "预约排队叫号产线",
            billingCycle,
            status,
            periodEnd != null && status.equals("active") && !periodEnd.isAfter(OffsetDateTime.now()) ? "expired" : status,
            OffsetDateTime.parse("2026-07-01T00:00:00Z"),
            periodEnd,
            new BigDecimal("128.00"),
            "SGD",
            "manual payment",
            status.equals("cancelled") ? "disabled" : status.equals("suspended") ? "suspended" : "enabled",
            periodEnd,
            OffsetDateTime.parse("2026-06-26T00:00:00Z"),
            OffsetDateTime.parse("2026-06-26T00:00:00Z"),
            0,
            List.of(new ProductSubscriptionItem(
                ITEM_ID,
                SUBSCRIPTION_ID,
                TENANT_ID,
                "reservation_queue",
                "store",
                UUID.fromString("20000000-0000-0000-0000-000000009301"),
                "store-main",
                "Main Store",
                null,
                null,
                billingCycle,
                OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                periodEnd,
                1,
                new BigDecimal("128.00"),
                new BigDecimal("128.00"),
                "SGD",
                status,
                "manual payment",
                OffsetDateTime.parse("2026-06-26T00:00:00Z"),
                OffsetDateTime.parse("2026-06-26T00:00:00Z"),
                0
            ))
        );
    }

    private static String mutationJson(
        String idempotencyKey,
        String billingCycle,
        String currentPeriodStart,
        String currentPeriodEnd,
        Integer version
    ) {
        String versionProperty = version == null ? "" : ",\"version\":" + version;
        return """
            {
              "idempotencyKey":"%s",
              "appKey":"reservation_queue",
              "billingCycle":"%s",
              "currentPeriodStart":"%s",
              "currentPeriodEnd":"%s",
              "amount":128.00,
              "currency":"SGD",
              "paymentNote":"manual payment"%s
            }
            """.formatted(idempotencyKey, billingCycle, currentPeriodStart, currentPeriodEnd, versionProperty);
    }

    private static String statusJson(String idempotencyKey, String paymentNote, int version) {
        return """
            {"idempotencyKey":"%s","paymentNote":"%s","version":%d}
            """.formatted(idempotencyKey, paymentNote, version);
    }

    private static String durationMutationJson(String idempotencyKey, String billingCycle, int durationCount, int version) {
        return """
            {
              "idempotencyKey":"%s",
              "appKey":"reservation_queue",
              "billingCycle":"%s",
              "durationCount":%d,
              "currency":"SGD",
              "paymentNote":"single store renewal",
              "version":%d
            }
            """.formatted(idempotencyKey, billingCycle, durationCount, version);
    }
}
