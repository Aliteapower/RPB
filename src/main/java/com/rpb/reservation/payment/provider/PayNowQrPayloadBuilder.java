package com.rpb.reservation.payment.provider;

import com.rpb.reservation.payment.application.PaymentServiceErrorCode;
import com.rpb.reservation.payment.application.PaymentServiceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class PayNowQrPayloadBuilder {
    private static final String PAYNOW_TYPE_MOBILE = "mobile";
    private static final String PAYNOW_TYPE_UEN = "uen";

    public String build(PayNowQrPayloadRequest request) {
        if (request == null) {
            throw invalid();
        }
        String paynowType = trimLower(request.paynowType());
        if (!PAYNOW_TYPE_MOBILE.equals(paynowType) && !PAYNOW_TYPE_UEN.equals(paynowType)) {
            throw invalid();
        }

        String proxyValue = PAYNOW_TYPE_MOBILE.equals(paynowType) ? trim(request.paynowMobile()) : trim(request.paynowUen());
        String merchantName = trim(request.merchantName());
        String reference = trim(request.reference());
        BigDecimal amount = request.amount();
        if (isBlank(proxyValue) || isBlank(merchantName) || isBlank(reference) || amount == null || amount.signum() <= 0) {
            throw invalid();
        }

        String payloadWithoutCrc = tlv("00", "01")
            + tlv("01", "12")
            + tlv("26", merchantAccountInfo(paynowType, proxyValue))
            + tlv("52", "0000")
            + tlv("53", "702")
            + tlv("54", amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
            + tlv("58", "SG")
            + tlv("59", merchantName)
            + tlv("62", tlv("01", reference))
            + "6304";
        return payloadWithoutCrc + crc16(payloadWithoutCrc);
    }

    private static String merchantAccountInfo(String paynowType, String proxyValue) {
        String proxyType = PAYNOW_TYPE_MOBILE.equals(paynowType) ? "0" : "2";
        return tlv("00", "SG.PAYNOW")
            + tlv("01", proxyType)
            + tlv("02", proxyValue)
            + tlv("03", "0");
    }

    private static String tlv(String id, String value) {
        String safeValue = value == null ? "" : value;
        return id + "%02d".formatted(safeValue.length()) + safeValue;
    }

    private static String crc16(String value) {
        int crc = 0xFFFF;
        for (int index = 0; index < value.length(); index++) {
            crc ^= value.charAt(index) << 8;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
                crc &= 0xFFFF;
            }
        }
        return "%04X".formatted(crc);
    }

    private static PaymentServiceException invalid() {
        return new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_PROFILE_INVALID);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimLower(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
