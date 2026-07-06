package com.rpb.reservation.queuedisplay.application;

public record QueueDisplayAdSlide(
    String slideId,
    String title,
    String subtitle,
    String tagline,
    String mediaKind,
    String mediaUrl,
    String altText,
    String titleI18nKey,
    String subtitleI18nKey,
    String taglineI18nKey
) {
    public QueueDisplayAdSlide(String slideId, String title, String subtitle, String tagline) {
        this(slideId, title, subtitle, tagline, null, null, null, null, null, null);
    }

    public static QueueDisplayAdSlide text(
        String slideId,
        String title,
        String subtitle,
        String tagline,
        String titleI18nKey,
        String subtitleI18nKey,
        String taglineI18nKey
    ) {
        return new QueueDisplayAdSlide(
            slideId,
            title,
            subtitle,
            tagline,
            null,
            null,
            null,
            titleI18nKey,
            subtitleI18nKey,
            taglineI18nKey
        );
    }

    public static QueueDisplayAdSlide media(
        String slideId,
        String mediaKind,
        String mediaUrl,
        String altText,
        String title
    ) {
        return new QueueDisplayAdSlide(slideId, title, null, null, mediaKind, mediaUrl, altText, null, null, null);
    }

    public QueueDisplayAdSlide withText(String title, String subtitle, String tagline) {
        return new QueueDisplayAdSlide(
            slideId,
            title,
            subtitle,
            tagline,
            mediaKind,
            mediaUrl,
            altText,
            titleI18nKey,
            subtitleI18nKey,
            taglineI18nKey
        );
    }
}
