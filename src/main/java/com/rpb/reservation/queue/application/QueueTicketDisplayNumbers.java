package com.rpb.reservation.queue.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueueTicketDisplayNumbers {
    private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");

    private QueueTicketDisplayNumbers() {
    }

    public static String fromGroupCode(String groupCode, Integer ticketNumber) {
        if (ticketNumber == null) {
            return null;
        }
        return prefix(groupCode) + ticketNumber;
    }

    private static String prefix(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            return "Q";
        }

        Matcher matcher = LEADING_NUMBER.matcher(groupCode.trim());
        if (!matcher.find()) {
            return "Q";
        }

        int minPartySize = Integer.parseInt(matcher.group(1));
        if (minPartySize <= 2) {
            return "A";
        }
        if (minPartySize <= 4) {
            return "B";
        }
        if (minPartySize <= 6) {
            return "C";
        }
        return "D";
    }
}
