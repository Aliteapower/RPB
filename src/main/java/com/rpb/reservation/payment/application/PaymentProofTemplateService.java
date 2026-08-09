package com.rpb.reservation.payment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
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
