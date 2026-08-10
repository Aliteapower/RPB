package com.rpb.reservation.appgate.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PayNowPaymentUiAcceptanceValidationTest {

    @Test
    void quickPayCreationUsesPaymentReferenceGenerator() throws Exception {
        assertThat(Files.readString(Path.of("src/main/java/com/rpb/reservation/payment/application/PaymentIntentService.java")))
            .contains("PaymentReferenceGenerator.generate");
    }

    @Test
    void tenantAdminStaffQuickPayAndDisplayEntriesAreWired() throws Exception {
        String router = FrontendSourceSupport.readString(Path.of("src", "router", "index.ts"));
        String tenantNav = FrontendSourceSupport.readString(Path.of("src", "components", "tenant-admin", "TenantAdminNav.vue"));
        String staffHome = FrontendSourceSupport.readString(Path.of("src", "pages", "StoreStaffHomePage.vue"));
        String bottomNav = FrontendSourceSupport.readString(Path.of("src", "components", "staff", "staffBottomNavItems.ts"));
        String bottomNavComponent = FrontendSourceSupport.readString(Path.of("src", "components", "staff", "StaffBottomNav.vue"));
        String quickPayPopup = FrontendSourceSupport.readString(Path.of("src", "utils", "paymentQuickPayPopup.ts"));
        String zh = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "zh-CN.ts"));
        String en = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "en-SG.ts"));

        assertThat(router)
            .contains("path: '/stores/:storeId/admin/payment/settings'")
            .contains("name: 'tenant-admin-payment-settings'")
            .contains("path: '/stores/:storeId/admin/payment/records'")
            .contains("name: 'tenant-admin-payment-records'")
            .contains("path: '/stores/:storeId/admin/payment/proof-templates'")
            .contains("name: 'tenant-admin-payment-proof-templates'")
            .contains("path: '/stores/:storeId/admin/payment/i18n-catalog'")
            .contains("name: 'tenant-admin-payment-i18n-catalog'")
            .contains("path: '/stores/:storeId/payments'")
            .contains("name: 'payment-quick-pay'")
            .contains("path: '/stores/:storeId/payments/proof-review'")
            .contains("name: 'payment-proof-review'")
            .contains("path: '/stores/:storeId/payments/present/:terminalCode'")
            .contains("name: 'payment-present'")
            .contains("path: '/stores/:storeId/payments/display/:sessionNo'")
            .contains("name: 'payment-display'");

        assertThat(tenantNav)
            .contains("useStoreVisibleApps")
            .contains("hasPaymentProductLine")
            .contains("hasReservationQueueProductLine")
            .contains("/admin/payment/settings")
            .contains("/admin/payment/records")
            .contains("/admin/payment/proof-templates")
            .contains("/admin/payment/i18n-catalog")
            .contains("/admin/reservation-queue/i18n-catalog")
            .contains("nav.tenant.paymentProductLine")
            .contains("nav.tenant.reservationQueueProductLine")
            .contains("nav.tenant.paymentSettings")
            .contains("nav.tenant.paymentRecords")
            .contains("nav.tenant.paymentProofTemplates")
            .contains("nav.tenant.paymentI18nCatalog")
            .contains("nav.tenant.reservationQueueI18nCatalog");
        assertThat(staffHome)
            .contains("useStoreVisibleApps")
            .contains("paymentEntry")
            .contains("hasReservationQueue")
            .contains("v-if=\"hasReservationQueue\"")
            .contains("staffHome.hints.paymentOnly")
            .contains("openMode: 'popup'")
            .contains("openQuickPaymentPopup(href)")
            .doesNotContain("router.replace(paymentQuickPayRoute.value)")
            .contains("payment.intent.create")
            .contains("staffHome.actions.quickPay.label")
            .contains("name: 'payment-quick-pay'");
        assertThat(bottomNav)
            .contains("'payment'")
            .contains("appKey: 'payment'")
            .contains("appKey: 'reservation_queue'")
            .contains("nav.staff.payment")
            .contains("payment-quick-pay")
            .contains("openMode: 'popup'");
        assertThat(bottomNavComponent)
            .contains("useStoreVisibleApps")
            .contains("hasVisibleApp(item.appKey)")
            .contains("openQuickPaymentPopup(href)")
            .contains(":target=\"item.openMode === 'popup' ? '_blank' : undefined\"")
            .contains("--staff-nav-items");
        assertThat(quickPayPopup)
            .contains("QUICK_PAYMENT_POPUP_TARGET")
            .contains("QUICK_PAYMENT_POPUP_FEATURES")
            .contains("popup=yes")
            .contains("openQuickPaymentPopup");
        assertThat(zh)
            .contains("paymentProductLine: '收款 / PayNow'")
            .contains("paymentSettings: '基础设置'")
            .contains("paymentRecords: 'Quick Payment Records'")
            .contains("paymentI18nCatalog: 'PayNow 国际化字典'")
            .contains("reservationQueueProductLine: '预约排队叫号'")
            .contains("reservationQueueI18nCatalog: '预约排队国际化字典'")
            .contains("payment: '收款'")
            .contains("paymentOnly")
            .contains("quickPay");
        assertThat(en)
            .contains("paymentProductLine: 'Pay / PayNow'")
            .contains("paymentSettings: 'Settings'")
            .contains("paymentRecords: 'Quick Payment Records'")
            .contains("paymentI18nCatalog: 'PayNow I18n'")
            .contains("reservationQueueProductLine: 'Reservation Queue'")
            .contains("reservationQueueI18nCatalog: 'Reservation Queue I18n'")
            .contains("payment: 'Pay'")
            .contains("paymentOnly")
            .contains("quickPay");
    }

    @Test
    void payNowPagesUseRpbNativePaymentApiAndQrComponent() throws Exception {
        String api = FrontendSourceSupport.readString(Path.of("src", "api", "paymentApi.ts"));
        String types = FrontendSourceSupport.readString(Path.of("src", "types", "payment.ts"));
        String settings = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminPaymentSettingsPage.vue"));
        String proofTemplates = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminPaymentProofTemplatesPage.vue"));
        String records = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminPaymentRecordsPage.vue"));
        String quickPay = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentQuickPayPage.vue"));
        String proofReview = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentProofReviewPage.vue"));
        String display = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentDisplayPage.vue"));
        String present = FrontendSourceSupport.readString(Path.of("src", "pages", "PaymentPresentPage.vue"));
        String presentBridge = FrontendSourceSupport.readString(Path.of("src", "utils", "paymentPresentBridge.ts"));
        String generatedZh = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "generated-zh-CN.ts"));
        String generatedEn = FrontendSourceSupport.readString(Path.of("src", "i18n", "locales", "generated-en-SG.ts"));
        String staffRepository = FrontendSourceSupport.readString(Path.of("src", "main", "java", "com", "rpb", "reservation", "tenantadmin", "persistence", "TenantAdminStaffRepository.java"));

        assertThat(api)
            .contains("/tenant-admin/payment/profile")
            .contains("/payments/intents")
            .contains("/payments/business-day")
            .contains("/payments/proof-review")
            .contains("/tenant-admin/payment/proof-templates")
            .contains("/quick-pay-records")
            .contains("manualConfirmQuickPay")
            .contains("/manual-confirm")
            .contains("/sessions/")
            .contains("getPaymentProofCandidates")
            .contains("scanPaymentProof")
            .contains("getPaymentProofTemplates")
            .contains("createPaymentProofTemplate")
            .contains("updatePaymentProofTemplate")
            .contains("testScanPaymentProofTemplate")
            .contains("requestMultipart")
            .contains("getPaymentBusinessDay")
            .contains("openPaymentBusinessDay")
            .contains("endPaymentBusinessDay")
            .contains("`${businessDayEndpoint(storeId)}/end-day`")
            .contains("credentials: 'include'");
        assertThat(types)
            .contains("PaymentProfileMutation")
            .contains("PaymentIntentCreateRequest")
            .contains("PaymentManualConfirmRequest")
            .contains("PaymentManualConfirmResponse")
            .contains("PaymentBusinessDayResponse")
            .contains("PaymentBusinessDayStatus")
            .contains("PaymentProofScanResponse")
            .contains("PaymentProofCandidate")
            .contains("PaymentProofTemplate")
            .contains("PaymentProofTemplateTestScanResponse")
            .contains("PaymentSession");
        assertThat(settings)
            .contains("getPaymentProfile")
            .contains("updatePaymentProfile")
            .contains("PAYMENT_PROFILE_NOT_FOUND")
            .contains("formatAppGateErrorMessage")
            .contains("appgate.permission_denied")
            .contains("TenantAdminNav")
            .contains("tenant-admin-payment-records")
            .contains("tenant-admin-payment-proof-templates")
            .doesNotContain("sidecar")
            .doesNotContain("payment_runtime");
        assertThat(proofTemplates)
            .contains("getPaymentProofTemplates")
            .contains("createPaymentProofTemplate")
            .contains("updatePaymentProofTemplate")
            .contains("getPaymentProofTemplateContributions")
            .contains("submitPaymentProofTemplateContribution")
            .contains("suggestPaymentProofTemplateRule")
            .contains("!editable || testing")
            .contains("testing.value || !editable.value")
            .contains("const templateId = selected.value?.id")
            .contains("selected.value?.id !== templateId || !editable.value")
            .contains("回单样式库")
            .contains("已引用平台模板")
            .contains("租户自定义")
            .contains("提交给平台")
            .contains("tenant-admin-payment-settings")
            .doesNotContain("sidecar")
            .doesNotContain("payment_runtime");
        assertThat(records)
            .contains("getQuickPayRecords")
            .contains("Quick Payment Records")
            .contains("formatAppGateErrorMessage")
            .contains("appgate.permission_denied")
            .contains("TenantAdminNav")
            .contains("tenant-admin-payment-settings")
            .doesNotContain("sidecar")
            .doesNotContain("payment_runtime");
        assertThat(quickPay)
            .contains("createPaymentIntent")
            .contains("getPaymentBusinessDay")
            .contains("openPaymentBusinessDay")
            .contains("endPaymentBusinessDay")
            .contains("businessDayOpen")
            .contains("openBusinessDayToday")
            .contains("endBusinessDay")
            .contains("response.session.businessDate")
            .contains("showOpenTodayButton")
            .contains("const showOpenTodayButton = computed(() => !businessDayOpen.value)")
            .contains("showEndDayButton")
            .contains("paymentOptionsOpen")
            .contains("payment-options-toggle")
            .contains("payment-options-panel")
            .doesNotContain("displayedBusinessDate.value !== currentBusinessDate.value")
            .contains("sourceType: 'quick_pay'")
            .contains("appendAmountToken")
            .contains("backspaceAmount")
            .contains("clearAmount")
            .contains("savePresetEditor")
            .contains("openPresentSettingsEditor")
            .contains("savePresentSettingsEditor")
            .contains("presentSettingsRecentExpiredHoldSeconds")
            .contains("recentExpiredHoldSeconds")
            .contains("manualConfirmQuickPay")
            .contains("confirmRecentPayment")
            .contains("manuallyConfirmingSessionNo")
            .contains("item.sessionNo === manuallyConfirmingSessionNo")
            .contains("item.paymentReference")
            .contains("recent-reference")
            .contains("confirmPaymentPresentPayment")
            .contains("isPaymentPresentRecentPending")
            .contains("openPresentWindow")
            .contains("publishPaymentPresentPayload")
            .contains("publishPaymentPresentSettings")
            .contains("readPaymentPresentPayloads")
            .contains("readPaymentPresentSettings")
            .contains("isPaymentPresentPayloadActive")
            .contains("activePresentPayloadCount")
            .contains("presentCapacityFull")
            .contains("noticeText.value = gt('generated.payment-quick-pay.055')")
            .contains("Number(presentSettingsRecentExpiredHoldSeconds.value)")
            .contains("const presentMaxPaymentOptions = [1, 2, 3, 4, 5, 6] as const")
            .contains("MAX_PRESENT_PAYMENTS")
            .contains("formatAppGateErrorMessage")
            .contains("appgate.permission_denied")
            .contains("payment-proof-review")
            .contains("openProofReviewWindow")
            .contains("rpb-paynow-proof-review")
            .contains("popup=yes,width=520,height=900")
            .contains("calc(112px + env(safe-area-inset-bottom))")
            .contains("max-height: calc(100dvh - 150px)")
            .contains("active-tab=\"payment\"");
        assertThat(proofReview)
            .contains("getPaymentProofCandidates")
            .contains("scanPaymentProof")
            .contains("refreshCandidatesOnFocus")
            .contains("window.addEventListener('focus', refreshCandidatesOnFocus)")
            .contains("window.removeEventListener('focus', refreshCandidatesOnFocus)")
            .contains("navigator.mediaDevices.getUserMedia")
            .contains("startScanner")
            .contains("captureFrameBlob")
            .contains("canvas.toBlob")
            .contains("setInterval")
            .contains("successAutoAdvanceTimer !== undefined")
            .contains("photoInputRef")
            .contains("albumInputRef")
            .contains("openPhotoCapture")
            .contains("openAlbumPicker")
            .contains("generated.payment-proof-review.006")
            .contains("accept=\"image/png,image/jpeg,image/webp\"")
            .contains("capture=\"environment\"")
            .contains("successfulDisplayNumber")
            .contains("speakPaymentSuccess")
            .contains("SpeechSynthesisUtterance")
            .contains("confirmPaymentPresentPayment")
            .contains("confirmPaymentPresentPaymentByReference")
            .contains("resolveSuccessfulCandidate")
            .contains("resolveSuccessfulAmount")
            .contains("return formatSpokenAmount(candidate?.amount || result.ocr?.extractedAmount || null)")
            .doesNotContain("result.expectedAmount || result.ocr?.extractedAmount || candidate?.amount")
            .contains("formatSpokenAmount")
            .contains("gt('generated.payment-proof-review.040', { amount })")
            .contains("if (!fromCamera) {\n        stopScanner()\n      }")
            .contains("scheduleSuccessAutoAdvance(fromCamera)")
            .contains("clearSuccessAutoAdvance")
            .contains("prepareNextScan")
            .contains("generated.payment-proof-review.037")
            .contains("generated.payment-proof-review.038")
            .contains("generated.payment-proof-review.040")
            .contains("generated.payment-proof-review.041")
            .contains("auto_confirmed")
            .contains("already_confirmed")
            .contains("needs_review")
            .contains("generated.payment-proof-review.028")
            .contains("active-tab=\"payment\"")
            .doesNotContain("fromCamera && result.outcome === 'needs_review'")
            .doesNotContain("sidecar")
            .doesNotContain("payment_runtime");
        assertThat(staffRepository)
            .contains("\"payment.intent.view\"")
            .contains("\"payment.intent.create\"")
            .contains("\"payment.proof.review\"");
        assertThat(display)
            .contains("getPaymentSession")
            .contains("DownloadableQrCode")
            .contains("active-tab=\"payment\"");
        assertThat(present)
            .contains("activePayloads")
            .contains("subscribePaymentPresentPayloads")
            .contains("subscribePaymentPresentSettings")
            .contains("payload.paymentReference")
            .doesNotContain("<strong>{{ payload.intentNo }}</strong>")
            .contains("PAYMENT_PRESENT_TTL_SECONDS")
            .contains("readPaymentPresentPayloads")
            .contains("readPaymentPresentRecent")
            .contains("Waiting for new payment")
            .contains("clearActivePayload")
            .contains("isPendingRecent")
            .contains("isPaymentPresentRecentPending")
            .contains("<small v-if=\"isPendingRecent(item)\">{{ recentSecondsText(item) }}</small>")
            .contains("present-grid")
            .contains("DownloadableQrCode")
            .contains(":show-download=\"false\"")
            .doesNotContain(":download-label=\"gt('generated.payment-present.009')\"");
        assertThat(presentBridge)
            .contains("BroadcastChannel")
            .contains("localStorage")
            .contains("rpb-payment-present")
            .contains("PaymentPresentSettings")
            .contains("paymentReference: response.intent.paymentReference")
            .contains("paymentReference: String(source.paymentReference || source.intentNo || '')")
            .contains("export const MAX_PRESENT_PAYMENTS = 6")
            .contains("export const DEFAULT_RECENT_EXPIRED_HOLD_SECONDS = 20")
            .contains("export type PresentMaxPayments = 1 | 2 | 3 | 4 | 5 | 6")
            .contains("recentExpiredHoldSeconds: DEFAULT_RECENT_EXPIRED_HOLD_SECONDS")
            .contains("recentVisibleUntilMs")
            .contains("prunePaymentPresentRecent")
            .contains("value === 5 || value === 6")
            .contains("readPaymentPresentPayloads")
            .contains("readPaymentPresentSettings")
            .contains("savePaymentPresentSettings")
            .contains("publishPaymentPresentSettings")
            .contains("confirmPaymentPresentPayment")
            .contains("confirmPaymentPresentPaymentByReference")
            .contains("const next = recent.filter(item => item.sessionNo !== sessionNo)")
            .contains("normalizePaymentReference(item.paymentReference)")
            .contains("isPaymentPresentRecentPending")
            .contains(".filter(isPaymentPresentRecentPending)")
            .contains("return isPaymentPresentRecentPending(payload) && nowMs - payload.createdAtMs < PAYMENT_PRESENT_TTL_SECONDS * 1000")
            .contains("120");
        assertThat(generatedZh)
            .contains("\"generated.payment-quick-pay.055\": \"等顾客支付\"")
            .contains("\"generated.payment-quick-pay.056\": \"过期后保留秒数\"")
            .contains("\"generated.payment-quick-pay.057\": \"回单校验\"")
            .contains("\"generated.payment-quick-pay.058\": \"收款设置\"")
            .contains("\"generated.payment-quick-pay.059\": \"展开\"")
            .contains("\"generated.payment-quick-pay.060\": \"收起\"")
            .contains("\"generated.payment-quick-pay.061\": \"确认\"")
            .contains("\"generated.payment-quick-pay.062\": \"确认已收到这笔 PayNow 转账并关单？\"")
            .contains("\"generated.payment-quick-pay.063\": \"已手工确认收款：{displayNumber}\"")
            .contains("\"generated.tenant-admin-payment-proof-templates.001\": \"回单样式库\"")
            .contains("\"generated.tenant-admin-payment-proof-templates.006\": \"平台种子\"")
            .contains("\"generated.tenant-admin-payment-proof-templates.007\": \"租户自定义\"")
            .contains("\"generated.payment-proof-review.003\": \"回单校验\"")
            .contains("\"generated.payment-proof-review.006\": \"回单校验\"");
        assertThat(generatedZh)
            .contains("\"generated.payment-proof-review.018\": \"请人工确认\"")
            .contains("\"generated.payment-proof-review.039\": \"已确认\"")
            .contains("\"generated.payment-proof-review.040\": \"收款 {amount} 元成功\"")
            .contains("\"generated.payment-proof-review.041\": \"收款成功\"")
            .contains("\"generated.payment-proof-review.028\": \"关闭并扫下一笔\"");
        assertThat(generatedEn)
            .contains("\"generated.payment-quick-pay.055\": \"Waiting for customer payment\"")
            .contains("\"generated.payment-quick-pay.056\": \"Keep after expiry (seconds)\"")
            .contains("\"generated.payment-quick-pay.058\": \"Payment settings\"")
            .contains("\"generated.payment-quick-pay.059\": \"Expand\"")
            .contains("\"generated.payment-quick-pay.060\": \"Collapse\"")
            .contains("\"generated.payment-quick-pay.061\": \"Confirm\"")
            .contains("\"generated.payment-quick-pay.062\": \"Confirm this PayNow transfer and close it?\"")
            .contains("\"generated.payment-quick-pay.063\": \"Payment manually confirmed: {displayNumber}\"")
            .contains("\"generated.tenant-admin-payment-proof-templates.001\": \"Proof template library\"")
            .contains("\"generated.tenant-admin-payment-proof-templates.006\": \"Platform seed\"")
            .contains("\"generated.tenant-admin-payment-proof-templates.007\": \"Tenant custom\"")
            .contains("\"generated.payment-proof-review.006\": \"Payment Proof Review\"");
        assertThat(generatedEn)
            .contains("\"generated.payment-proof-review.039\": \"Already confirmed\"")
            .contains("\"generated.payment-proof-review.018\": \"Manual confirmation required\"")
            .contains("\"generated.payment-proof-review.040\": \"Payment {amount} confirmed\"")
            .contains("\"generated.payment-proof-review.041\": \"Payment confirmed\"")
            .contains("\"generated.payment-proof-review.028\": \"Close and scan next\"");
    }

    @Test
    void payNowProofTemplatePlatformLibraryApiAndTypesAreWired() throws Exception {
        String api = FrontendSourceSupport.readString(Path.of("src", "api", "paymentApi.ts"));
        String types = FrontendSourceSupport.readString(Path.of("src", "types", "payment.ts"));

        assertThat(api)
            .contains("getPlatformPaymentProofTemplates")
            .contains("createPlatformPaymentProofTemplate")
            .contains("updatePlatformPaymentProofTemplate")
            .contains("suggestPlatformPaymentProofTemplateRule")
            .contains("getPlatformPaymentProofTemplateContributions")
            .contains("acceptPlatformPaymentProofTemplateContribution")
            .contains("rejectPlatformPaymentProofTemplateContribution")
            .contains("getPaymentProofTemplateContributions")
            .contains("submitPaymentProofTemplateContribution")
            .contains("suggestPaymentProofTemplateRule");
        assertThat(types)
            .contains("PaymentProofTemplateContribution")
            .contains("PaymentProofTemplateRuleSuggestionResponse")
            .contains("suggestedLayoutJson: string")
            .contains("targetTemplateVersion?: number | null")
            .doesNotContain("contribution: PaymentProofTemplateContribution")
            .doesNotContain("suggestion: {");
    }

    @Test
    void platformPayNowProofTemplateLibraryPageIsWired() throws Exception {
        String page = FrontendSourceSupport.readString(Path.of("src", "pages", "PlatformPaymentProofTemplatesPage.vue"));
        String router = FrontendSourceSupport.readString(Path.of("src", "router", "index.ts"));

        assertThat(page)
            .contains("PlatformAdminNav")
            .contains("getPlatformPaymentProofTemplates")
            .contains("suggestPlatformPaymentProofTemplateRule")
            .contains("acceptPlatformPaymentProofTemplateContribution")
            .contains("rejectPlatformPaymentProofTemplateContribution")
            .contains("suggestionOcr.value = response.ocr")
            .contains("suggestionOcr?.rawText")
            .contains("targetTemplateVersion")
            .contains("decision === 'reject'")
            .contains("const refreshedSelected = templates.value.find(template => template.id === selected.value?.id)")
            .contains("selectTemplate(refreshedSelected)")
            .contains("PayNow 回单样式库");
        String tenantPage = FrontendSourceSupport.readString(Path.of("src", "pages", "TenantAdminPaymentProofTemplatesPage.vue"));
        assertThat(tenantPage)
            .contains("contributionFor(template)?.status")
            .contains("contributionFor(template)?.reviewNote");
        assertThat(router)
            .contains("PlatformPaymentProofTemplatesPage")
            .contains("platform-payment-proof-templates");
    }
}
