package com.rpb.reservation.tenantadmin.api;

public record TenantAdminCustomerMutationRequest(
    String displayName,
    String nickname,
    String phoneE164,
    String email,
    PrivacyConsentRequest privacyConsent
) {
    public record PrivacyConsentRequest(Boolean accepted,String noticeVersion,String entrySource) {
        public com.rpb.reservation.customer.application.CustomerPrivacyConsentCommand toCommand() {
            return new com.rpb.reservation.customer.application.CustomerPrivacyConsentCommand(accepted,noticeVersion,entrySource);
        }
    }
}
