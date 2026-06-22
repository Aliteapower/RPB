package com.rpb.reservation.appgate.api;

import com.rpb.reservation.appgate.domain.AppGateDecision;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class AppGateApiErrorMapper {

    public ResponseEntity<AppGateApiErrorResponse> toResponse(AppGateDecision decision) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(toBody(decision));
    }

    public AppGateApiErrorResponse toBody(AppGateDecision decision) {
        String code = decision.denyReason() == null ? "PERMISSION_DENIED" : decision.denyReason().name();
        String messageKey = decision.messageKey() == null ? "appgate.permission_denied" : decision.messageKey();
        Map<String, Object> details = decision.details() == null ? Map.of() : decision.details();
        return AppGateApiErrorResponse.of(code, messageKey, details);
    }
}
