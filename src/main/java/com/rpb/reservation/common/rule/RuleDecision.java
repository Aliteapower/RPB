package com.rpb.reservation.common.rule;

/**
 * Lightweight reusable output placeholder for rules, policies, and validators.
 */
public record RuleDecision(boolean accepted, String violationCode) {

    public RuleDecision {
        if (!accepted && (violationCode == null || violationCode.isBlank())) {
            throw new IllegalArgumentException("violation_code_required");
        }
    }

    public static RuleDecision allow() {
        return new RuleDecision(true, null);
    }

    public static RuleDecision deny(String violationCode) {
        return new RuleDecision(false, violationCode);
    }
}
