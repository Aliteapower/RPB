package com.rpb.reservation.platformbilling.application;

public record BillingDuration(
    String billingCycle,
    int durationCount
) {
    public BillingDuration {
        if (!"monthly".equals(billingCycle) && !"yearly".equals(billingCycle)) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
        int max = "monthly".equals(billingCycle) ? 120 : 10;
        if (durationCount < 1 || durationCount > max) {
            throw new PlatformBillingServiceException(PlatformBillingServiceErrorCode.REQUEST_INVALID);
        }
    }

    public String durationUnit() {
        return "monthly".equals(billingCycle) ? "month" : "year";
    }
}
