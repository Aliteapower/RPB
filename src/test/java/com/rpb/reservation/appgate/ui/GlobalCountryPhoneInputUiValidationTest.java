package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GlobalCountryPhoneInputUiValidationTest {

    @Test
    void sharedCountryPhoneModuleSupportsSingaporeNowAndChinaLater() throws Exception {
        Path utilityPath = Path.of("src", "utils", "countryPhone.ts");
        Path componentPath = Path.of("src", "components", "common", "CountryPhoneField.vue");
        Path staffWrapperPath = Path.of("src", "components", "staff", "StaffSingaporePhoneField.vue");
        Path staffHelperPath = Path.of("src", "components", "staff", "staffGuestContact.ts");

        assertThat(Files.exists(utilityPath)).isTrue();
        assertThat(Files.exists(componentPath)).isTrue();
        assertThat(Files.exists(staffWrapperPath)).isTrue();
        assertThat(Files.exists(staffHelperPath)).isTrue();

        String utility = FrontendSourceSupport.readString(utilityPath);
        String component = FrontendSourceSupport.readString(componentPath);
        String staffWrapper = FrontendSourceSupport.readString(staffWrapperPath);
        String staffHelper = FrontendSourceSupport.readString(staffHelperPath);

        assertThat(utility)
            .contains("export type PhoneCountryCode = 'SG' | 'CN'")
            .contains("SG: {")
            .contains("callingCode: '+65'")
            .contains("nationalLength: 8")
            .contains("placeholder: '91234567'")
            .contains("CN: {")
            .contains("callingCode: '+86'")
            .contains("nationalLength: 11")
            .contains("placeholder: '13800138000'")
            .contains("function sanitizeCountryLocalPhone")
            .contains("function isValidCountryLocalPhone")
            .contains("function toCountryPhoneE164")
            .contains("function storedPhoneToCountryLocal")
            .contains("function isLegacyCountryPhone");

        assertThat(component)
            .contains("countryCode?: PhoneCountryCode")
            .contains("modelFormat?: 'local' | 'e164'")
            .contains("phoneCountryConfig")
            .contains("sanitizeCountryLocalPhone")
            .contains("storedPhoneToCountryLocal")
            .contains("isLegacyCountryPhone")
            .contains("config.callingCode")
            .contains(":maxlength=\"config.nationalLength\"")
            .contains(":placeholder=\"config.placeholder\"")
            .contains("autocomplete=\"tel-national\"")
            .contains("inputmode=\"numeric\"")
            .contains(":pattern=\"`[0-9]{${config.nationalLength}}`\"")
            .contains("input.value = nextLocal")
            .contains("emit('update:modelValue'");

        assertThat(staffWrapper)
            .contains("CountryPhoneField")
            .contains("model-format=\"local\"")
            .contains(":label=\"t('staffControls.guest.phoneLabel')\"");

        assertThat(staffHelper)
            .contains("from '../../utils/countryPhone'")
            .contains("sanitizeCountryLocalPhone(value, 'SG')")
            .contains("isValidCountryLocalPhone(value, 'SG')")
            .contains("toCountryPhoneE164(value, 'SG')");
    }

    @Test
    void platformTenantAndH5PhoneEditorsUseTheSharedCountryPhoneField() throws Exception {
        assertUsesCountryPhoneField(
            Path.of("src", "pages", "PlatformProfilePage.vue"),
            "profileForm.phone",
            "$t('platform.profile.fields.phone')"
        );
        assertUsesCountryPhoneField(
            Path.of("src", "components", "platform", "PlatformTenantForm.vue"),
            "localForm.contactPhone",
            "$t('platform.tenants.form.phone')"
        );
        assertUsesCountryPhoneField(
            Path.of("src", "pages", "TenantAdminProfilePage.vue"),
            "form.contactPhone",
            "gt('generated.tenant-admin-profile.010')"
        );
        assertUsesCountryPhoneField(
            Path.of("src", "pages", "TenantAdminProfilePage.vue"),
            "shareForm.whatsappBusinessPhoneE164",
            "gt('generated.tenant-admin-profile.017')"
        );
        assertUsesCountryPhoneField(
            Path.of("src", "pages", "TenantAdminStaffFormPage.vue"),
            "tenantProfileForm.contactPhone",
            "gt('generated.tenant-admin-staff-form.010')"
        );
        assertUsesCountryPhoneField(
            Path.of("src", "pages", "TenantAdminStaffFormPage.vue"),
            "form.phone",
            "gt('generated.tenant-admin-staff-form.026')"
        );
        assertUsesCountryPhoneField(
            Path.of("src", "pages", "TenantAdminReservationSharePage.vue"),
            "form.whatsappBusinessPhoneE164",
            "gt('generated.tenant-admin-reservation-share.010')"
        );
        assertUsesCountryPhoneField(
            Path.of("src", "pages", "TenantAdminCustomersPage.vue"),
            "form.phoneE164",
            "gt('generated.tenant-admin-customers.011')"
        );

        String publicBooking = FrontendSourceSupport.readString(Path.of("src", "pages", "PublicBookingPage.vue"));
        assertThat(publicBooking)
            .contains("CountryPhoneField")
            .contains("v-model=\"bookingForm.phoneLocal\"")
            .contains("model-format=\"local\"")
            .contains(":label=\"gt('generated.public-booking.037')\"")
            .contains("phoneE164: toSingaporePhoneE164(bookingForm.phoneLocal)")
            .doesNotContain("public-booking-phone-field")
            .doesNotContain("sanitizeSingaporeLocalPhone")
            .doesNotContain("placeholder=\"+6591234567\"");
    }

    private static void assertUsesCountryPhoneField(Path path, String model, String label) throws Exception {
        String source = FrontendSourceSupport.readString(path);

        assertThat(source)
            .contains("CountryPhoneField")
            .contains("model-format=\"e164\"")
            .contains("v-model=\"" + model + "\"")
            .contains(":label=\"" + label + "\"")
            .doesNotContain("placeholder=\"+6591234567\"")
            .doesNotContain("placeholder=\"+6588880000\"");
    }
}
