package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PublicBookingUiValidationTest {

    @Test
    void publicBookingPagePresentsReservationAsThreeStepWizard() throws Exception {
        String publicBookingPageSource = FrontendSourceSupport.readString(Path.of("src", "pages", "PublicBookingPage.vue"));

        assertThat(publicBookingPageSource)
            .contains("type BookingStep = 1 | 2 | 3")
            .contains("const currentStep = ref<BookingStep>(1)")
            .contains("const canProceedToAuth")
            .contains("const canProceedToContact")
            .contains("function goToAuthStep")
            .contains("function goToContactStep")
            .contains("function goBackToTimeStep")
            .contains("function goBackToAuthStep")
            .contains("<section v-if=\"currentStep === 1\" class=\"booking-panel\" aria-label=\"预约时间\">")
            .contains("<strong>选择日期、餐段与人数</strong>")
            .contains("<input v-model.number=\"bookingForm.partySize\" min=\"1\" max=\"20\" type=\"number\" />")
            .contains("下一步：登录")
            .contains("<section v-else-if=\"currentStep === 2\" class=\"booking-panel\" aria-label=\"登录\">")
            .contains("<strong>顾客登录</strong>")
            .contains("emailAuthEnabled")
            .contains("hasConfiguredLoginMethod")
            .contains("v-if=\"emailAuthEnabled\"")
            .contains("v-if=\"!hasConfiguredLoginMethod\"")
            .contains("门店尚未配置顾客登录方式，请联系门店")
            .contains("邮箱注册 / 登录")
            .contains("已注册邮箱通过验证码登录，未注册邮箱自动注册")
            .contains("Google 登录")
            .contains("Facebook 登录")
            .contains("下一步：填写手机号")
            .contains("<form v-else class=\"booking-panel\" aria-label=\"提交预约\" @submit.prevent=\"submitBooking\">")
            .contains("<strong>填写手机号并提交</strong>")
            .contains("<input v-model=\"bookingForm.phoneE164\" inputmode=\"tel\" placeholder=\"+6591234567\" />")
            .contains("!!bookingForm.phoneE164.trim()")
            .contains("<textarea v-model=\"bookingForm.note\" rows=\"3\"></textarea>")
            .doesNotContain("Gmail / Google");
    }

    @Test
    void publicBookingPageUsesReservationStyleDatePeriodAndTimePicker() throws Exception {
        String publicBookingPageSource = FrontendSourceSupport.readString(Path.of("src", "pages", "PublicBookingPage.vue"));

        assertThat(publicBookingPageSource)
            .contains("v-model=\"selectedDate\"")
            .contains(":min=\"minBookingDate\"")
            .contains(":max=\"maxBookingDate\"")
            .contains("type=\"date\"")
            .contains("const maxBookingDate = computed")
            .contains("settings.maxAdvanceDays")
            .contains("bookingDateWindowText")
            .contains("可预约至")
            .contains("booking-time-field")
            .contains("selectedPeriodKey")
            .contains("periodFilterOptions")
            .contains("booking-period-tabs")
            .contains("role=\"group\"")
            .contains("全部")
            .contains("displayName: group.displayName")
            .contains("filteredSlots")
            .contains("booking-time-slots")
            .contains("booking-time-card")
            .contains("slot.displayName")
            .contains("slot.localTime.slice(0, 5)")
            .doesNotContain("'午餐'")
            .doesNotContain("'晚餐'");
    }

    @Test
    void publicBookingPageDefaultsToLocalDateInsteadOfUtcDate() throws Exception {
        String publicBookingPageSource = FrontendSourceSupport.readString(Path.of("src", "pages", "PublicBookingPage.vue"));

        assertThat(publicBookingPageSource)
            .contains("const minBookingDate = todayDate()")
            .contains("const selectedDate = ref(minBookingDate)")
            .contains("const maxBookingDate = computed")
            .contains("function addDays")
            .contains("function clampBookingDate")
            .contains("getFullYear()")
            .contains("getMonth() + 1")
            .contains("getDate()")
            .contains("function formatDisplayDate")
            .doesNotContain("toISOString().slice(0, 10)");
    }

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
        Path publicBookingContextPath = Path.of(
            "src",
            "main",
            "java",
            "com",
            "rpb",
            "reservation",
            "publicbooking",
            "application",
            "PublicBookingContext.java"
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

        String publicBookingPageSource = FrontendSourceSupport.readString(publicBookingPagePath);
        String publicBookingTypeSource = FrontendSourceSupport.readString(publicBookingTypePath);
        String publicBookingControllerSource = FrontendSourceSupport.readString(publicBookingControllerPath);
        String publicBookingContextSource = FrontendSourceSupport.readString(publicBookingContextPath);
        String publicBookingPersistenceSource = FrontendSourceSupport.readString(publicBookingPersistencePath);

        assertThat(publicBookingPageSource)
            .contains("context.store.shareEmail")
            .contains(":href=\"`mailto:${context.store.shareEmail}`\"")
            .contains("发送邮件")
            .contains("store.whatsappBusinessPhoneE164")
            .contains("whatsappContactUrl")
            .contains("WhatsApp")
            .contains("context.store.googleMapUrl")
            .contains("context.store.shareContactPhone")
            .contains("emailAuthEnabled")
            .contains("门店尚未配置顾客登录方式，请联系门店");

        assertThat(publicBookingTypeSource)
            .contains("shareEmail: string | null")
            .contains("whatsappBusinessPhoneE164: string | null")
            .contains("emailAuthEnabled: boolean");

        assertThat(publicBookingControllerSource)
            .contains("String shareEmail")
            .contains("String whatsappBusinessPhoneE164")
            .contains("store.shareEmail()")
            .contains("store.whatsappBusinessPhoneE164()")
            .contains("boolean emailAuthEnabled")
            .contains("context.emailAuthEnabled()");

        assertThat(publicBookingContextSource)
            .contains("boolean emailAuthEnabled");

        assertThat(publicBookingPersistenceSource)
            .contains("share_email")
            .contains("whatsapp_business_phone_e164");
    }

    @Test
    void tenantAdminPublicBookingPageUsesProjectOperationPanelsForConfiguration() throws Exception {
        String tenantAdminPublicBookingPageSource = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminPublicBookingPage.vue"));
        String publicBookingTypeSource = FrontendSourceSupport.readString(Path.of("src", "types", "publicBooking.ts"));
        String publicBookingApiSource = FrontendSourceSupport.readString(Path.of("src", "api", "publicBookingApi.ts"));
        String tenantAdminApiSource = FrontendSourceSupport.readString(Path.of("src", "api", "tenantAdminApi.ts"));
        String tenantAdminPublicBookingControllerSource = FrontendSourceSupport.readString(Path.of(
            "src",
            "main",
            "java",
            "com",
            "rpb",
            "reservation",
            "publicbooking",
            "api",
            "TenantAdminPublicBookingController.java"
        ));

        assertThat(tenantAdminPublicBookingPageSource)
            .contains("import DownloadableQrCode from '../components/common/DownloadableQrCode.vue'")
            .contains("getTenantProfile")
            .contains("type PublicBookingPanel = 'settings' | 'email' | 'google' | 'facebook' | 'rules'")
            .contains("const activePanel = ref<PublicBookingPanel>('settings')")
            .contains("const tenantLogoUrl = ref('')")
            .contains("function openPanel(panel: PublicBookingPanel)")
            .contains("const publicBookingUrl = computed(() => `${window.location.origin}/book/${storeId.value}`)")
            .contains("const publicBookingQrFileName = computed(() => `public-booking-${storeId.value}.png`)")
            .contains("function fallbackCopyText(text: string): boolean")
            .contains("document.execCommand('copy')")
            .contains("复制公网预约入口")
            .contains("公网预约入口已复制")
            .contains("公网预约入口复制失败")
            .contains("getTenantProfile(storeId.value).catch(() => null)")
            .contains("tenantLogoUrl.value = tenantProfileResponse?.profile.logoMediaUrl || ''")
            .contains("<DownloadableQrCode")
            .contains(":value=\"publicBookingUrl\"")
            .contains(":logo-url=\"tenantLogoUrl\"")
            .contains(":file-name=\"publicBookingQrFileName\"")
            .contains("download-label=\"下载二维码\"")
            .contains("预约入口二维码")
            .contains("defaultQuotaPercent: 100")
            .contains("defaultQuotaPercent: form.defaultQuotaMode === 'percentage' ? Number(form.defaultQuotaPercent ?? 100) : null")
            .doesNotContain("defaultQuotaPercent: 20")
            .doesNotContain("form.defaultQuotaPercent ?? 20")
            .contains("getStoreReservationMealPeriods")
            .contains("const mealPeriods = ref<ReservationMealPeriod[]>([])")
            .contains("const activeMealPeriods = computed")
            .contains("mealPeriodResponse.effectivePeriods")
            .contains("getTenantAdminPublicBookingAvailabilityRules")
            .contains("updateTenantAdminPublicBookingAvailabilityRule")
            .contains("deleteTenantAdminPublicBookingAvailabilityRule")
            .contains("const availabilityRules = ref<TenantAdminPublicBookingAvailabilityRule[]>([])")
            .contains("ruleType: 'weekly'")
            .contains("const weekdayOptions")
            .contains("const selectedWeekdays = ref<number[]>([])")
            .contains("hydrateWeeklySelectionFromSavedRules()")
            .contains("const firstWeeklyRule = availabilityRules.value.find")
            .contains("Object.assign(ruleForm, {")
            .contains("quotaMode: firstWeeklyRule.quotaMode")
            .contains("tableCount: firstWeeklyRule.tableCount")
            .contains("syncWeeklyAvailabilityRules")
            .contains("staleWeeklyRules")
            .contains("sameWeeklyRuleEditGroup")
            .contains("deleteTenantAdminPublicBookingAvailabilityRule(storeId.value, rule.id)")
            .contains("selectedWeekdays.value.length === 0")
            .contains("公网预约规则已清空")
            .doesNotContain("const selectedWeekdays = ref<number[]>([1])")
            .doesNotContain("selectedWeekdays.value[0] || 1")
            .doesNotContain("|| '周一'")
            .doesNotContain("至少选择一个星期")
            .doesNotContain("weekdayValidationAttempted")
            .doesNotContain("showWeekdayRequired")
            .contains("saveWeeklyAvailabilityRules")
            .contains("Promise.all(selectedWeekdays.value.map")
            .contains("settings-operation-list")
            .contains("aria-label=\"公网预约项目操作\"")
            .contains("项目操作")
            .contains("aria-expanded=\"activePanel === 'settings'\"")
            .contains("aria-expanded=\"activePanel === 'email'\"")
            .contains("aria-expanded=\"activePanel === 'google'\"")
            .contains("aria-expanded=\"activePanel === 'facebook'\"")
            .contains("aria-expanded=\"activePanel === 'rules'\"")
            .contains("v-if=\"activePanel === 'settings'\"")
            .contains("v-else-if=\"activePanel === 'email'\"")
            .contains("v-else-if=\"activePanel === 'google'\"")
            .contains("v-else-if=\"activePanel === 'facebook'\"")
            .contains("v-else-if=\"activePanel === 'rules'\"")
            .contains("emailLoginReady")
            .contains("googleLoginReady")
            .contains("facebookLoginReady")
            .contains("canSaveEmail")
            .contains("canSaveGoogle")
            .contains("canSaveFacebook")
            .contains("status-chip")
            .contains("可用")
            .contains("待配置")
            .contains("邮箱注册 / 登录邮件服务")
            .contains("用于公网预约邮箱验证码，已注册邮箱登录，未注册邮箱注册")
            .contains("要求顾客登录")
            .contains("启用邮箱注册 / 登录")
            .contains("emailSmtpCredentialComplete")
            .contains("启用后需填写发件邮箱、SMTP Host、SMTP Port；填写 SMTP 用户名时需保存 SMTP 密钥")
            .contains("启用后需填写 Client ID")
            .contains("启用后需填写 App ID，并保存 App Secret")
            .contains("保存邮箱登录服务")
            .contains("SMTP Host")
            .contains("公网预约规则")
            .contains("用于设置公网预约是否开放以及可预约配额；每周固定适合周一店休，指定日期例外适合节假日或临时调整")
            .contains("规则类型")
            .contains("每周固定")
            .contains("指定日期例外")
            .contains("v-if=\"ruleForm.ruleType === 'weekly'\"")
            .contains("v-if=\"ruleForm.ruleType === 'date_exception'\"")
            .contains("星期")
            .contains("class=\"weekday-checkbox-grid\"")
            .contains("v-model=\"selectedWeekdays\"")
            .contains("type=\"checkbox\"")
            .contains("周一")
            .contains("周日")
            .contains("选择餐段")
            .contains("<select v-model=\"ruleForm.periodKey\">")
            .contains("<option value=\"\">全部餐段</option>")
            .contains("v-for=\"period in activeMealPeriods\"")
            .contains(":value=\"period.periodKey\"")
            .contains("{{ mealPeriodOptionLabel(period) }}")
            .contains("保存规则")
            .contains("已保存规则")
            .contains("暂无可用预约餐段")
            .contains("Google 登录")
            .contains("Facebook 登录")
            .contains("公网预约规则")
            .doesNotContain("login-method-summary")
            .doesNotContain("顾客登录方式总览")
            .doesNotContain("日期餐段覆盖")
            .doesNotContain("<h2 class=\"form-panel__wide\">邮件验证码</h2>")
            .doesNotContain("<select v-model.number=\"ruleForm.dayOfWeek\">")
            .doesNotContain("<span>餐段 Key</span>")
            .doesNotContain("placeholder=\"dinner\"");

        assertThat(publicBookingTypeSource)
            .contains("export type TenantAdminPublicBookingRuleType = 'weekly' | 'date_exception'")
            .contains("export interface TenantAdminPublicBookingAvailabilityRule")
            .contains("ruleType: TenantAdminPublicBookingRuleType")
            .contains("businessDate: string | null")
            .contains("dayOfWeek: number | null")
            .contains("periodKey: string | null")
            .contains("quotaMode: 'percentage' | 'table_count' | 'guest_count' | 'closed'")
            .contains("export interface TenantAdminPublicBookingAvailabilityRulesResponse")
            .contains("rules: TenantAdminPublicBookingAvailabilityRule[]")
            .contains("export interface TenantAdminPublicBookingAvailabilityRuleMutation");

        assertThat(publicBookingApiSource)
            .contains("getTenantAdminPublicBookingAvailabilityRules")
            .contains("updateTenantAdminPublicBookingAvailabilityRule")
            .contains("deleteTenantAdminPublicBookingAvailabilityRule")
            .contains("tenantAdminPublicBookingEndpoint(storeId, '/availability-rules')")
            .contains("method: 'GET'")
            .contains("method: 'PUT'")
            .contains("method: 'DELETE'");

        assertThat(tenantAdminApiSource)
            .contains("export async function getTenantProfile")
            .contains("logoMediaUrl: string | null");

        assertThat(tenantAdminPublicBookingControllerSource)
            .contains("@GetMapping(\"/availability-rules\")")
            .contains("@PutMapping(\"/availability-rules\")")
            .contains("@DeleteMapping(\"/availability-rules/{ruleId}\")")
            .contains("deleteAvailabilityRule")
            .contains("AvailabilityRuleRequest")
            .contains("AvailabilityRulesResponse")
            .contains("AvailabilityRuleResponse");
    }

    @Test
    void downloadableQrCodeComponentIsReusableAndSupportsLogoDownload() throws Exception {
        String qrComponentSource = FrontendSourceSupport.readString(Path.of("src", "components", "common", "DownloadableQrCode.vue"));
        String qrRendererSource = FrontendSourceSupport.readString(Path.of("src", "utils", "qrCodeRenderer.ts"));
        String packageJsonSource = FrontendSourceSupport.readString(Path.of("package.json"));

        assertThat(qrComponentSource)
            .contains("defineProps")
            .contains("value: string")
            .contains("logoUrl?: string | null")
            .contains("fileName?: string")
            .contains("downloadLabel?: string")
            .contains("renderQrCodeToCanvas")
            .contains("safeQrDownloadFileName")
            .contains("downloadQrCodePng")
            .contains("canvasRef")
            .contains("<canvas")
            .contains("components.downloadableQrCode.download")
            .contains("components.downloadableQrCode.errors.renderFailed");

        assertThat(qrRendererSource)
            .contains("import QRCode from 'qrcode'")
            .contains("export async function renderQrCodeToCanvas")
            .contains("QRCode.toCanvas")
            .contains("errorCorrectionLevel: options.errorCorrectionLevel ?? 'H'")
            .contains("drawLogoOnCanvas")
            .contains("loadImage")
            .contains("canvas.toDataURL('image/png')")
            .contains("export function safeQrDownloadFileName");

        assertThat(packageJsonSource)
            .contains("\"qrcode\"")
            .contains("\"@types/qrcode\"");
    }
}
