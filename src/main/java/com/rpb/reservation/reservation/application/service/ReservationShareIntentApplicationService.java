package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort;
import com.rpb.reservation.audit.domain.AuditLog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.ReservationShareInfo;
import com.rpb.reservation.reservation.application.ReservationShareInfoError;
import com.rpb.reservation.reservation.application.ReservationShareInfoResult;
import com.rpb.reservation.reservation.application.ReservationShareIntentResult;
import com.rpb.reservation.reservation.application.command.ReservationShareIntentCommand;
import com.rpb.reservation.reservation.application.query.ReservationShareInfoQuery;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationShareIntentApplicationService {
    private static final Set<String> CHANNELS = Set.of("whatsapp", "wechat", "system_share", "copy_link");

    private final ReservationShareInfoApplicationService shareInfoService;
    private final AuditLogRepositoryPort auditLogRepository;

    public ReservationShareIntentApplicationService(
        ReservationShareInfoApplicationService shareInfoService,
        AuditLogRepositoryPort auditLogRepository
    ) {
        this.shareInfoService = shareInfoService;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public ReservationShareIntentResult recordIntent(ReservationShareIntentCommand command) {
        ReservationShareInfoError validationError = validate(command);
        if (validationError != null) {
            return ReservationShareIntentResult.failure(validationError);
        }

        ReservationShareInfoResult shareInfoResult = shareInfoService.getShareInfo(new ReservationShareInfoQuery(
            command.tenantId(),
            command.storeId(),
            command.reservationId(),
            command.actorId(),
            command.actorType(),
            command.publicShareBaseUrl(),
            command.locale()
        ));
        if (!shareInfoResult.success()) {
            return ReservationShareIntentResult.failure(shareInfoResult.error());
        }

        try {
            StoreScope scope = new StoreScope(new TenantId(command.tenantId()), new StoreId(command.storeId()));
            auditLogRepository.append(scope, auditLog(command, shareInfoResult.shareInfo()));
            return ReservationShareIntentResult.success(command.channel().trim());
        } catch (RuntimeException exception) {
            return ReservationShareIntentResult.failure(ReservationShareInfoError.PERSISTENCE_ERROR);
        }
    }

    private static AuditLog auditLog(ReservationShareIntentCommand command, ReservationShareInfo info) {
        return new AuditLog(
            UUID.randomUUID(),
            "reservation.share_intent",
            "reservation",
            command.reservationId(),
            "staff",
            command.actorType(),
            command.actorId(),
            metadata(command.channel().trim(), info)
        );
    }

    private static String metadata(String channel, ReservationShareInfo info) {
        return """
            {"channel":"%s","customerPhoneAvailable":%s,"customerMaskedPhone":"%s","senderLabel":"%s","shareToken":"%s","sharePath":"%s","canOpenWhatsAppLink":%s,"canOpenWechatLink":%s}
            """.formatted(
            json(channel),
            info.customerPhoneAvailable(),
            json(info.customerMaskedPhone()),
            json(info.senderLabel()),
            json(info.shareToken()),
            json(info.sharePath()),
            info.canOpenWhatsAppLink(),
            info.canOpenWechatLink()
        ).trim();
    }

    private static ReservationShareInfoError validate(ReservationShareIntentCommand command) {
        if (
            command == null
                || command.tenantId() == null
                || command.storeId() == null
                || command.reservationId() == null
                || command.actorId() == null
                || !hasText(command.actorType())
                || !hasText(command.publicShareBaseUrl())
                || !hasText(command.channel())
                || !CHANNELS.contains(command.channel().trim())
        ) {
            return ReservationShareInfoError.INVALID_COMMAND;
        }
        return null;
    }

    private static String json(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
