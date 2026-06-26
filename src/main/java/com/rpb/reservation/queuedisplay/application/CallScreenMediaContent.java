package com.rpb.reservation.queuedisplay.application;

import org.springframework.core.io.Resource;

public record CallScreenMediaContent(Resource resource, String contentType, long byteSize) {
}
