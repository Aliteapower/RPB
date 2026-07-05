package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffCommonGuestInputUiValidationTest {

    @Test
    void createReservationDialogUsesSharedGuestContactLookupInput() throws Exception {
        Path createDialogPath = Path.of("src", "components", "reservation-workbench", "CreateReservationDialog.vue");
        Path guestContactLookupPath = Path.of("src", "components", "staff", "StaffGuestContactLookup.vue");
        Path guestNamePath = Path.of("src", "components", "staff", "StaffGuestNameField.vue");
        Path singaporePhonePath = Path.of("src", "components", "staff", "StaffSingaporePhoneField.vue");
        Path contactHelperPath = Path.of("src", "components", "staff", "staffGuestContact.ts");

        assertThat(Files.exists(createDialogPath)).isTrue();
        assertThat(Files.exists(guestContactLookupPath)).isTrue();
        assertThat(Files.exists(guestNamePath)).isTrue();
        assertThat(Files.exists(singaporePhonePath)).isTrue();
        assertThat(Files.exists(contactHelperPath)).isTrue();

        String createDialogSource = FrontendSourceSupport.readString(createDialogPath);
        String guestContactLookupSource = FrontendSourceSupport.readString(guestContactLookupPath);
        String contactHelperSource = FrontendSourceSupport.readString(contactHelperPath);

        assertThat(createDialogSource)
            .contains("StaffGuestContactLookup")
            .contains("isValidSingaporeLocalPhone")
            .contains("toSingaporePhoneE164")
            .contains("customerId: ''")
            .contains("customerSalutation: ''")
            .contains("phoneLocal: ''")
            .contains("customerId: optionalValue(form.customerId)")
            .contains("customerNickname: optionalValue(form.customerSalutation)")
            .contains("phoneE164: toSingaporePhoneE164(form.phoneLocal)")
            .contains("v-model:customer-id=\"form.customerId\"")
            .contains("v-model:customer-name=\"form.customerName\"")
            .contains("v-model:salutation=\"form.customerSalutation\"")
            .contains("v-model:phone-local=\"form.phoneLocal\"")
            .doesNotContain("v-model=\"form.phoneE164\"")
            .doesNotContain("placeholder=\"+6591234567\"")
            .doesNotContain("const E164_PATTERN");

        assertThat(guestContactLookupSource)
            .contains("StaffGuestNameField")
            .contains("StaffSingaporePhoneField")
            .contains("lookupCustomerByPhone")
            .contains("isValidSingaporeLocalPhone")
            .contains("toSingaporePhoneE164");

        assertThat(contactHelperSource)
            .contains("SINGAPORE_PHONE_PREFIX = '+65'")
            .contains("LOCAL_SINGAPORE_PHONE_PATTERN = /^[0-9]{8}$/")
            .contains("function isValidSingaporeLocalPhone")
            .contains("function toSingaporePhoneE164")
            .contains("function sanitizeSingaporeLocalPhone");
    }

    @Test
    void sharedInputsExposeSalutationChoiceAndFixedSingaporePrefix() throws Exception {
        Path guestNamePath = Path.of("src", "components", "staff", "StaffGuestNameField.vue");
        Path singaporePhonePath = Path.of("src", "components", "staff", "StaffSingaporePhoneField.vue");
        Path zhPath = Path.of("src", "i18n", "locales", "zh-CN.ts");

        assertThat(Files.exists(guestNamePath)).isTrue();
        assertThat(Files.exists(singaporePhonePath)).isTrue();
        assertThat(Files.exists(zhPath)).isTrue();

        String guestNameSource = FrontendSourceSupport.readString(guestNamePath);
        String singaporePhoneSource = FrontendSourceSupport.readString(singaporePhonePath);
        String zhSource = FrontendSourceSupport.readString(zhPath);

        assertThat(guestNameSource)
            .contains("defineProps")
            .contains("defineEmits")
            .contains("update:customerName")
            .contains("update:salutation")
            .contains("staffControls.guest.salutations.mr")
            .contains("staffControls.guest.salutations.ms")
            .contains("aria-pressed")
            .contains("staffControls.guest.nameLabel");

        assertThat(singaporePhoneSource)
            .contains("defineProps")
            .contains("defineEmits")
            .contains("update:modelValue")
            .contains("sanitizeSingaporeLocalPhone")
            .contains("+65")
            .contains("maxlength=\"8\"")
            .contains("inputmode=\"numeric\"")
            .contains("pattern=\"[0-9]*\"")
            .contains("autocomplete=\"tel-national\"")
            .contains("staffControls.guest.phoneLabel");

        assertThat(zhSource)
            .contains("nameLabel: '顾客姓名'")
            .contains("phoneLabel: '手机号'")
            .contains("mr: '先生'")
            .contains("ms: '女士'");
    }

    @Test
    void staffGuestInputPatternIsSharedByExistingCustomerEntryPages() throws Exception {
        Path walkInQueuePath = Path.of("src", "pages", "WalkInQueuePage.vue");
        Path walkInDirectSeatingPath = Path.of("src", "pages", "WalkInDirectSeatingPage.vue");

        assertThat(Files.exists(walkInQueuePath)).isTrue();
        assertThat(Files.exists(walkInDirectSeatingPath)).isTrue();

        assertUsesSharedGuestInputPattern(FrontendSourceSupport.readString(walkInQueuePath));
        assertUsesSharedGuestInputPattern(FrontendSourceSupport.readString(walkInDirectSeatingPath));
    }

    private static void assertUsesSharedGuestInputPattern(String source) {
        assertThat(source)
            .contains("StaffGuestContactLookup")
            .contains("isValidSingaporeLocalPhone")
            .contains("toSingaporePhoneE164")
            .contains("customerId: ''")
            .contains("customerSalutation: ''")
            .contains("phoneLocal: ''")
            .contains("customerId: optionalValue(form.customerId)")
            .contains("customerNickname: optionalValue(form.customerSalutation)")
            .contains("phoneE164: toSingaporePhoneE164(form.phoneLocal)")
            .contains("v-model:customer-id=\"form.customerId\"")
            .contains("v-model:customer-name=\"form.customerName\"")
            .contains("v-model:salutation=\"form.customerSalutation\"")
            .contains("v-model:phone-local=\"form.phoneLocal\"")
            .doesNotContain("v-model=\"form.phoneE164\"")
            .doesNotContain("v-model=\"form.customerNickname\"")
            .doesNotContain("placeholder=\"+6591234567\"")
            .doesNotContain("const E164_PATTERN");
    }
}
