package com.rpb.reservation.reservation.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.reservation.application.ReservationArrivedDirectSeatingResult;
import com.rpb.reservation.reservation.application.ReservationArrivedToQueueResult;
import com.rpb.reservation.reservation.application.ReservationCheckInResult;
import com.rpb.reservation.reservation.application.ReservationCreateResult;
import com.rpb.reservation.reservation.application.command.CheckInReservationCommand;
import com.rpb.reservation.reservation.application.command.CreateReservationCommand;
import com.rpb.reservation.reservation.application.command.QueueArrivedReservationCommand;
import com.rpb.reservation.reservation.application.command.SeatArrivedReservationCommand;
import com.rpb.reservation.reservation.application.service.ReservationArrivedDirectSeatingApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationArrivedToQueueApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCheckInApplicationService;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}")
public class ReservationController {
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String CREATE_PERMISSION = "reservation.create";
    private static final String CHECK_IN_PERMISSION = "reservation.check_in";
    private static final String SEAT_PERMISSION = "reservation.seat";
    private static final String QUEUE_PERMISSION = "reservation.queue";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final ReservationCreateApplicationService applicationService;
    private final ReservationCheckInApplicationService checkInApplicationService;
    private final ReservationArrivedDirectSeatingApplicationService seatingApplicationService;
    private final ReservationArrivedToQueueApplicationService queueApplicationService;
    private final CurrentActorProvider currentActorProvider;
    private final ReservationApiMapper apiMapper;
    private final ReservationApiErrorMapper errorMapper;
    private final ReservationCheckInApiMapper checkInApiMapper;
    private final ReservationCheckInApiErrorMapper checkInErrorMapper;
    private final ReservationArrivedDirectSeatingApiMapper seatingApiMapper;
    private final ReservationArrivedDirectSeatingApiErrorMapper seatingErrorMapper;
    private final ReservationArrivedToQueueApiMapper queueApiMapper;
    private final ReservationArrivedToQueueApiErrorMapper queueErrorMapper;

    @Autowired
    public ReservationController(
        ReservationCreateApplicationService applicationService,
        ReservationCheckInApplicationService checkInApplicationService,
        ReservationArrivedDirectSeatingApplicationService seatingApplicationService,
        ReservationArrivedToQueueApplicationService queueApplicationService,
        CurrentActorProvider currentActorProvider,
        ReservationApiMapper apiMapper,
        ReservationApiErrorMapper errorMapper,
        ReservationCheckInApiMapper checkInApiMapper,
        ReservationCheckInApiErrorMapper checkInErrorMapper,
        ReservationArrivedDirectSeatingApiMapper seatingApiMapper,
        ReservationArrivedDirectSeatingApiErrorMapper seatingErrorMapper,
        ReservationArrivedToQueueApiMapper queueApiMapper,
        ReservationArrivedToQueueApiErrorMapper queueErrorMapper
    ) {
        this.applicationService = applicationService;
        this.checkInApplicationService = checkInApplicationService;
        this.seatingApplicationService = seatingApplicationService;
        this.queueApplicationService = queueApplicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
        this.checkInApiMapper = checkInApiMapper;
        this.checkInErrorMapper = checkInErrorMapper;
        this.seatingApiMapper = seatingApiMapper;
        this.seatingErrorMapper = seatingErrorMapper;
        this.queueApiMapper = queueApiMapper;
        this.queueErrorMapper = queueErrorMapper;
    }

    @PostMapping("/reservations")
    @RequireAppGate(appKey = "reservation_queue", permission = CREATE_PERMISSION)
    public ResponseEntity<?> createReservation(
        @PathVariable UUID storeId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody CreateReservationRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return errorMapper.toResponse(ReservationApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }

        CreateReservationRequest nonNullRequest = nonNull(request);
        Optional<ReservationApiErrorCode> validationError = nonNullRequest.validateContract();
        if (validationError.isPresent()) {
            return errorMapper.toResponse(validationError.get());
        }

        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(CREATE_PERMISSION)) {
            return errorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(ReservationApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        CreateReservationCommand command = apiMapper.toCommand(nonNullRequest, storeId, idempotencyKey, actor);
        ReservationCreateResult result = applicationService.createReservation(command);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }

        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(apiMapper.toResponse(result, nonNullRequest));
    }

