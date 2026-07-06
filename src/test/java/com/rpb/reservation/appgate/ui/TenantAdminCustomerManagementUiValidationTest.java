package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TenantAdminCustomerManagementUiValidationTest {

    @Test
    void tenantAdminCustomerManagementSupportsListSearchEditAndArchive() throws Exception {
        String page = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminCustomersPage.vue"));
        String api = FrontendSourceSupport.readString(Path.of("src", "api", "tenantAdminApi.ts"));
        String router = FrontendSourceSupport.readString(Path.of("src", "router", "index.ts"));
        String nav = FrontendSourceSupport.readString(Path.of("src", "components", "tenant-admin", "TenantAdminNav.vue"));
        String zhGenerated = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "generated-zh-CN.ts"));
        String enGenerated = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "generated-en-SG.ts"));

        assertThat(page)
            .contains("listCustomers")
            .contains("createCustomer")
            .contains("updateCustomer")
            .contains("archiveCustomer")
            .contains("先生")
            .contains("女士")
            .contains("phoneE164")
            .contains("displayName")
            .contains("nickname")
            .contains("email");

        assertThat(api)
            .contains("TenantAdminCustomer")
            .contains("listCustomers")
            .contains("createCustomer")
            .contains("updateCustomer")
            .contains("archiveCustomer");

        assertThat(router)
            .contains("TenantAdminCustomersPage")
            .contains("tenant-admin-customers");

        assertThat(nav)
            .contains("nav.tenant.customers");

        assertThat(zhGenerated)
            .contains("顾客管理")
            .contains("电话号码 / 姓名 / 称呼 / 电邮")
            .contains("归档")
            .contains("先生")
            .contains("女士");

        assertThat(enGenerated)
            .contains("Customers")
            .contains("Phone / name / salutation / email")
            .contains("Archive")
            .contains("Mr")
            .contains("Ms");
    }

    @Test
    void reservationAndPublicBookingSubmitOptionalCustomerProfileEmail() throws Exception {
        String reservationTypes = FrontendSourceSupport.readString(Path.of("src", "types", "reservation.ts"));
        String reservationApi = FrontendSourceSupport.readString(Path.of("src", "api", "reservationCreateApi.ts"));
        String createDialog = FrontendSourceSupport.readString(Path.of(
            "src",
            "components",
            "reservation-workbench",
            "CreateReservationDialog.vue"
        ));
        String publicBookingTypes = FrontendSourceSupport.readString(Path.of("src", "types", "publicBooking.ts"));
        String publicBookingPage = FrontendSourceSupport.readString(Path.of("src", "pages", "PublicBookingPage.vue"));
        String zhGenerated = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "generated-zh-CN.ts"));
        String enGenerated = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "generated-en-SG.ts"));

        assertThat(reservationTypes)
            .contains("customerEmail?: string | null");
        assertThat(reservationApi)
            .contains("customerEmail: request.customerEmail ?? null");
        assertThat(createDialog)
            .contains("customerEmail")
            .contains("reservationWorkbench.createDialog.customerEmail");

        assertThat(publicBookingTypes)
            .contains("customerName: string | null")
            .contains("customerNickname: string | null")
            .contains("customerEmail: string | null");
        assertThat(publicBookingPage)
            .contains("customerName")
            .contains("customerNickname")
            .contains("customerEmail")
            .contains("generated.public-booking.078")
            .contains("generated.public-booking.079")
            .contains("generated.public-booking.080")
            .contains("先生")
            .contains("女士");

        assertThat(zhGenerated)
            .contains("顾客姓名")
            .contains("称呼")
            .contains("电邮");
        assertThat(enGenerated)
            .contains("Customer name")
            .contains("Salutation")
            .contains("Email");
    }
}
