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

        assertThat(productLinesPagePath).exists();
        assertThat(tenantBillingPagePath).exists();
        assertThat(productLineListPath).exists();
        assertThat(productLineDrawerPath).exists();
        assertThat(productLinePriceFormPath).exists();
        assertThat(productLineCatalogPath).exists();
        assertThat(apiPath).exists();
        assertThat(typesPath).exists();

        String productLinesPage = Files.readString(productLinesPagePath);
        String tenantBillingPage = Files.readString(tenantBillingPagePath);
        String productLineList = Files.readString(productLineListPath);
        String productLineDrawer = Files.readString(productLineDrawerPath);
        String productLinePriceForm = Files.readString(productLinePriceFormPath);
        String productLineCatalog = Files.readString(productLineCatalogPath);
        String api = Files.readString(apiPath);
        String types = Files.readString(typesPath);
        String router = Files.readString(routerPath);
        String nav = Files.readString(navPath);
        String tenantTable = Files.readString(tenantTablePath);

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
            .contains("产品线")
            .contains("租户计费");
        assertThat(tenantTable).contains("计费").contains("订阅/计费").contains("billingOnly").contains("billing");

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
            .contains("interface PlatformProductLineCreateMutation")
            .contains("interface PlatformProductLineListQuery")
            .contains("total?: number")
            .contains("interface TenantProductSubscription")
            .contains("interface ProductSubscriptionMutation");

        assertThat(productLinesPage)
            .contains("产品线")
            .contains("预约排队叫号产线")
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
            .contains("新增产品线")
            .contains("产品线 / App Key / 说明")
            .contains("全部状态")
            .contains("查询")
            .contains("重置")
            .contains("上一页")
            .contains("下一页")
            .contains("pageCount")
            .contains("emit('page'")
            .contains("emit('create')")
            .contains("emit('edit', productLine)");

        assertThat(productLineDrawer)
            .contains("role=\"dialog\"")
            .contains("产品线代码")
            .contains("computedAppKey")
            .contains(":value=\"mode === 'create' ? computedAppKey : form.appKey\"")
            .contains("v-model=\"form.defaultEntryRoute\"")
            .contains("entryRouteOptions")
            .contains("创建产品线")
            .contains("PlatformProductLinePriceForm")
            .doesNotContain("v-model=\"form.appKey\"")
            .doesNotContain("<input v-model=\"form.defaultEntryRoute\"");

        assertThat(productLinePriceForm)
            .contains("月付价格")
            .contains("年付价格")
            .contains("保存定价")
            .contains("monthlyStatus")
            .contains("yearlyStatus");

        assertThat(productLineCatalog)
            .contains("defaultEntryRouteOptions")
            .contains("暂不配置入口")
            .contains("/stores/:storeId/staff")
            .contains("normalizeProductLineAppKey")
            .contains("isProductLineAppKeyValid");

        assertThat(tenantBillingPage)
            .contains("订阅 / 计费")
            .contains("历史赠送 / 永久有效")
            .contains("有效期开始")
            .contains("金额")
            .contains("币种")
            .contains("购买数量")
            .contains("个月")
            .contains("标准单价")
            .contains("本次金额")
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
