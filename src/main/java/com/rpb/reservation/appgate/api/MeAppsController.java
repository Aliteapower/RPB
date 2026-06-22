package com.rpb.reservation.appgate.api;

import com.rpb.reservation.appgate.application.AppGateAppEntry;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeAppsController {
    private final AppGateService appGateService;
    private final CurrentActorProvider currentActorProvider;

    public MeAppsController(AppGateService appGateService, CurrentActorProvider currentActorProvider) {
        this.appGateService = appGateService;
        this.currentActorProvider = currentActorProvider;
    }

    @GetMapping("/api/me/apps")
    public MeAppsResponse apps(@RequestParam UUID storeId) {
        return currentActorProvider.currentActor()
            .map(actor -> responseFor(actor, storeId))
            .orElseGet(MeAppsResponse::empty);
    }

    private MeAppsResponse responseFor(CurrentActor actor, UUID storeId) {
        List<AppGateAppEntry> apps = appGateService.visibleApps(actor, storeId);
        return MeAppsResponse.of(apps);
    }
}
