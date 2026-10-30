package com.rpb.reservation.walkin.auth;

import com.rpb.reservation.auth.application.AuthApplicationService;
import com.rpb.reservation.auth.security.AuthCookieService;
import com.rpb.reservation.auth.security.AuthSessionFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "test"})
@ConditionalOnProperty(prefix = "rpb.local-auth", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LocalAuthProperties.class)
public class LocalRuntimeSecurityConfiguration {

    @Bean
    SecurityFilterChain localRuntimeSecurityFilterChain(
        HttpSecurity http,
        ObjectProvider<AuthCookieService> cookieServiceProvider,
        ObjectProvider<AuthApplicationService> authServiceProvider
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable);

        AuthCookieService cookieService = cookieServiceProvider.getIfAvailable();
        AuthApplicationService authService = authServiceProvider.getIfAvailable();
        if (cookieService != null && authService != null) {
            http.addFilterBefore(new AuthSessionFilter(cookieService, authService), UsernamePasswordAuthenticationFilter.class);
        }

        return http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/schedule/*/deletion").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/schedule-adjustments/preview","/api/v1/stores/*/workforce/schedule-adjustments/*").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/schedule-copy-bulk/preview","/api/v1/stores/*/workforce/schedule-copy-bulk/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/schedule-board","/api/v1/stores/*/workforce/schedule-bulk/options","/api/v1/stores/*/workforce/schedule-staffing/employees").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/schedule-bulk/preview","/api/v1/stores/*/workforce/schedule-bulk/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/employees/*/login-account").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/employees/*/login-account","/api/v1/stores/*/workforce/employees/*/login-account/employee-number","/api/v1/stores/*/workforce/employees/*/login-account/status").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/review-explanations").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/review-explanations/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/shifts","/api/v1/stores/*/workforce/schedule","/api/v1/stores/*/workforce/clock","/api/v1/stores/*/workforce/attendance","/api/v1/stores/*/workforce/attendance/*","/api/v1/stores/*/workforce/corrections").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/shifts/*","/api/v1/stores/*/workforce/schedule/*","/api/v1/stores/*/workforce/schedule-copy/*","/api/v1/stores/*/workforce/clock/*","/api/v1/stores/*/workforce/corrections/*","/api/v1/stores/*/workforce/corrections/*/decision").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/leave-types","/api/v1/stores/*/workforce/leaves","/api/v1/stores/*/workforce/calendar","/api/v1/stores/*/workforce/approvals","/api/v1/stores/*/workforce/approvals/*","/api/v1/stores/*/workforce/work-summary").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/leave-types/*","/api/v1/stores/*/workforce/leaves/*","/api/v1/stores/*/workforce/leaves/*/decision","/api/v1/stores/*/workforce/calendar/*","/api/v1/stores/*/workforce/approvals/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/payroll-report","/api/v1/stores/*/workforce/payroll-report/export").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/payroll-reconciliation").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/pay-template-library/*/upgrade-preview").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/pay-change-bulk-actions","/api/v1/stores/*/workforce/pay-change-requests/*/upgrade").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/pay-change-requests","/api/v1/stores/*/workforce/pay-change-requests/*","/api/v1/stores/*/workforce/pay-change-requests/*/impact","/api/v1/stores/*/workforce/pay-change-monthly-baseline").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/pay-change-requests/*","/api/v1/stores/*/workforce/pay-change-requests/*/actions").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/pay-template-publication-directory","/api/v1/stores/*/workforce/pay-template-masters","/api/v1/stores/*/workforce/pay-template-library","/api/v1/stores/*/workforce/pay-template-library/*/preview").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/pay-template-masters/*","/api/v1/stores/*/workforce/pay-template-masters/*/publish","/api/v1/stores/*/workforce/pay-template-library/*/enable").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/employees/*/pay-summary","/api/v1/stores/*/workforce/position-salary-defaults","/api/v1/stores/*/workforce/pay-profile-preview","/api/v1/stores/*/workforce/payroll-home","/api/v1/stores/*/workforce/payroll-workbench","/api/v1/stores/*/workforce/salary-templates","/api/v1/stores/*/workforce/pay-profiles","/api/v1/stores/*/workforce/payroll-batches","/api/v1/stores/*/workforce/payroll-batches/*","/api/v1/stores/*/workforce/my-payslips","/api/v1/stores/*/workforce/my-payslips/*","/api/v1/stores/*/workforce/payroll-batches/*/payslips/*","/api/v1/stores/*/workforce/payroll-batches/*/changes").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/position-salary-defaults/*","/api/v1/stores/*/workforce/salary-templates/*","/api/v1/stores/*/workforce/pay-profiles/*","/api/v1/stores/*/workforce/payroll-batches/*","/api/v1/stores/*/workforce/payroll-batches/*/inputs/*","/api/v1/stores/*/workforce/payroll-batches/*/actions/*","/api/v1/stores/*/workforce/payroll-batches/*/payments/*","/api/v1/stores/*/workforce/payroll-batch-operations/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/document-types","/api/v1/stores/*/workforce/employees/*/documents","/api/v1/stores/*/workforce/employees/*/documents/*/attachments/*").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/document-types/*","/api/v1/stores/*/workforce/employees/*/documents/*","/api/v1/stores/*/workforce/employees/*/documents/*/attachments/*","/api/v1/stores/*/workforce/employees/*/documents/*/attachments/*/deletion").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/document-expiries","/api/v1/stores/*/workforce/document-expiries/employees").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/public-holidays/*","/api/v1/stores/*/workforce/public-holiday-templates/*","/api/v1/stores/*/workforce/store-rest-months/*").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/public-holidays/*","/api/v1/stores/*/workforce/store-rest-months/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/statutory-policies","/api/v1/stores/*/workforce/statutory-policies/*/versions","/api/v1/stores/*/workforce/statutory-policy-templates/*").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/statutory-policies/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/supplemental-sources","/api/v1/stores/*/workforce/supplemental-batches","/api/v1/stores/*/workforce/supplemental-batches/*","/api/v1/stores/*/workforce/my-supplemental-payslips","/api/v1/stores/*/workforce/my-supplemental-payslips/*","/api/v1/stores/*/workforce/statutory-settlement-contexts/*/*/*","/api/v1/stores/*/workforce/statutory-company-months/*/*","/api/v1/stores/*/workforce/statutory-remittances","/api/v1/stores/*/workforce/statutory-remittances/*","/api/v1/stores/*/workforce/aw-reconciliations","/api/v1/stores/*/workforce/aw-reconciliations/*").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/supplemental-batches/*","/api/v1/stores/*/workforce/supplemental-batches/*/payments/*","/api/v1/stores/*/workforce/statutory-settlement-contexts/*/*/*","/api/v1/stores/*/workforce/statutory-company-months/*/*","/api/v1/stores/*/workforce/statutory-remittances/*").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/stores/*/workforce/supplemental-batches/*/actions","/api/v1/stores/*/workforce/aw-reconciliations/*/settlements","/api/v1/stores/*/workforce/statutory-settlement-contexts/*/*/*/settlements").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/statutory-policy-templates/*/with-aw","/api/v1/stores/*/workforce/statutory-year-contexts/*/*/*").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/statutory-year-contexts/*/*/*").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/stores/*/workforce/statutory-year-contexts/*/*/*/reconciliations").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/employees/*/documents/*/attachments/*/recognition").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/attendance-rule-templates").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/attendance-rule-templates/*").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/rest-swaps/preview","/api/v1/stores/*/workforce/rest-swaps/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/leave-batch").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/leave-batch").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/leave-groups").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/leave-groups/*/decision").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/approval-board","/api/v1/stores/*/workforce/approval-readiness").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/approval-board/preview","/api/v1/stores/*/workforce/approval-board/commit").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/work-mode","/api/v1/stores/*/workforce/clock-site","/api/v1/stores/*/workforce/clock-site/settings","/api/v1/stores/*/workforce/employees/*/work-mode").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/clock-site","/api/v1/stores/*/workforce/clock-site/settings","/api/v1/stores/*/workforce/employees/*/work-modes/*","/api/v1/stores/*/workforce/home-addresses/*","/api/v1/stores/*/workforce/home-addresses/*/decision","/api/v1/stores/*/workforce/clock-site/check").permitAll()
                // Workforce verifies the authenticated actor, exact scope and AppGate in its application services.
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/workforce/home","/api/v1/stores/*/workforce/employees","/api/v1/stores/*/workforce/employees/*","/api/v1/stores/*/workforce/companies","/api/v1/stores/*/workforce/administrators").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/workforce/employees/*","/api/v1/stores/*/workforce/companies/*","/api/v1/stores/*/workforce/administrators/*").permitAll()
                // Authenticated and scoped by the storage application boundary, including public customer cookies.
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/wine-storage/inventory").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/wine-storage/inventory/export").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/wine-storage/home","/api/v1/stores/*/wine-storage/products","/api/v1/stores/*/wine-storage/photos/*","/api/v1/public/stores/*/wine-storage/home","/api/v1/public/stores/*/wine-storage/products","/api/v1/public/stores/*/wine-storage/photos/*").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/stores/*/wine-storage/settings","/api/v1/stores/*/wine-storage/direct-deliver","/api/v1/stores/*/wine-storage/rules","/api/v1/stores/*/wine-storage/photos","/api/v1/stores/*/wine-storage/applications/*/receive","/api/v1/stores/*/wine-storage/applications/*/reject","/api/v1/stores/*/wine-storage/intents","/api/v1/stores/*/wine-storage/scan","/api/v1/stores/*/wine-storage/intents/*/deliver","/api/v1/stores/*/wine-storage/intents/*/cancel","/api/v1/stores/*/wine-storage/items/*/corrections","/api/v1/stores/*/wine-storage/items/*/photos","/api/v1/public/stores/*/wine-storage/applications","/api/v1/public/stores/*/wine-storage/applications/*/cancel","/api/v1/public/stores/*/wine-storage/photos","/api/v1/public/stores/*/wine-storage/intents","/api/v1/public/stores/*/wine-storage/scan","/api/v1/public/stores/*/wine-storage/intents/*/authorize","/api/v1/public/stores/*/wine-storage/intents/*/cancel").permitAll()
                .requestMatchers("/api/v1/stores/*/catalog/**", "/api/v1/stores/*/wallet/cards/**", "/api/v1/public/stores/*/wallet/cards/**").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/wallet/collection-methods").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/wallet/collection-methods").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/wallet/business-report").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/wallet/transactions","/api/v1/stores/*/wallet/transactions/*/*","/api/v1/public/stores/*/wallet/transactions","/api/v1/public/stores/*/wallet/transactions/*/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/wallet/personal-scan-charges/result","/api/v1/public/stores/*/wallet/personal-payment-consent").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/stores/*/wallet/personal-scan-charges/reconcile","/api/v1/stores/*/wallet/personal-scan-charges","/api/v1/public/stores/*/wallet/personal-payment-consent","/api/v1/public/stores/*/wallet/personal-codes","/api/v1/public/stores/*/wallet/personal-codes/status").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/wallet/collection-proofs/*/*/submitted","/api/v1/stores/*/wallet/collection-proofs/*/*/context","/api/v1/stores/*/wallet/collection-proofs/*/*","/api/v1/stores/*/wallet/collection-proofs/images/*").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/stores/*/wallet/collection-proofs/*/*/scan").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/public/stores/*/wallet/collection-proofs/*/*/context","/api/v1/public/stores/*/wallet/collection-proofs/*/*/paynow","/api/v1/public/stores/*/wallet/collection-proofs/*/*","/api/v1/public/stores/*/wallet/collection-proofs/images/*").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/public/stores/*/wallet/collection-proofs/*/*/submit").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/wallet/collection-profile","/api/v1/stores/*/wallet/context","/api/v1/stores/*/wallet/customers/*","/api/v1/stores/*/wallet/customers/*/accounts","/api/v1/stores/*/wallet/logo/*","/api/v1/stores/*/wallet/collection-exceptions","/api/v1/stores/*/wallet/customers","/api/v1/stores/*/wallet/accounts/*","/api/v1/stores/*/wallet/topups/*","/api/v1/stores/*/wallet/topups/*/paynow","/api/v1/stores/*/wallet/bills/*","/api/v1/stores/*/wallet/bills/*/paynow","/api/v1/stores/*/wallet/refunds/*","/api/v1/stores/*/wallet/reconciliation").permitAll()
                .requestMatchers(HttpMethod.PUT,"/api/v1/stores/*/wallet/collection-profile","/api/v1/stores/*/wallet/program").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/stores/*/wallet/scan-charges","/api/v1/stores/*/wallet/payment-codes/resolve","/api/v1/stores/*/wallet/collection-exceptions","/api/v1/stores/*/wallet/accounts","/api/v1/stores/*/wallet/topups","/api/v1/stores/*/wallet/topups/*/confirm","/api/v1/stores/*/wallet/topups/*/cancel","/api/v1/stores/*/wallet/bills","/api/v1/stores/*/wallet/bills/*/code","/api/v1/stores/*/wallet/bills/*/bind-code","/api/v1/stores/*/wallet/bills/*/collections","/api/v1/stores/*/wallet/bills/*/cancel","/api/v1/stores/*/wallet/refunds","/api/v1/stores/*/wallet/refunds/*/confirm","/api/v1/stores/*/wallet/refunds/*/cancel").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/public/stores/*/wallet/me","/api/v1/public/stores/*/wallet/bills/*","/api/v1/public/stores/*/wallet/logo/*","/api/v1/public/stores/*/wallet/pending-bills").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/public/stores/*/wallet/payment-codes/status","/api/v1/public/stores/*/wallet/payment-codes","/api/v1/public/stores/*/wallet/bill-code","/api/v1/public/stores/*/wallet/bills/*/quote","/api/v1/public/stores/*/wallet/bills/*/pay").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/wallet/points-redemptions/*","/api/v1/public/stores/*/wallet/points-redemptions/*").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/stores/*/wallet/points-redemptions","/api/v1/stores/*/wallet/points-redemptions/*/code","/api/v1/stores/*/wallet/points-redemptions/*/cancel","/api/v1/public/stores/*/wallet/points-redemption-code","/api/v1/public/stores/*/wallet/points-redemptions/*/confirm").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stores/*/wallet/merchant-orders/*","/api/v1/public/stores/*/wallet/merchant-orders/*").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/stores/*/wallet/merchant-orders","/api/v1/stores/*/wallet/merchant-orders/*/code","/api/v1/stores/*/wallet/merchant-orders/*/cancel","/api/v1/public/stores/*/wallet/merchant-order-code","/api/v1/public/stores/*/wallet/merchant-orders/*/confirm").permitAll()
                // Exact method/path additions; wallet use cases still enforce session, scope and App Gate.
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/stores/*/wallet/marketing/settings",
                    "/api/v1/stores/*/wallet/marketing/campaigns",
                    "/api/v1/stores/*/wallet/marketing/campaigns/*",
                    "/api/v1/stores/*/wallet/topup-offers",
                    "/api/v1/stores/*/wallet/accounts/*/funding-lots",
                    "/api/v1/stores/*/wallet/points/context",
                    "/api/v1/stores/*/wallet/customers/*/points").permitAll()
                .requestMatchers(HttpMethod.PUT,
                    "/api/v1/stores/*/wallet/marketing/settings",
                    "/api/v1/stores/*/wallet/marketing/campaigns/*/draft").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/stores/*/wallet/marketing/campaigns",
                    "/api/v1/stores/*/wallet/marketing/campaigns/*/publish",
                    "/api/v1/stores/*/wallet/marketing/campaigns/*/disable",
                    "/api/v1/stores/*/wallet/marketing/campaigns/*/archive",
                    "/api/v1/stores/*/wallet/topup-quotes",
                    "/api/v1/stores/*/wallet/customer-codes/resolve",
                    "/api/v1/stores/*/wallet/charge-previews",
                    "/api/v1/stores/*/wallet/refund-previews",
                    "/api/v1/stores/*/wallet/funding-lots/*/exit-bonus",
                    "/api/v1/stores/*/wallet/customers/*/points/grants",
                    "/api/v1/stores/*/wallet/customers/*/points/deductions",
                    "/api/v1/stores/*/wallet/customers/*/points/redemptions",
                    "/api/v1/stores/*/wallet/points/entries/*/reverse").permitAll()
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/public/stores/*/wallet/topup-offers",
                    "/api/v1/public/stores/*/wallet/topups/*",
                    "/api/v1/public/stores/*/wallet/topups/*/paynow",
                    "/api/v1/public/stores/*/wallet/points").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/public/stores/*/wallet/topup-quotes",
                    "/api/v1/public/stores/*/wallet/customer-codes",
                    "/api/v1/public/stores/*/wallet/topups",
                    "/api/v1/public/stores/*/wallet/topups/*/declare-paid",
                    "/api/v1/public/stores/*/wallet/topups/*/cancel").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/platform/tenants/*/product-subscriptions/quote","/api/v1/platform/tenants/*/product-subscriptions/*/add-stores").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/daily-ledger/settings", "/api/v1/stores/*/daily-ledger/days/*", "/api/v1/stores/*/daily-ledger/history", "/api/v1/stores/*/daily-ledger/years", "/api/v1/stores/*/daily-ledger/audit").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/v1/stores/*/daily-ledger/settings", "/api/v1/stores/*/daily-ledger/days/*/records/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/me/apps").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/walk-ins/direct-seating").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/walk-ins/queue").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/seatings/*/cleaning/start").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/seatings/*/table-switch").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/cleanings/*/complete").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations").permitAll()
                .requestMatchers("/api/v1/public/customer-auth/**").permitAll()
                .requestMatchers("/api/v1/public/stores/*/booking/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/reservations/today").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/reservations/calendar-summary").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/reservations/time-slots").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/reservations/*/share-info").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/share-info/intent").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/reservations/*/assignable-tables").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/v1/stores/*/reservations/*/table-assignment").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/public/reservation-shares/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/staff-home/overview").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/tables").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/tables/temporary-groups").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/stores/*/tables/temporary-groups/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/check-in").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/seating/direct").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/seating/check-in-direct").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/queue").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/cancel").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/no-show").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/reservations/*/complete").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/customers/phone-lookup").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/queue-tickets").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/queue-display/state").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/stores/*/queue-display/media/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/call").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/skip").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/rejoin").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/cancel").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/stores/*/queue-tickets/*/seating/direct").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants/*/admin-store-access").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants/*/operating-entities").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/operating-entities").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/tenants/*/operating-entities/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants/*/stores").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/stores").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/tenants/*/stores/*").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/tenants/*/stores/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/tenants/*").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/tenants/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/restore").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/logo").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/tenants/*/logo").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants/*/logo/media/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/profile").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/profile").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/profile/logo").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/profile/logo").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/profile/social-links").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/profile/social-links/*").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/profile/social-links/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/profile/social-links/*/logo").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/platform/profile/social-links/*/logo").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/product-lines").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/product-lines").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/product-lines/*").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/product-lines/*/prices").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants/*/product-subscriptions","/api/v1/platform/tenants/*/product-subscriptions/*/events").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/product-subscriptions/purchase").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/product-subscriptions/*/renew").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/product-subscriptions/*/items/*/renew").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/product-subscriptions/*/suspend").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/product-subscriptions/*/cancel").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants/*/product-subscriptions/*/convert-from-legacy").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/call-screen/text-seed").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/call-screen/text-seed").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/reservation/share-template-seed").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/reservation/share-template-seed").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/reservation/meal-period-seed").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/reservation/meal-period-seed").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/call-screen/media-seed").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/call-screen/media-seed").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/platform/call-screen/media").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/call-screen/media/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/platform/i18n/catalog").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/platform/i18n/catalog").permitAll()
                .requestMatchers("/api/v1/stores/*/tenant-admin/**").permitAll()
                .anyRequest().denyAll()
            )
            .build();
    }
}
