package com.rpb.reservation.queuedisplay.application;

public record QueueDisplayAdSlide(
    String slideId,
    String title,
    String subtitle,
    String tagline,
    String mediaKind,
    String mediaUrl,
    String altText
) {
    public QueueDisplayAdSlide(String slideId, String title, String subtitle, String tagline) {
        this(slideId, title, subtitle, tagline, null, null, null);
    }

    public static QueueDisplayAdSlide media(
        String slideId,
        String mediaKind,
        String mediaUrl,
        String altText,
        String title
    ) {
        return new QueueDisplayAdSlide(slideId, title, null, null, mediaKind, mediaUrl, altText);
    }
}
