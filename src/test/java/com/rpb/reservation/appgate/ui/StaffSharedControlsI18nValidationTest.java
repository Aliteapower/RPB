package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaffSharedControlsI18nValidationTest {

    @Test
    void staffSharedControlsResolveVisibleCopyThroughI18nKeys() throws Exception {
        List<Path> migratedPaths = List.of(
            Path.of("src", "components", "staff", "StaffBusinessDateSwitcher.vue"),
            Path.of("src", "components", "staff", "StaffGuestContactLookup.vue"),
            Path.of("src", "components", "staff", "StaffGuestNameField.vue"),
            Path.of("src", "components", "staff", "StaffSingaporePhoneField.vue"),
            Path.of("src", "components", "staff", "StaffTimeWheelPicker.vue"),
            Path.of("src", "components", "staff-home", "StaffHomeWorkflowStrip.vue"),
            Path.of("src", "components", "staff-table", "TableResourcePicker.vue")
        );

        StringBuilder source = new StringBuilder();
        for (Path path : migratedPaths) {
            assertThat(path).exists();
            source.append(FrontendSourceSupport.readString(path)).append('\n');
        }

        assertThat(source)
            .contains("useI18n")
            .contains("staffControls.businessDate.today")
            .contains("staffControls.guest.nameLabel")
            .contains("staffControls.timePicker.hour")
            .contains("staffControls.workflow.walkInSeating")
            .contains("staffControls.tablePicker.title")
            .doesNotContain("业务日期")
            .doesNotContain("顾客姓名")
            .doesNotContain("手机号")
            .doesNotContain("24小时制时间选择")
            .doesNotContain("门店流转提示")
            .doesNotContain("桌号及分组")
            .doesNotContain("当前分区暂无可用桌台")
            .doesNotContain("临时组合")
            .doesNotContain("预约指定")
            .doesNotContain("需使用预约指定桌台");
    }

    @Test
    void staffSharedControlLocaleDictionariesContainChineseAndEnglishDefaults() throws Exception {
        String zhSource = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "zh-CN.ts"));
        String enSource = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "en-SG.ts"));

        assertThat(zhSource)
            .contains("staffControls")
            .contains("nameLabel: '顾客姓名'")
            .contains("workflow")
            .contains("tablePicker")
            .contains("requiredResource: '预约指定'");
        assertThat(enSource)
            .contains("staffControls")
            .contains("nameLabel: 'Guest name'")
            .contains("workflow")
            .contains("tablePicker")
            .contains("requiredResource: 'Assigned resource'");
    }
}
