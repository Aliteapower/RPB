package com.rpb.reservation.tenantadmin.application;

import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.auth.authorization.StaffPermissionCatalog;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiErrorCode;
import com.rpb.reservation.tenantadmin.api.TenantAdminApiException;
import com.rpb.reservation.tenantadmin.persistence.StaffPermissionsRepository;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.rpb.reservation.tenantadmin.api.TenantAdminApiErrorCode.*;

@Service
@Transactional(readOnly = true)
public class StaffPermissionsService {
    public record Choice(String code, String labelZh, String labelEn, List<String> requires, boolean grantable) {}
    public record Group(String key, String appKey, String labelZh, String labelEn, boolean provisioned, boolean enabled, List<Choice> permissions) {}
    public record View(boolean success, long version, boolean inherited, List<String> permissions, List<Group> groups) {}
    private final StaffPermissionsRepository repository;
    private final AppGateService gate;
    private final StaffPermissionsAuditService audit;
    public StaffPermissionsService(StaffPermissionsRepository repository, AppGateService gate, StaffPermissionsAuditService audit) {
        this.repository = repository; this.gate = gate; this.audit = audit;
    }
    public View get(StoreScope scope, CurrentActor actor, UUID staffId) {
        var target = requireTarget(scope, actor, staffId, false);
        var stored = repository.find(scope, staffId);
        return view(scope, actor, stored.map(StaffPermissionsRepository.Configuration::version).orElse(0L), stored.isEmpty(),
            stored.map(StaffPermissionsRepository.Configuration::permissions).orElseGet(() -> managed(target.permissions())));
    }
    @Transactional
    public View put(StoreScope scope, CurrentActor actor, UUID staffId, Long expectedVersion, List<String> requested) {
        if (expectedVersion == null || expectedVersion < 0 || requested == null || requested.size() > StaffPermissionCatalog.permissions().size()
            || requested.stream().anyMatch(Objects::isNull) || !StaffPermissionCatalog.permissions().containsAll(requested)) throw error(REQUEST_INVALID);
        var target = requireTarget(scope, actor, staffId, true);
        var stored = repository.find(scope, staffId);
        long version = stored.map(StaffPermissionsRepository.Configuration::version).orElse(0L);
        var before = stored.map(StaffPermissionsRepository.Configuration::permissions).orElseGet(() -> managed(target.permissions()));
        var after = StaffPermissionCatalog.expand(new HashSet<>(requested));
        if (expectedVersion != version) {
            if (stored.isPresent() && before.equals(after)) return view(scope, actor, version, false, before);
            throw error(PERMISSION_VERSION_CONFLICT);
        }
        if (stored.isPresent() && before.equals(after)) return view(scope, actor, version, false, before);
        Set<String> additions = new HashSet<>(after); additions.removeAll(before);
        if (!actor.permissions().containsAll(additions)) throw error(FORBIDDEN);
        for (var group : StaffPermissionCatalog.groups()) {
            if (group.permissions().stream().anyMatch(p -> additions.contains(p.code()))
                && !gate.isStoreProductProvisioned(group.appKey(), scope.tenantId().value(), scope.storeId().value())) throw error(FORBIDDEN);
        }
        repository.save(scope, staffId, after, version + 1);
        audit.changed(scope, staffId, actor.actorId(), before, after, version + 1);
        return view(scope, actor, version + 1, false, after);
    }
    private StaffPermissionsRepository.Target requireTarget(StoreScope scope, CurrentActor actor, UUID staffId, boolean lock) {
        if (actor == null || actor.actorId() == null || !scope.tenantId().value().equals(actor.tenantId())
            || !actor.canAccessStore(scope.storeId().value()) || !actor.roles().contains("tenant_admin")
            || !actor.hasPermission("tenant.admin.manage") || "platform_admin".equals(actor.actorType()) || actor.actorId().equals(staffId)) throw error(FORBIDDEN);
        var target = repository.target(scope, staffId, lock).orElseThrow(() -> error(STAFF_NOT_FOUND));
        if (!"staff".equals(target.actorType()) || target.roles().stream().anyMatch(Set.of("tenant_admin", "store_manager", "platform_admin")::contains)) throw error(FORBIDDEN);
        return target;
    }
    private View view(StoreScope scope, CurrentActor actor, long version, boolean inherited, Set<String> selected) {
        Map<String, Boolean> provisioned = new HashMap<>(), enabled = new HashMap<>();
        var groups = StaffPermissionCatalog.groups().stream().map(group -> {
            boolean available = provisioned.computeIfAbsent(group.appKey(), key -> gate.isStoreProductProvisioned(key, scope.tenantId().value(), scope.storeId().value()));
            boolean active = enabled.computeIfAbsent(group.appKey(), key -> gate.evaluateCommercial(key, scope.tenantId().value(), scope.storeId().value()).allowed());
            var choices = group.permissions().stream().map(p -> new Choice(p.code(), p.labelZh(), p.labelEn(), p.requires(),
                available && actor.hasPermission(p.code()) && actor.permissions().containsAll(StaffPermissionCatalog.expand(Set.of(p.code()))))).toList();
            return new Group(group.key(), group.appKey(), group.labelZh(), group.labelEn(), available, active, choices);
        }).toList();
        return new View(true, version, inherited, selected.stream().sorted().toList(), groups);
    }
    private static Set<String> managed(Set<String> source) { var result = new HashSet<>(source); result.retainAll(StaffPermissionCatalog.permissions()); return Set.copyOf(result); }
    private static TenantAdminApiException error(TenantAdminApiErrorCode code) { return new TenantAdminApiException(code); }
}