    @PostMapping("/reservations/{reservationId}/check-in")
    @RequireAppGate(appKey = "reservation_queue", permission = CHECK_IN_PERMISSION)
    public ResponseEntity<?> checkInReservation(
        @PathVariable UUID storeId,
        @PathVariable UUID reservationId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody(required = false) CheckInReservationRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return checkInErrorMapper.toResponse(ReservationApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }

        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return checkInErrorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(CHECK_IN_PERMISSION)) {
            return checkInErrorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return checkInErrorMapper.toResponse(ReservationApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        CheckInReservationCommand command = checkInApiMapper.toCommand(
            nonNull(request),
            storeId,
            reservationId,
            idempotencyKey,
            actor
        );
        ReservationCheckInResult result = checkInApplicationService.checkInReservation(command);
        if (!result.success()) {
            return checkInErrorMapper.toResponse(result);
        }

        return ResponseEntity.ok(checkInApiMapper.toResponse(result));
    }

    @PostMapping("/reservations/{reservationId}/seating/direct")
    @RequireAppGate(appKey = "reservation_queue", permission = SEAT_PERMISSION)
    public ResponseEntity<?> seatArrivedReservation(
        @PathVariable UUID storeId,
        @PathVariable UUID reservationId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody(required = false) SeatArrivedReservationRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return seatingErrorMapper.toResponse(ReservationApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }

        SeatArrivedReservationRequest nonNullRequest = nonNull(request);
        Optional<ReservationApiErrorCode> validationError = nonNullRequest.validateContract();
        if (validationError.isPresent()) {
            return seatingErrorMapper.toResponse(validationError.get());
        }

        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return seatingErrorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(SEAT_PERMISSION)) {
            return seatingErrorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return seatingErrorMapper.toResponse(ReservationApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        SeatArrivedReservationCommand command = seatingApiMapper.toCommand(
            nonNullRequest,
            storeId,
            reservationId,
            idempotencyKey,
            actor
        );
        ReservationArrivedDirectSeatingResult result = seatingApplicationService.seatArrivedReservation(command);
        if (!result.success()) {
            return seatingErrorMapper.toResponse(result);
        }

        return ResponseEntity.ok(seatingApiMapper.toResponse(result));
    }

    @PostMapping("/reservations/{reservationId}/queue")
    @RequireAppGate(appKey = "reservation_queue", permission = QUEUE_PERMISSION)
    public ResponseEntity<?> queueArrivedReservation(
        @PathVariable UUID storeId,
        @PathVariable UUID reservationId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody(required = false) QueueArrivedReservationRequest request
    ) {
        if (!hasText(idempotencyKey)) {
            return queueErrorMapper.toResponse(ReservationApiErrorCode.MISSING_IDEMPOTENCY_KEY);
        }

        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return queueErrorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(QUEUE_PERMISSION)) {
            return queueErrorMapper.toResponse(ReservationApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return queueErrorMapper.toResponse(ReservationApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        QueueArrivedReservationCommand command = queueApiMapper.toCommand(
            nonNull(request),
            storeId,
            reservationId,
            idempotencyKey,
            actor
        );
        ReservationArrivedToQueueResult result = queueApplicationService.queueArrivedReservation(command);
        if (!result.success()) {
            return queueErrorMapper.toResponse(result);
        }

        return ResponseEntity.ok(queueApiMapper.toResponse(result));
    }

    private static CreateReservationRequest nonNull(CreateReservationRequest request) {
        return request == null ? new CreateReservationRequest(null, null, null, null, null, null, null, null) : request;
    }

    private static CheckInReservationRequest nonNull(CheckInReservationRequest request) {
        return request == null ? new CheckInReservationRequest(null, null, null) : request;
    }

    private static SeatArrivedReservationRequest nonNull(SeatArrivedReservationRequest request) {
        return request == null ? new SeatArrivedReservationRequest(null, null, null, null, null) : request;
    }

    private static QueueArrivedReservationRequest nonNull(QueueArrivedReservationRequest request) {
        return request == null ? new QueueArrivedReservationRequest(null, null, null) : request;
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
