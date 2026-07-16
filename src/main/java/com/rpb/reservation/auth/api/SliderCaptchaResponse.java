package com.rpb.reservation.auth.api;

import java.time.Instant;

public record SliderCaptchaResponse(
    boolean success,
    ChallengeBody challenge
) {
    public record ChallengeBody(
        String challengeId,
        String type,
        int imageWidth,
        int imageHeight,
        int pieceSize,
        int pieceY,
        Instant expiresAt,
        String backgroundImage,
        String pieceImage,
        String hint
    ) {
    }
}
