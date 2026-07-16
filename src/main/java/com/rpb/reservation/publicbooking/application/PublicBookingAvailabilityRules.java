package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.common.time.BusinessDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class PublicBookingAvailabilityRules {

    private PublicBookingAvailabilityRules() {
    }

    static Optional<PublicBookingAvailabilityRule> effectiveRule(
        List<PublicBookingAvailabilityRule> rules,
        BusinessDate businessDate,
        String periodKey
    ) {
        if (rules == null || rules.isEmpty()) {
            return Optional.empty();
        }
        return rules.stream()
            .filter(rule -> appliesTo(rule, businessDate, periodKey))
            .min(Comparator.comparingInt(rule -> priority(rule, periodKey)));
    }

    private static boolean appliesTo(
        PublicBookingAvailabilityRule rule,
        BusinessDate businessDate,
        String periodKey
    ) {
        if (rule == null || businessDate == null) {
            return false;
        }
        if (!periodMatches(rule.periodKey(), periodKey)) {
            return false;
        }
        if (PublicBookingAvailabilityRule.TYPE_DATE_EXCEPTION.equals(rule.ruleType())) {
            return businessDate.value().equals(rule.businessDate());
        }
        if (PublicBookingAvailabilityRule.TYPE_WEEKLY.equals(rule.ruleType())) {
            return rule.dayOfWeek() != null && rule.dayOfWeek() == businessDate.value().getDayOfWeek().getValue();
        }
        return false;
    }

    private static int priority(PublicBookingAvailabilityRule rule, String periodKey) {
        int datePriority = PublicBookingAvailabilityRule.TYPE_DATE_EXCEPTION.equals(rule.ruleType()) ? 0 : 2;
        int periodPriority = hasText(rule.periodKey()) && rule.periodKey().equals(periodKey) ? 0 : 1;
        return datePriority + periodPriority;
    }

    private static boolean periodMatches(String rulePeriodKey, String targetPeriodKey) {
        return !hasText(rulePeriodKey) || rulePeriodKey.equals(targetPeriodKey);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
