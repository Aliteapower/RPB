package com.rpb.reservation.appgate.api;

import com.rpb.reservation.appgate.application.AppGateAppEntry;
import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.application.ProductRouteAccessQuery;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeAppsController {
    private final AppGateService appGateService;
    private final CurrentActorProvider currentActorProvider;
    private final ProductRouteAccessQuery routeAccess;

    public MeAppsController(AppGateService appGateService, CurrentActorProvider currentActorProvider) {
        this(appGateService,currentActorProvider,new ProductRouteAccessQuery(appGateService));
    }

    @Autowired
    public MeAppsController(AppGateService appGateService, CurrentActorProvider currentActorProvider, ProductRouteAccessQuery routeAccess) {
        this.appGateService = appGateService;
        this.currentActorProvider = currentActorProvider;
        this.routeAccess = routeAccess;
    }

    @GetMapping("/api/me/apps")
    public MeAppsResponse apps(@RequestParam UUID storeId,@RequestParam(defaultValue="false") boolean includeAccess) {
        return currentActorProvider.currentActor()
            .map(actor -> responseFor(actor, storeId,includeAccess))
            .orElseGet(() -> includeAccess ? MeAppsResponse.empty().withProductAccess(List.of()) : MeAppsResponse.empty());
    }

    public MeAppsResponse apps(UUID storeId) { return apps(storeId,false); }

    private MeAppsResponse responseFor(CurrentActor actor, UUID storeId,boolean includeAccess) {
        List<AppGateAppEntry> apps = appGateService.visibleApps(actor, storeId);
        var response=MeAppsResponse.of(apps,appGateService.historyApps(actor,storeId));
        return includeAccess ? response.withProductAccess(routeAccess.find(actor,storeId)) : response;
    }
}
