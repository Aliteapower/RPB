package com.rpb.reservation.reservation.application.service;

import org.springframework.stereotype.Component;

@Component
public class PhoneMaskingPolicy {

    public String mask(String phone) {
        if (!hasText(phone)) {
            return "";
        }
        String value = phone.trim();
        int start = Math.max(0, value.length() - 4);
        return "****" + value.substring(start);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
