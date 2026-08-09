package com.rpb.reservation.payment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.domain.PaymentReferencePattern;
import com.rpb.reservation.payment.persistence.PaymentProofTemplateRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentProofTemplateService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final BigDecimal CONFIDENCE_BOOST = new BigDecimal("0.1800");

    private final PaymentProofTemplateRepository repository;
    private final PaymentProofOcrAdapter ocrAdapter;

    public PaymentProofTemplateService(PaymentProofTemplateRepository repository) {
        this(repository, null);
    }

    @Autowired
    public PaymentProofTemplateService(PaymentProofTemplateRepository repository, PaymentProofOcrAdapter ocrAdapter) {
        this.repository = Objects.requireNonNull(repository, "payment_proof_template_repository_required");
        this.ocrAdapter = ocrAdapter;
    }

    @Transactional(readOnly = true)
    public List<PaymentProofTemplate> listTemplates(StoreScope scope, CurrentActor actor) {
        validateActor(scope, actor);
        return repository.findManageableTemplates(scope);
    }

    @Transactional
    public PaymentProofTemplate createTenantTemplate(
        StoreScope scope,
        PaymentProofTemplateCommand command,
        CurrentActor actor
    ) {
        validateActor(scope, actor);
        return repository.createTenantTemplate(scope, normalized(command), actor.actorId());
    }

    @Transactional
    public PaymentProofTemplate updateTenantTemplate(
        StoreScope scope,
        UUID templateId,
        PaymentProofTemplateCommand command,
        CurrentActor actor
    ) {
        validateActor(scope, actor);
        if (templateId == null) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
        return repository.updateTenantTemplate(scope, templateId, normalized(command));
    }

    @Transactional
    public PaymentProofTemplateTestScanResult testScan(
        StoreScope scope,
        String fileName,
        String contentType,
        byte[] fileBytes,
        CurrentActor actor
    ) {
        validateActor(scope, actor);
        if (ocrAdapter == null || fileBytes == null || fileBytes.length == 0 || !allowedContentType(contentType)) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
        PaymentProofOcrFields fields = extract(fileName, fileBytes);
        PaymentProofOcrFields enhanced = enhance(scope, fields);
        PaymentProofTemplate matchedTemplate = matchedTemplate(scope, enhanced.rawText()).orElse(null);
        if (matchedTemplate != null) {
            repository.createSample(
                scope,
                matchedTemplate.id(),
                new PaymentProofTemplateSampleCommand(
                    trim(fileName),
                    trimLower(contentType),
                    fileDigest(fileBytes),
                    enhanced.extractedReference(),
                    enhanced.extractedAmount(),
                    enhanced.rawText()
                ),
                actor.actorId()
            );
        }
        return new PaymentProofTemplateTestScanResult(matchedTemplate, enhanced);
    }

    @Transactional(readOnly = true)
    public PaymentProofTemplateRuleSuggestion suggestRule(
        StoreScope scope,
        String fileName,
        String contentType,
        byte[] fileBytes,
        String bankCode,
        String bankName,
        String locale,
        CurrentActor actor
    ) {
        validateActor(scope, actor);
        return suggest(fileName, contentType, fileBytes, bankCode, bankName, locale);
    }

    @Transactional(readOnly = true)
    public PaymentProofTemplateRuleSuggestion suggestPlatformRule(
        String fileName,
        String contentType,
        byte[] fileBytes,
        String bankCode,
        String bankName,
        String locale
    ) {
        return suggest(fileName, contentType, fileBytes, bankCode, bankName, locale);
    }

    @Transactional(readOnly = true)
    public PaymentProofOcrFields enhance(StoreScope scope, PaymentProofOcrFields fields) {
        if (scope == null || fields == null || isBlank(fields.rawText())) {
            return fields;
        }
        Optional<PaymentProofTemplate> matched = matchedTemplate(scope, fields.rawText());
        if (matched.isEmpty()) {
            return fields;
        }
        PaymentProofTemplate template = matched.get();
        TemplateLayout layout = TemplateLayout.from(template.layoutJson());
        String extractedReference = firstNonBlank(
            extractByPatterns(fields.rawText(), layout.referencePatterns()).orElse(null),
            fields.extractedReference(),
            PaymentReferencePattern.extractSystemReference(fields.rawText()).orElse(null)
        );
        BigDecimal extractedAmount = extractAmountByPatterns(fields.rawText(), layout.amountPatterns())
            .orElse(fields.extractedAmount());
        boolean successDetected = fields.successDetected() || containsAny(fields.rawText(), layout.successKeywords());
        BigDecimal confidence = boostedConfidence(fields.confidence(), extractedReference, extractedAmount, successDetected);
        return new PaymentProofOcrFields(
            extractedReference,
            extractedAmount,
            fields.extractedPaidAt(),
            firstNonBlank(fields.bankCode(), template.bankCode()),
            successDetected,
            confidence,
            fields.rawText(),
            templateRawJson(fields.rawJson(), template)
        );
    }

    private Optional<PaymentProofTemplate> matchedTemplate(StoreScope scope, String rawText) {
        String lower = rawText == null ? "" : rawText.toLowerCase();
        return repository.findEffectiveTemplates(scope).stream()
            .filter(PaymentProofTemplate::active)
            .filter(template -> TemplateLayout.from(template.layoutJson()).matches(lower))
            .sorted(Comparator
                .comparing((PaymentProofTemplate template) -> !template.tenantOwned())
                .thenComparingInt(PaymentProofTemplate::priority)
                .thenComparing(PaymentProofTemplate::templateName))
            .findFirst();
    }

    private PaymentProofOcrFields extract(String fileName, byte[] fileBytes) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("rpb-payment-proof-template-", suffix(fileName));
            Files.write(tempFile, fileBytes);
            return ocrAdapter.extract(tempFile, new PaymentProofOcrExpected(null, null));
        } catch (IOException exception) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private PaymentProofTemplateRuleSuggestion suggest(
        String fileName,
        String contentType,
        byte[] fileBytes,
        String bankCode,
        String bankName,
        String locale
    ) {
        if (ocrAdapter == null || fileBytes == null || fileBytes.length == 0 || !allowedContentType(contentType)) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
        PaymentProofOcrFields fields = extract(fileName, fileBytes);
        String normalizedBankCode = firstNonBlank(trimLower(bankCode), trimLower(fields.bankCode()));
        String normalizedBankName = firstNonBlank(trim(bankName), normalizedBankCode == null ? null : normalizedBankCode.toUpperCase(Locale.ROOT));
        String normalizedLocale = trim(locale) == null ? "zh-CN" : trim(locale);
        if (isBlank(normalizedBankCode) || isBlank(normalizedBankName)) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
        return new PaymentProofTemplateRuleSuggestion(
            normalizedBankCode,
            normalizedBankName,
            normalizedLocale,
            normalizedBankName + " PayNow",
            suggestedLayoutJson(fields.rawText(), normalizedBankCode, fields),
            fields
        );
    }

    private static String suggestedLayoutJson(String rawText, String bankCode, PaymentProofOcrFields fields) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ArrayNode matchKeywords = root.putArray("matchKeywords");
        stableKeywords(rawText, bankCode).forEach(matchKeywords::add);
        ArrayNode successKeywords = root.putArray("successKeywords");
        stableSuccessKeywords(rawText, fields).forEach(successKeywords::add);
        ArrayNode referencePatterns = root.putArray("referencePatterns");
        referencePatterns.add("(?:讯息|信息|Message|Comment|Ref|Reference)\\s*[:：]?\\s*\\\"?([A-Z0-9.-]{10,32})\\\"?");
        ArrayNode amountPatterns = root.putArray("amountPatterns");
        amountPatterns.add("(?:您已支付|You(?:'ve)? sent|Payment successful|paid|sent)\\s*(?:S\\$|SGD|\\$)?\\s*([0-9OoIl,.]+)");
        amountPatterns.add("(?:SGD|S\\$|\\$)\\s*([0-9OoIl,.]+)");
        root.putArray("referenceRoi").add(0.0).add(0.30).add(1.0).add(0.66);
        root.putArray("amountRoi").add(0.0).add(0.15).add(1.0).add(0.42);
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("payment_proof_template_rule_json_required", exception);
        }
    }

    private static List<String> stableKeywords(String rawText, String bankCode) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (!isBlank(bankCode)) {
            values.add(bankCode.toUpperCase(Locale.ROOT));
        }
        String text = rawText == null ? "" : rawText.toLowerCase(Locale.ROOT);
        for (String keyword : List.of("PayNow", "OCBC", "DBS", "UOB", "POSB")) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                values.add(keyword);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> stableSuccessKeywords(String rawText, PaymentProofOcrFields fields) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String text = rawText == null ? "" : rawText.toLowerCase(Locale.ROOT);
        for (String keyword : List.of("您已支付", "You've sent", "You have sent", "Payment successful")) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                values.add(keyword);
            }
        }
        if (values.isEmpty() && fields.successDetected()) {
            values.add("Payment successful");
        }
        return List.copyOf(values);
    }

    private static PaymentProofTemplateCommand normalized(PaymentProofTemplateCommand command) {
        if (command == null
            || isBlank(command.bankCode())
            || isBlank(command.bankName())
            || isBlank(command.templateName())
            || !List.of("active", "inactive", "draft").contains(trimLower(command.status()))
            || !validJson(command.layoutJson())) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
        return new PaymentProofTemplateCommand(
            trimLower(command.bankCode()),
            trim(command.bankName()),
            trim(command.locale()) == null ? "zh-CN" : trim(command.locale()),
            trim(command.templateName()),
            trimLower(command.status()),
            Math.max(1, Math.min(command.priority() <= 0 ? 100 : command.priority(), 999)),
            command.layoutJson().trim(),
            command.version()
        );
    }

    private static Optional<String> extractByPatterns(String rawText, List<String> patterns) {
        for (String pattern : patterns) {
            Matcher matcher = compile(pattern).matcher(rawText == null ? "" : rawText);
            if (matcher.find()) {
                String value = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
                if (!isBlank(value)) {
                    return Optional.of(PaymentReferencePattern.normalize(value));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BigDecimal> extractAmountByPatterns(String rawText, List<String> patterns) {
        for (String pattern : patterns) {
            Matcher matcher = compile(pattern).matcher(rawText == null ? "" : rawText);
            if (matcher.find()) {
                String value = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
                Optional<BigDecimal> parsed = parseAmount(value);
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
        }
        return Optional.empty();
    }

    private static Pattern compile(String pattern) {
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
    }

    private static Optional<BigDecimal> parseAmount(String value) {
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

    private static BigDecimal boostedConfidence(
        BigDecimal current,
        String extractedReference,
        BigDecimal extractedAmount,
        boolean successDetected
    ) {
        BigDecimal score = current == null ? BigDecimal.ZERO : current;
        if (!isBlank(extractedReference)) {
            score = score.add(CONFIDENCE_BOOST);
        }
        if (extractedAmount != null) {
            score = score.add(new BigDecimal("0.0800"));
        }
        if (successDetected) {
            score = score.add(new BigDecimal("0.0500"));
        }
        return score.min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
    }

    private static String templateRawJson(String rawJson, PaymentProofTemplate template) {
        try {
            ObjectNode root = isBlank(rawJson)
                ? OBJECT_MAPPER.createObjectNode()
                : (ObjectNode) OBJECT_MAPPER.readTree(rawJson);
            root.put("templateId", template.id().toString());
            root.put("templateName", template.templateName());
            root.put("templateSource", template.source());
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception exception) {
            return "{\"templateId\":\"" + template.id() + "\",\"templateSource\":\"" + template.source() + "\"}";
        }
    }

    private static boolean containsAny(String rawText, List<String> keywords) {
        String lower = rawText == null ? "" : rawText.toLowerCase();
        return keywords.stream().map(String::toLowerCase).anyMatch(lower::contains);
    }

    private static boolean validJson(String value) {
        try {
            OBJECT_MAPPER.readTree(value == null || value.isBlank() ? "{}" : value);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static String fileDigest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("sha_256_required", exception);
        }
    }

    private static void validateActor(StoreScope scope, CurrentActor actor) {
        if (scope == null
            || actor == null
            || actor.tenantId() == null
            || !actor.tenantId().equals(scope.tenantId().value())
            || !actor.canAccessStore(scope.storeId().value())) {
            throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static boolean allowedContentType(String contentType) {
        return List.of("image/png", "image/jpeg", "image/webp").contains(trimLower(contentType));
    }

    private static String suffix(String fileName) {
        String safe = trimLower(fileName);
        if (safe == null || !safe.contains(".")) {
            return ".img";
        }
        String suffix = safe.substring(safe.lastIndexOf('.'));
        return suffix.matches("\\.[a-z0-9]{2,5}") ? suffix : ".img";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
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

    private record TemplateLayout(
        List<String> matchKeywords,
        List<String> successKeywords,
        List<String> referencePatterns,
        List<String> amountPatterns
    ) {
        static TemplateLayout from(String layoutJson) {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(isBlank(layoutJson) ? "{}" : layoutJson);
                return new TemplateLayout(
                    strings(root.path("matchKeywords")),
                    strings(root.path("successKeywords")),
                    strings(root.path("referencePatterns")),
                    strings(root.path("amountPatterns"))
                );
            } catch (Exception exception) {
                return new TemplateLayout(List.of(), List.of(), List.of(), List.of());
            }
        }

        boolean matches(String lowerRawText) {
            if (matchKeywords.isEmpty()) {
                return false;
            }
            return matchKeywords.stream().map(String::toLowerCase).anyMatch(lowerRawText::contains);
        }

        private static List<String> strings(JsonNode node) {
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> values = new java.util.ArrayList<>();
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
            return List.copyOf(values);
        }
    }
}
