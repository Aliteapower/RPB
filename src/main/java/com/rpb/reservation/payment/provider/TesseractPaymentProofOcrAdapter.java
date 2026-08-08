package com.rpb.reservation.payment.provider;

import com.rpb.reservation.payment.application.PaymentProofOcrAdapter;
import com.rpb.reservation.payment.application.PaymentProofOcrExpected;
import com.rpb.reservation.payment.application.PaymentProofOcrFields;
import com.rpb.reservation.payment.application.PaymentServiceErrorCode;
import com.rpb.reservation.payment.application.PaymentServiceException;
import com.rpb.reservation.payment.domain.PaymentReferencePattern;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TesseractPaymentProofOcrAdapter implements PaymentProofOcrAdapter {
    private static final List<String> PAGE_SEGMENTATION_MODES = List.of("6", "11", "4");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?:SGD|S\\$|\\$)\\s*([0-9OoIl]{1,3}(?:,[0-9OoIl]{3})*(?:[.,][0-9OoIl]{1,2})?)|"
            + "([0-9OoIl]{1,3}(?:,[0-9OoIl]{3})*(?:[.,][0-9OoIl]{1,2})?)\\s*(?:SGD|S6D|SG)",
        Pattern.CASE_INSENSITIVE
    );
    private static final List<String> SUCCESS_KEYWORDS = List.of(
        "payment successful",
        "transfer successful",
        "sent successfully",
        "successfully sent",
        "you have paid",
        "you've sent",
        "您已支付",
        "已支付",
        "支付成功",
        "转账成功",
        "交易成功"
    );

    @Override
    public PaymentProofOcrFields extract(Path file, PaymentProofOcrExpected expected) {
        List<String> rawTexts = new ArrayList<>();
        PaymentServiceException lastFailure = null;
        for (String pageSegmentationMode : PAGE_SEGMENTATION_MODES) {
            try {
                String rawText = runTesseract(file, pageSegmentationMode);
                if (!rawText.isBlank()) {
                    rawTexts.add(rawText);
                }
            } catch (PaymentServiceException exception) {
                lastFailure = exception;
            }
        }
        if (rawTexts.isEmpty()) {
            if (lastFailure != null) {
                throw lastFailure;
            }
            throw new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_OCR_UNAVAILABLE);
        }
        return selectBestFields(rawTexts, expected);
    }

    static PaymentProofOcrFields selectBestFields(List<String> rawTexts, PaymentProofOcrExpected expected) {
        List<PaymentProofOcrFields> attempts = new ArrayList<>();
        int index = 0;
        for (String rawText : rawTexts) {
            index += 1;
            attempts.add(parseFields(rawText, expected, "{\"selectedAttempt\":" + index + "}"));
        }
        if (rawTexts.size() > 1) {
            attempts.add(parseFields(String.join("\n", rawTexts), expected, "{\"selectedAttempt\":\"combined\"}"));
        }
        return attempts.stream()
            .max(Comparator.comparing(fields -> selectionScore(fields, expected)))
            .orElseGet(() -> parseFields("", expected, "{}"));
    }

    private static PaymentProofOcrFields parseFields(
        String rawText,
        PaymentProofOcrExpected expected,
        String rawJson
    ) {
        String extractedReference = PaymentReferencePattern.extractSystemReference(rawText).orElse(null);
        BigDecimal extractedAmount = extractAmount(rawText, expected == null ? null : expected.expectedAmount()).orElse(null);
        boolean successDetected = successDetected(rawText);
        BigDecimal confidence = confidence(extractedReference, extractedAmount, expected, successDetected);
        return new PaymentProofOcrFields(
            extractedReference,
            extractedAmount,
            null,
            detectBank(rawText),
            successDetected,
            confidence,
            rawText,
            rawJson
        );
    }

    public static Optional<BigDecimal> extractAmount(String rawText, BigDecimal expectedAmount) {
        Matcher matcher = AMOUNT_PATTERN.matcher(rawText == null ? "" : rawText);
        List<BigDecimal> candidates = new java.util.ArrayList<>();
        while (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            parseAmount(value).ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (expectedAmount != null) {
            candidates.sort(java.util.Comparator.comparing(value -> value.subtract(expectedAmount).abs()));
        }
        return Optional.of(candidates.get(0).setScale(2, RoundingMode.HALF_UP));
    }

    public static Optional<BigDecimal> parseAmount(String value) {
        String clean = value == null ? "" : value.trim()
            .replace('O', '0')
            .replace('o', '0')
            .replace('I', '1')
            .replace('l', '1')
            .replace(",", "");
        clean = clean.replaceAll("[^0-9.]", "");
        if (clean.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(clean).setScale(2, RoundingMode.HALF_UP));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public static boolean successDetected(String rawText) {
        String lower = rawText == null ? "" : rawText.toLowerCase();
        return SUCCESS_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private static String runTesseract(Path file, String pageSegmentationMode) {
        String command = System.getenv("PAYMENT_OCR_TESSERACT_CMD");
        if (command == null || command.isBlank()) {
            command = "tesseract";
        }
        ProcessBuilder builder = new ProcessBuilder(
            command,
            file.toAbsolutePath().toString(),
            "stdout",
            "-l",
            "eng+chi_sim",
            "--oem",
            "1",
            "--psm",
            pageSegmentationMode
        );
        try {
            Process process = builder.start();
            byte[] stdout = process.getInputStream().readAllBytes();
            byte[] stderr = process.getErrorStream().readAllBytes();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_OCR_UNAVAILABLE);
            }
            String text = new String(stdout, StandardCharsets.UTF_8).trim();
            if (text.isBlank() && stderr.length > 0) {
                throw new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_OCR_UNAVAILABLE);
            }
            return text;
        } catch (IOException exception) {
            throw new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_OCR_UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_OCR_UNAVAILABLE);
        }
    }

    private static BigDecimal selectionScore(PaymentProofOcrFields fields, PaymentProofOcrExpected expected) {
        BigDecimal score = fields.confidence();
        if (expected != null
            && expected.expectedReference() != null
            && PaymentReferencePattern.normalize(expected.expectedReference()).equals(PaymentReferencePattern.normalize(fields.extractedReference()))) {
            score = score.add(new BigDecimal("1.00"));
        }
        if (expected != null
            && expected.expectedAmount() != null
            && fields.extractedAmount() != null
            && fields.extractedAmount().compareTo(expected.expectedAmount().setScale(2, RoundingMode.HALF_UP)) == 0) {
            score = score.add(new BigDecimal("0.35"));
        }
        if (fields.extractedReference() != null && fields.extractedAmount() != null) {
            score = score.add(new BigDecimal("0.20"));
        }
        if (fields.extractedReference() != null) {
            int referenceLength = Math.min(40, fields.extractedReference().length());
            score = score.add(new BigDecimal(referenceLength).movePointLeft(3));
        }
        return score;
    }

    private static BigDecimal confidence(
        String extractedReference,
        BigDecimal extractedAmount,
        PaymentProofOcrExpected expected,
        boolean successDetected
    ) {
        BigDecimal score = BigDecimal.ZERO;
        if (extractedReference != null && !extractedReference.isBlank()) {
            score = score.add(new BigDecimal("0.40"));
        }
        if (expected != null
            && expected.expectedReference() != null
            && PaymentReferencePattern.normalize(expected.expectedReference()).equals(PaymentReferencePattern.normalize(extractedReference))) {
            score = score.add(new BigDecimal("0.20"));
        }
        if (extractedAmount != null) {
            score = score.add(new BigDecimal("0.25"));
        }
        if (successDetected) {
            score = score.add(new BigDecimal("0.15"));
        }
        return score.min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
    }

    private static String detectBank(String rawText) {
        String lower = rawText == null ? "" : rawText.toLowerCase();
        for (String bank : List.of("ocbc", "dbs", "posb", "uob", "maybank", "citi")) {
            if (lower.contains(bank)) {
                return bank;
            }
        }
        return null;
    }
}
