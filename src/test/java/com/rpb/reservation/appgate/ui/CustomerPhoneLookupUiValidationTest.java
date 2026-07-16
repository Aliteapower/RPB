package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerPhoneLookupUiValidationTest {

    @Test
    void customerPhoneLookupContractAndBackendBoundariesAreDeclared() throws Exception {
        Path contractPath = Path.of("docs", "api", "CUSTOMER_PHONE_LOOKUP_API_CONTRACT.md");
        Path permissionPath = Path.of("src", "main", "java", "com", "rpb", "reservation", "appgate", "domain", "AppGateRequiredPermission.java");
        Path localSecurityPath = Path.of("src", "main", "java", "com", "rpb", "reservation", "walkin", "auth", "LocalRuntimeSecurityConfiguration.java");
        Path controllerPath = Path.of("src", "main", "java", "com", "rpb", "reservation", "customer", "api", "CustomerPhoneLookupController.java");
        Path servicePath = Path.of("src", "main", "java", "com", "rpb", "reservation", "customer", "application", "service", "CustomerPhoneLookupApplicationService.java");
        Path portPath = Path.of("src", "main", "java", "com", "rpb", "reservation", "customer", "application", "port", "out", "CustomerLookupReadPort.java");

        assertThat(contractPath).exists();
        assertThat(controllerPath).exists();
        assertThat(servicePath).exists();
        assertThat(portPath).exists();

        String source = FrontendSourceSupport.readString(contractPath)
            + FrontendSourceSupport.readString(permissionPath)
            + FrontendSourceSupport.readString(localSecurityPath)
            + FrontendSourceSupport.readString(controllerPath)
            + FrontendSourceSupport.readString(servicePath)
            + FrontendSourceSupport.readString(portPath);

        assertThat(source)
            .contains("/api/v1/stores/{storeId}/customers/phone-lookup")
            .contains("customer.lookup")
            .contains("CUSTOMER_LOOKUP")
            .contains("CustomerPhoneLookupApplicationService")
            .contains("CustomerLookupReadPort")
            .contains("findActiveByPhone")
            .contains("INVALID_PHONE_E164")
            .contains(".requestMatchers(HttpMethod.GET, \"/api/v1/stores/*/customers/phone-lookup\").permitAll()")
            .doesNotContain(" like ")
            .doesNotContain("Page<")
            .doesNotContain("Idempotency-Key");
    }

    @Test
    void staffFormsUseSharedPhoneLookupComponentForAutoFill() throws Exception {
        Path typesPath = Path.of("src", "types", "customerPhoneLookup.ts");
        Path apiPath = Path.of("src", "api", "customerPhoneLookupApi.ts");
        Path componentPath = Path.of("src", "components", "staff", "StaffGuestContactLookup.vue");
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");

        assertThat(typesPath).exists();
        assertThat(apiPath).exists();
        assertThat(componentPath).exists();
        assertThat(zhPath).exists();

        String sharedSource = FrontendSourceSupport.readString(typesPath)
            + FrontendSourceSupport.readString(apiPath)
            + FrontendSourceSupport.readString(componentPath);
        String zhSource = FrontendSourceSupport.readString(zhPath);

        assertThat(sharedSource)
            .contains("lookupCustomerByPhone")
            .contains("/api/v1/stores/${encodeURIComponent(storeId)}/customers/phone-lookup")
            .contains("phoneE164")
            .contains("found: boolean")
            .contains("customerId")
            .contains("update:customerId")
            .contains("update:customerName")
            .contains("update:salutation")
            .contains("toSingaporePhoneE164(phoneLocal)")
            .contains("StaffGuestNameField")
            .contains("StaffSingaporePhoneField")
            .contains("customer-lookup")
            .contains("staffControls.guest.lookup.found");

        assertThat(zhSource)
            .contains("found: '已识别顾客'")
            .contains("notFound: '新手机号'")
            .contains("error: '顾客识别失败'");

        for (Path path : List.of(
            Path.of("src", "components", "reservation-workbench", "CreateReservationDialog.vue"),
            Path.of("src", "pages", "WalkInQueuePage.vue"),
            Path.of("src", "pages", "WalkInDirectSeatingPage.vue")
        )) {
            String pageSource = FrontendSourceSupport.readString(path);

            assertThat(pageSource)
                .as("%s should use the shared phone lookup component", path)
                .contains("StaffGuestContactLookup")
                .contains("customerId: ''")
                .contains("customerId: optionalValue(form.customerId)")
                .contains("v-model:customer-id=\"form.customerId\"")
                .contains("v-model:customer-name=\"form.customerName\"")
                .contains("v-model:salutation=\"form.customerSalutation\"")
                .contains("v-model:phone-local=\"form.phoneLocal\"")
                .doesNotContain("lookupCustomerByPhone(")
                .doesNotContain("fetch(`/api/v1/stores/");
        }
    }
}
