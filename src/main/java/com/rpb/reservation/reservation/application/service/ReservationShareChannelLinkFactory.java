package com.rpb.reservation.reservation.application.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.web.util.UriComponentsBuilder;

public class ReservationShareChannelLinkFactory {
    public static final String WECHAT_LINK = "weixin://";
    private static final Pattern E164_PATTERN = Pattern.compile("^[+][1-9][0-9]{1,14}$");

    public String fullShareMessage(String shareText, String publicShareUrl) {
        String text = clean(shareText);
        String url = clean(publicShareUrl);
        if (text.isEmpty()) {
            return url;
        }
        return url.isEmpty() ? text : text + "\n\n" + url;
    }

    public String publicShareUrl(String publicShareBaseUrl, String sharePath) {
        if (!hasText(publicShareBaseUrl) || !hasText(sharePath)) {
            return "";
        }
        try {
            return UriComponentsBuilder.fromUriString(publicShareBaseUrl.trim())
                .replacePath(sharePath.trim())
                .replaceQuery(null)
                .fragment(null)
                .build()
                .toUriString();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    public String whatsappLink(String customerPhoneE164, String message) {
        if (!isE164(customerPhoneE164) || !hasText(message)) {
            return null;
        }
        String recipient = customerPhoneE164.trim().substring(1);
        return "https://wa.me/" + recipient + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
    }

    public boolean isE164(String value) {
        return hasText(value) && E164_PATTERN.matcher(value.trim()).matches();
    }

    private static String clean(String value) {
        return hasText(value) ? value.trim() : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
