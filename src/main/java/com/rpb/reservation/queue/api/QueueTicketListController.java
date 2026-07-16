package com.rpb.reservation.queue.api;

import com.rpb.reservation.appgate.guard.RequireAppGate;
import com.rpb.reservation.queue.application.QueueTicketListResult;
import com.rpb.reservation.queue.application.query.QueueTicketListQuery;
import com.rpb.reservation.queue.application.service.QueueTicketListApplicationService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/queue-tickets")
public class QueueTicketListController {
    private static final String VIEW_PERMISSION = "queue.view";
    private static final Set<String> ALLOWED_ROLES = Set.of("tenant_admin", "store_manager", "store_staff");

    private final QueueTicketListApplicationService applicationService;
    private final CurrentActorProvider currentActorProvider;
    private final QueueTicketListApiMapper apiMapper;
    private final QueueTicketListApiErrorMapper errorMapper;

    @Autowired
    public QueueTicketListController(
        QueueTicketListApplicationService applicationService,
        CurrentActorProvider currentActorProvider,
        QueueTicketListApiMapper apiMapper,
        QueueTicketListApiErrorMapper errorMapper
    ) {
        this.applicationService = applicationService;
        this.currentActorProvider = currentActorProvider;
        this.apiMapper = apiMapper;
        this.errorMapper = errorMapper;
    }

    @GetMapping
    @RequireAppGate(appKey = "reservation_queue", permission = VIEW_PERMISSION)
    public ResponseEntity<?> listQueueTickets(
        @PathVariable UUID storeId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "limit", required = false) String limit,
        @RequestParam(value = "offset", required = false) String offset,
        @RequestParam(value = "tableArea", required = false) String tableArea,
        @RequestParam(value = "partySize", required = false) String partySize,
        @RequestParam(value = "phone", required = false) String phone
    ) {
        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        if (currentActor.isEmpty()) {
            return errorMapper.toResponse(QueueTicketListApiErrorCode.FORBIDDEN);
        }
        CurrentActor actor = currentActor.get();
        if (!hasAllowedRole(actor) || !actor.hasPermission(VIEW_PERMISSION)) {
            return errorMapper.toResponse(QueueTicketListApiErrorCode.FORBIDDEN);
        }
        if (!actor.canAccessStore(storeId)) {
            return errorMapper.toResponse(QueueTicketListApiErrorCode.STORE_SCOPE_MISMATCH);
        }

        QueueTicketListQuery query = apiMapper.toQuery(
            storeId,
            status,
            limit,
            offset,
            tableArea,
            partySize,
            phone,
            actor
        );
        QueueTicketListResult result = applicationService.listQueueTickets(query);
        if (!result.success()) {
            return errorMapper.toResponse(result);
        }

        return ResponseEntity.ok(apiMapper.toResponse(result));
    }

    private static boolean hasAllowedRole(CurrentActor actor) {
        return actor.roles().stream().anyMatch(ALLOWED_ROLES::contains);
    }
}
