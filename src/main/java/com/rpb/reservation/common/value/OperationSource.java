package com.rpb.reservation.common.value;

import java.util.Locale;

public final class OperationSource {

    private static final String STAFF = "staff";
    private static final String CUSTOMER = "customer";
    private static final String INTEGRATION = "integration";
    private static final String SYSTEM = "system";

    private OperationSource() {
    }

    public static String fromActorType(String actorType) {
        return fromSourceOrActor(null, actorType);
    }

    public static String fromSourceOrActor(String source, String actorType) {
        String normalizedSource = normalize(source);
        if (isSupported(normalizedSource)) {
            return normalizedSource;
        }
        String normalizedActorType = normalize(actorType);
        if (isSupported(normalizedActorType)) {
            return normalizedActorType;
        }
        return STAFF;
    }

    private static boolean isSupported(String source) {
        return STAFF.equals(source)
            || CUSTOMER.equals(source)
            || INTEGRATION.equals(source)
            || SYSTEM.equals(source);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
