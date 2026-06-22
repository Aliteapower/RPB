package com.rpb.reservation.appgate.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateAccessRequest;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.appgate.domain.AppGateDenyReason;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

public class AppGateInterceptor implements HandlerInterceptor {
    private final AppGateService appGateService;
    private final AppGateDenialAuditService denialAuditService;
    private final CurrentActorProvider currentActorProvider;
    private final AppGateApiErrorMapper errorMapper;
    private final ObjectMapper objectMapper;

    public AppGateInterceptor(
        AppGateService appGateService,
        AppGateDenialAuditService denialAuditService,
        CurrentActorProvider currentActorProvider,
        AppGateApiErrorMapper errorMapper,
        ObjectMapper objectMapper
    ) {
        this.appGateService = appGateService;
        this.denialAuditService = denialAuditService;
        this.currentActorProvider = currentActorProvider;
        this.errorMapper = errorMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireAppGate annotation = annotationFor(handlerMethod);
        if (annotation == null) {
            return true;
        }

        Optional<CurrentActor> currentActor = currentActorProvider.currentActor();
        UUID storeId = storeId(request).orElse(null);
        if (currentActor.isEmpty() || storeId == null) {
            AppGateDecision decision = AppGateDecision.deny(
                annotation.appKey(),
                currentActor.map(CurrentActor::tenantId).orElse(null),
                storeId,
                annotation.permission(),
                AppGateDenyReason.PERMISSION_DENIED
            );
            denialAuditService.recordDenial(decision, currentActor.orElse(null));
            writeDenied(response, decision);
            return false;
        }

        CurrentActor actor = currentActor.get();
        AppGateDecision decision = appGateService.evaluate(new AppGateAccessRequest(
            annotation.appKey(),
            actor.tenantId(),
            storeId,
            annotation.permission(),
            actor
        ));
        if (!decision.allowed()) {
            denialAuditService.recordDenial(decision, actor);
            writeDenied(response, decision);
            return false;
        }
        return true;
    }

    private static RequireAppGate annotationFor(HandlerMethod handlerMethod) {
        RequireAppGate methodAnnotation = handlerMethod.getMethodAnnotation(RequireAppGate.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return handlerMethod.getBeanType().getAnnotation(RequireAppGate.class);
    }

    private static Optional<UUID> storeId(HttpServletRequest request) {
        Object rawVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(rawVariables instanceof Map<?, ?> variables)) {
            return Optional.empty();
        }
        Object rawStoreId = variables.get("storeId");
        if (rawStoreId == null) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(rawStoreId.toString()));
    }

    private void writeDenied(HttpServletResponse response, AppGateDecision decision) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), errorMapper.toBody(decision));
    }
}
