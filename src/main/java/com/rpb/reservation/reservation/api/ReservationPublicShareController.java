package com.rpb.reservation.reservation.api;

import com.rpb.reservation.reservation.application.ReservationPublicShareResult;
import com.rpb.reservation.reservation.application.service.ReservationPublicShareApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/reservation-shares")
public class ReservationPublicShareController {
    private final ReservationPublicShareApplicationService applicationService;
    private final ReservationPublicShareApiMapper apiMapper;

    public ReservationPublicShareController(
        ReservationPublicShareApplicationService applicationService,
        ReservationPublicShareApiMapper apiMapper
    ) {
        this.applicationService = applicationService;
        this.apiMapper = apiMapper;
    }

    @GetMapping("/{token}")
    public ResponseEntity<?> publicShare(@PathVariable String token) {
        ReservationPublicShareResult result = applicationService.getPublicShare(token);
        if (!result.success()) {
            return apiMapper.toErrorResponse(result);
        }
        return ResponseEntity.ok(apiMapper.toResponse(result));
    }
}
