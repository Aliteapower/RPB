package com.rpb.reservation.appgate.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.appgate.api.AppGateApiErrorMapper;
import com.rpb.reservation.appgate.application.AppGateDenialAuditService;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AppGateWebMvcConfiguration implements WebMvcConfigurer {
    private final AppGateService appGateService;
    private final AppGateDenialAuditService denialAuditService;
    private final CurrentActorProvider currentActorProvider;
    private final AppGateApiErrorMapper errorMapper;
    private final ObjectMapper objectMapper;

    public AppGateWebMvcConfiguration(
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
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AppGateInterceptor(appGateService, denialAuditService, currentActorProvider, errorMapper, objectMapper));
    }
}
