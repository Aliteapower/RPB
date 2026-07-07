package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlatformProductLineBillingUiImplementationValidationTest {
    @Test
    void platformBillingPagesUseManualBillingApisAndExposeLoadingErrorSavingStates() throws Exception {
        Path productLinesPagePath = Path.of("src", "pages", "PlatformProductLinesPage.vue");
        Path tenantBillingPagePath = Path.of("src", "pages", "PlatformTenantBillingPage.vue");
        Path productLineListPath = Path.of("src", "components", "platform", "product-line", "PlatformProductLineList.vue");
        Path productLineDrawerPath = Path.of("src", "components", "platform", "product-line", "PlatformProductLineDrawer.vue");
        Path productLinePriceFormPath = Path.of("src", "components", "platform", "product-line", "PlatformProductLinePriceForm.vue");
        Path productLineCatalogPath = Path.of("src", "components", "platform", "product-line", "productLineCatalog.ts");
        Path apiPath = Path.of("src", "api", "platformProductLineBillingApi.ts");
        Path typesPath = Path.of("src", "types", "platformProductLineBilling.ts");
        Path routerPath = Path.of("src", "router", "index.ts");
        Path navPath = Path.of("src", "components", "platform", "PlatformAdminNav.vue");
        Path tenantTablePath = Path.of("src", "components", "platform", "PlatformTenantTable.vue");
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");

        assertThat(productLinesPagePath).exists();
        assertThat(tenantBillingPagePath).exists();
        assertThat(productLineListPath).exists();
        assertThat(productLineDrawerPath).exists();
        assertThat(productLinePriceFormPath).exists();
        assertThat(productLineCatalogPath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();

        String productLinesPage = FrontendSourceSupport.readString(productLinesPagePath);
        String tenantBillingPage = FrontendSourceSupport.readString(tenantBillingPagePath);
        String productLineList = FrontendSourceSupport.readString(productLineListPath);
        String productLineDrawer = FrontendSourceSupport.readString(productLineDrawerPath);
        String productLinePriceForm = FrontendSourceSupport.readString(productLinePriceFormPath);
        String productLineCatalog = FrontendSourceSupport.readString(productLineCatalogPath);
        String api = FrontendSourceSupport.readString(apiPath);
        String types = FrontendSourceSupport.readString(typesPath);
        String router = FrontendSourceSupport.readString(routerPath);
        String nav = FrontendSourceSupport.readString(navPath);
        String tenantTable = FrontendSourceSupport.readString(tenantTablePath);
        String zh = FrontendSourceSupport.readString(zhPath);

        assertThat(router)
            .contains("PlatformProductLinesPage")
            .contains("PlatformTenantBillingPage")
            .contains("path: '/platform/settings/product-lines'")
            .contains("path: '/platform/billing/subscriptions'")
            .contains("path: '/platform/tenants/:tenantId/billing'")
            .contains("meta: { requiresPlatformAdmin: true }");

        assertThat(nav)
            .contains("/platform/settings/product-lines")
            .contains("/platform/billing/subscriptions")
            .contains("nav.platform.productLines")
            .contains("nav.platform.billing");
        assertThat(zh)
            .contains("productLines: '产品线'")
            .contains("billing: '租户计费'");
        assertThat(tenantTable)
            .contains("platform.tenants.table.billingShort")
            .contains("platform.tenants.table.billingFull")
            .contains("billingOnly")
            .contains("billing");

        assertThat(api)
            .contains("listProductLines")
            .contains("createProductLine")
            .contains("updateProductLine")
            .contains("toQueryString")
            .contains("listTenantProductSubscriptions")
            .contains("purchaseProductSubscription")
            .contains("renewProductSubscription")
            .contains("suspendProductSubscription")
            .contains("cancelProductSubscription")
            .contains("convertLegacyProductSubscription")
            .contains("/api/v1/platform/product-lines")
            .contains("/product-subscriptions")
            .doesNotContain("paymentGateway")
            .doesNotContain("webhook")
            .doesNotContain("invoice");

        assertThat(types)
            .contains("type ProductBillingCycle")
            .contains("'legacy_grant'")
            .contains("type ProductSubscriptionStatus")
            .contains("interface TenantProductSubscriptionItem")
            .contains("items: TenantProductSubscriptionItem[]")
            .contains("interface PlatformProductLineCreateMutation")
            .contains("interface PlatformProductLineListQuery")
            .contains("total?: number")
            .contains("interface TenantProductSubscription")
            .contains("interface ProductSubscriptionMutation");

        assertThat(productLinesPage)
            .contains("platform.productLines.page.title")
            .contains("platform.productLines.page.defaultDisplayName")
            .contains("reservation_queue")
            .contains("PlatformProductLineList")
            .contains("PlatformProductLineDrawer")
            .contains("createProductLine")
            .contains("computedAppKey")
            .contains("normalizeProductLineAppKey")
            .contains("defaultEntryRoute")
            .contains("updateProductLinePrices")
            .contains("openProductLineDrawer")
            .contains("openCreateProductLineDrawer")
            .contains("closeProductLineDrawer")
            .contains("editDrawerOpen")
            .contains("page")
            .contains("total")
            .contains("loading")
            .contains("errorText")
            .contains("saving")
            .contains("updateProductLine")
            .doesNotContain("side-panels")
            .doesNotContain("grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.8fr)")
            .doesNotContain("v-model=\"form.appKey\"");

        assertThat(productLineList)
            .contains("platform.productLines.list.create")
            .contains("platform.productLines.list.keywordPlaceholder")
            .contains("platform.productLines.list.allStatuses")
            .contains("common.actions.query")
            .contains("common.actions.reset")
            .contains("common.actions.previousPage")
            .contains("common.actions.nextPage")
            .contains("pageCount")
            .contains("emit('page'")
            .contains("emit('create')")
            .contains("emit('edit', productLine)");

        assertThat(productLineDrawer)
            .contains("role=\"dialog\"")
            .contains("platform.productLines.drawer.productCode")
            .contains("computedAppKey")
            .contains(":value=\"mode === 'create' ? computedAppKey : form.appKey\"")
            .contains("v-model=\"form.defaultEntryRoute\"")
            .contains("entryRouteOptions")
            .contains("platform.productLines.drawer.createAction")
            .contains("PlatformProductLinePriceForm")
            .doesNotContain("v-model=\"form.appKey\"")
            .doesNotContain("<input v-model=\"form.defaultEntryRoute\"");

        assertThat(productLinePriceForm)
            .contains("platform.productLines.priceForm.monthlyAmount")
            .contains("platform.productLines.priceForm.yearlyAmount")
            .contains("platform.productLines.priceForm.save")
            .contains("monthlyStatus")
            .contains("yearlyStatus");

        assertThat(productLineCatalog)
            .contains("defaultEntryRouteOptions")
            .contains("platform.productLines.entryRoutes.none.label")
            .contains("/stores/:storeId/staff")
            .contains("normalizeProductLineAppKey")
            .contains("isProductLineAppKeyValid");

        assertThat(tenantBillingPage)
            .contains("platform.billing.page.title")
            .contains("platform.billing.cycles.legacyGrant")
            .contains("platform.billing.table.columns.periodStart")
            .contains("platform.billing.table.columns.amount")
            .contains("platform.billing.table.columns.currency")
            .contains("platform.billing.form.duration")
            .contains("platform.billing.units.month")
            .contains("platform.billing.form.unitPrice")
            .contains("platform.billing.form.amount")
            .contains("platform.billing.form.storeCount")
            .contains("platform.billing.form.storeUnitAmount")
            .contains("platform.billing.storeItems.title")
            .contains("subscription.items")
            .contains("storeItemRows")
            .contains("calculatedAmount")
            .contains("durationCount: safeDurationCount.value")
            .contains("purchaseProductSubscription")
            .contains("renewProductSubscription")
            .contains("suspendProductSubscription")
            .contains("cancelProductSubscription")
            .contains("convertLegacyProductSubscription")
            .contains("newIdempotencyKey")
            .contains("selectedSubscription")
            .contains("submitSelectedProductMutation")
            .contains("primaryActionLabel")
            .contains("toggleProductLine")
            .contains("isProductLineChecked")
            .contains("@change=\"toggleProductLine(row, $event)\"")
            .contains("canRenew")
            .contains("loading")
            .contains("errorText")
            .contains("saving")
            .doesNotContain("@submit.prevent=\"purchaseSelectedProduct\"")
            .doesNotContain(":checked=\"row.subscription?.status === 'active'\" disabled")
            .doesNotContain("amount: calculatedAmount.value")
            .doesNotContain("v-model.number=\"form.amount\"")
            .doesNotContain("type=\"datetime-local\"");
    }
}
