package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PublicBookingUiValidationTest {

    @Test
    void publicBookingPageShowsTenantMaintainedContactChannels() throws Exception {
        Path publicBookingPagePath = Path.of("src", "pages", "PublicBookingPage.vue");
        Path publicBookingTypePath = Path.of("src", "types", "publicBooking.ts");
        Path publicBookingControllerPath = Path.of(
            "src",
            "main",
            "java",
            "com",
            "rpb",
            "reservation",
            "publicbooking",
            "api",
            "PublicBookingController.java"
        );
        Path publicBookingPersistencePath = Path.of(
            "src",
            "main",
            "java",
            "com",
            "rpb",
            "reservation",
            "publicbooking",
            "persistence",
            "PublicBookingPersistenceAdapter.java"
        );

        String publicBookingPageSource = Files.readString(publicBookingPagePath);
        String publicBookingTypeSource = Files.readString(publicBookingTypePath);
        String publicBookingControllerSource = Files.readString(publicBookingControllerPath);
        String publicBookingPersistenceSource = Files.readString(publicBookingPersistencePath);

        assertThat(publicBookingPageSource)
            .contains("context.store.shareEmail")
            .contains(":href=\"`mailto:${context.store.shareEmail}`\"")
            .contains("发送邮件")
            .contains("store.whatsappBusinessPhoneE164")
            .contains("whatsappContactUrl")
            .contains("WhatsApp")
            .contains("context.store.googleMapUrl")
            .contains("context.store.shareContactPhone");

        assertThat(publicBookingTypeSource)
            .contains("shareEmail: string | null")
            .contains("whatsappBusinessPhoneE164: string | null");

        assertThat(publicBookingControllerSource)
            .contains("String shareEmail")
            .contains("String whatsappBusinessPhoneE164")
            .contains("store.shareEmail()")
            .contains("store.whatsappBusinessPhoneE164()");

        assertThat(publicBookingPersistenceSource)
            .contains("share_email")
            .contains("whatsapp_business_phone_e164");
    }
}
