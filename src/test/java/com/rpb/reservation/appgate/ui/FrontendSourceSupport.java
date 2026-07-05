package com.rpb.reservation.appgate.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FrontendSourceSupport {
    private static final Path GENERATED_ZH_CN = Path.of("src", "i18n", "locales", "generated-zh-CN.ts");
    private static final Pattern GENERATED_ENTRY = Pattern.compile("\"generated\\.([a-z0-9-]+)\\.[0-9]+\":\\s*\".*\",?");
    private static final Pattern GENERATED_ENTRY_WITH_VALUE =
        Pattern.compile("\"(generated\\.([a-z0-9-]+)\\.[0-9]+)\":\\s*\"(.*)\",?");

    private FrontendSourceSupport() {
    }

    static String readString(Path path) throws IOException {
        String source = Files.readString(path)
            .replace("\r\n", "\n")
            .replace('\r', '\n');

        String pageSlug = generatedPageSlug(path);
        if (pageSlug == null || !Files.exists(GENERATED_ZH_CN)) {
            return source;
        }

        Map<String, String> generatedEntries = generatedLocaleEntries(pageSlug);
        return source
            + "\n"
            + renderedGeneratedSource(source, generatedEntries)
            + "\n"
            + formattedGeneratedEntries(generatedEntries);
    }

    private static String generatedPageSlug(Path path) {
        String normalized = path.toString().replace('\\', '/');
        if (!normalized.startsWith("src/pages/") || !normalized.endsWith("Page.vue")) {
            return null;
        }

        String fileName = path.getFileName().toString();
        String baseName = fileName.substring(0, fileName.length() - "Page.vue".length());
        return baseName
            .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
            .toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> generatedLocaleEntries(String pageSlug) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();

        for (String line : Files.readAllLines(GENERATED_ZH_CN)) {
            String trimmed = line.trim();
            Matcher matcher = GENERATED_ENTRY.matcher(trimmed);
            if (matcher.matches() && matcher.group(1).equals(pageSlug)) {
                Matcher valueMatcher = GENERATED_ENTRY_WITH_VALUE.matcher(trimmed);
                if (valueMatcher.matches()) {
                    entries.put(valueMatcher.group(1), valueMatcher.group(3));
                }
            }
        }

        return entries;
    }

    private static String renderedGeneratedSource(String source, Map<String, String> entries) {
        String rendered = source;

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String quotedCall = "gt('" + key + "')";

            rendered = rendered.replaceAll(
                "\\{\\{\\s*" + Pattern.quote(quotedCall) + "\\s*\\}\\}",
                Matcher.quoteReplacement(value)
            );
            rendered = rendered.replaceAll(
                ":(aria-label|placeholder|title)=\"" + Pattern.quote(quotedCall) + "\"",
                "$1=\"" + Matcher.quoteReplacement(value) + "\""
            );
            rendered = rendered.replaceAll(
                ":([a-zA-Z0-9-]+)=\"" + Pattern.quote(quotedCall) + "\"",
                "$1=\"" + Matcher.quoteReplacement(value) + "\""
            );
            rendered = rendered.replace(quotedCall, "'" + value + "'");
        }

        return rendered;
    }

    private static String formattedGeneratedEntries(Map<String, String> entries) {
        StringBuilder formatted = new StringBuilder();

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            formatted
                .append("  \"")
                .append(entry.getKey())
                .append("\": \"")
                .append(entry.getValue())
                .append("\",\n");
        }

        return formatted.toString();
    }
}
