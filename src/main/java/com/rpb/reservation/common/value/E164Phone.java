package com.rpb.reservation.common.value;

import java.util.regex.Pattern;

/**
 * Nullable E.164 phone value. Null represents a supported no-phone Customer.
 */
public record E164Phone(String value) {

    private static final Pattern E164 = Pattern.compile("^[+][1-9][0-9]{1,14}$");

    public E164Phone {
        if (value != null && !E164.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid_phone_e164");
        }
    }

    public static E164Phone empty() {
        return new E164Phone(null);
    }

    public boolean isPresent() {
        return value != null;
    }
}
