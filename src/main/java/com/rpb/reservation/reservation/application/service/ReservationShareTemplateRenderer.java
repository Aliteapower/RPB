package com.rpb.reservation.reservation.application.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ReservationShareTemplateRenderer {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");
    private final Set<String> allowedVariables;

    public ReservationShareTemplateRenderer() {
        this(ReservationShareTemplateCatalog.supportedVariables());
    }

    public ReservationShareTemplateRenderer(List<String> allowedVariables) {
        this.allowedVariables = Set.copyOf(allowedVariables == null ? List.of() : allowedVariables);
    }

    public String render(String template, Map<String, String> variables) {
        List<String> unknownVariables = unknownVariables(template);
        if (!unknownVariables.isEmpty()) {
            throw new IllegalArgumentException("reservation_share_template_unknown_variables:" + String.join(",", unknownVariables));
        }
        String source = template == null ? "" : template;
        Matcher matcher = VARIABLE_PATTERN.matcher(source);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            String replacement = variables == null ? "" : variables.getOrDefault(variableName, "");
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement == null ? "" : replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString().stripTrailing();
    }

    public List<String> unknownVariables(String template) {
        if (template == null || template.isBlank()) {
            return List.of();
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        LinkedHashSet<String> unknown = new LinkedHashSet<>();
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            if (!allowedVariables.contains(variableName)) {
                unknown.add(variableName);
            }
        }
        return List.copyOf(unknown);
    }
}
