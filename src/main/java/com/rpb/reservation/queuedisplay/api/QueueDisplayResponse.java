package com.rpb.reservation.queuedisplay.api;

import com.rpb.reservation.queuedisplay.application.QueueDisplayAdSlide;
import com.rpb.reservation.queuedisplay.application.QueueDisplayAds;
import com.rpb.reservation.queuedisplay.application.QueueDisplayCurrentCall;
import com.rpb.reservation.queuedisplay.application.QueueDisplayResult;
import com.rpb.reservation.queuedisplay.application.QueueDisplayWaitingPreviewItem;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QueueDisplayResponse(
    boolean success,
    Instant serverNow,
    StoreTimeResponse storeTime,
    CurrentCallResponse currentCall,
    WaitingResponse waiting,
    AdsResponse ads
) {
    public static QueueDisplayResponse from(QueueDisplayResult result) {
        return new QueueDisplayResponse(
            true,
            result.serverNow(),
            new StoreTimeResponse(result.storeTimezone(), result.storeTimeText(), result.businessDate()),
            CurrentCallResponse.from(result.currentCall()),
            new WaitingResponse(
                result.waitingCount(),
                result.waitingPreview().stream().map(WaitingPreviewItemResponse::from).toList()
            ),
            AdsResponse.from(result.ads())
        );
    }

    public record StoreTimeResponse(String timezone, String timeText, LocalDate businessDate) {
    }

    public record CurrentCallResponse(
        UUID queueTicketId,
        String displayNumber,
        String customerDisplayName,
        Integer partySize,
        String partySizeGroup,
        Instant calledAt,
        Instant holdUntilAt
    ) {
        private static CurrentCallResponse from(QueueDisplayCurrentCall call) {
            if (call == null) {
                return null;
            }
            return new CurrentCallResponse(
                call.queueTicketId(),
                call.displayNumber(),
                call.customerDisplayName(),
                call.partySize(),
                call.partySizeGroup(),
                call.calledAt(),
                call.holdUntilAt()
            );
        }
    }

    public record WaitingResponse(int count, List<WaitingPreviewItemResponse> preview) {
    }

    public record WaitingPreviewItemResponse(
        String displayNumber,
        String customerDisplayName,
        Integer partySize,
        String partySizeGroup
    ) {
        private static WaitingPreviewItemResponse from(QueueDisplayWaitingPreviewItem item) {
            return new WaitingPreviewItemResponse(item.displayNumber(), item.customerDisplayName(), item.partySize(), item.partySizeGroup());
        }
    }

    public record AdsResponse(String mode, int slideDurationSeconds, int statePollSeconds, List<AdSlideResponse> slides) {
        private static AdsResponse from(QueueDisplayAds ads) {
            return new AdsResponse(
                ads.mode(),
                ads.slideDurationSeconds(),
                ads.statePollSeconds(),
                ads.slides().stream().map(AdSlideResponse::from).toList()
            );
        }
    }

    public record AdSlideResponse(
        String slideId,
        String title,
        String subtitle,
        String tagline,
        String mediaKind,
        String mediaUrl,
        String altText
    ) {
        private static AdSlideResponse from(QueueDisplayAdSlide slide) {
            return new AdSlideResponse(
                slide.slideId(),
                slide.title(),
                slide.subtitle(),
                slide.tagline(),
                slide.mediaKind(),
                slide.mediaUrl(),
                slide.altText()
            );
        }
    }
}
