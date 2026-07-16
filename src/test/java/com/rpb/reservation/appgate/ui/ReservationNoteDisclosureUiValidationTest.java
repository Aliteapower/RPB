package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReservationNoteDisclosureUiValidationTest {

    @Test
    void todayReservationCardDisclosesOnlyNonBlankNotes() throws Exception {
        String item = FrontendSourceSupport.readString(Path.of(
            "src", "components", "reservation-workbench", "ReservationTodayListItem.vue"
        ));

        assertThat(item)
            .contains("const displayNote = computed(() => props.item.note?.trim() ?? '')")
            .contains("const hasNote = computed(() => !!displayNote.value)")
            .contains("const isNoteExpanded = ref(false)")
            .contains("function toggleNote(): void")
            .contains("v-if=\"hasNote\"")
            .contains(":aria-expanded=\"isNoteExpanded\"")
            .contains(":aria-controls=\"noteRegionId\"")
            .contains("v-if=\"hasNote && isNoteExpanded\"")
            .contains("{{ displayNote }}")
            .contains("grid-column: 1 / -1;")
            .contains("white-space: pre-wrap;")
            .contains("overflow-wrap: anywhere;")
            .doesNotContain("v-html");
    }

    @Test
    void reservationNoteDisclosureHasChineseAndEnglishLabels() throws Exception {
        String zh = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "zh-CN.ts"));
        String en = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "en-SG.ts"));

        assertThat(zh)
            .contains("hasNote: '有备注'")
            .contains("hideNote: '收起备注'")
            .contains("noteLabel: '预约备注'");
        assertThat(en)
            .contains("hasNote: 'Has note'")
            .contains("hideNote: 'Hide note'")
            .contains("noteLabel: 'Reservation note'");
    }
}
