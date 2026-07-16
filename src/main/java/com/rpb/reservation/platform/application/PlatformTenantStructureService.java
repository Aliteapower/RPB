package com.rpb.reservation.platform.application;

import com.rpb.reservation.platform.persistence.PlatformTenantRepository;
import com.rpb.reservation.platform.persistence.PlatformTenantStructureRepository;
import com.rpb.reservation.platform.persistence.PlatformStoreAdminAccountRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformTenantStructureService {
    private static final Set<String> OPERATING_ENTITY_STATUSES = Set.of("active", "inactive");
    private static final Set<String> STORE_STATUSES = Set.of("created", "active", "inactive");
    private static final String DEFAULT_STATUS = "active";
    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final String DEFAULT_TIMEZONE = "Asia/Singapore";
    private static final String DEFAULT_DATE_FORMAT = "DD-MM-YYYY";
    private static final String DEFAULT_TIME_FORMAT = "HH:mm";
    private static final String DEFAULT_CURRENCY = "SGD";
    private static final String PASSWORD_PATTERN = "^[A-Za-z0-9]{6}$";

    private final PlatformTenantRepository tenantRepository;
    private final PlatformTenantStructureRepository structureRepository;
    private final PlatformStoreAdminAccountRepository storeAdminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTenantAuditService auditService;
    private final PublicHostBindingService publicHostBindingService;

    public PlatformTenantStructureService(
        PlatformTenantRepository tenantRepository,
        PlatformTenantStructureRepository structureRepository,
        PlatformStoreAdminAccountRepository storeAdminAccountRepository,
        PasswordEncoder passwordEncoder,
        PlatformTenantAuditService auditService,
        PublicHostBindingService publicHostBindingService
    ) {
        this.tenantRepository = tenantRepository;
        this.structureRepository = structureRepository;
        this.storeAdminAccountRepository = storeAdminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.publicHostBindingService = publicHostBindingService;
    }

    @Transactional(readOnly = true)
    public List<PlatformOperatingEntity> listOperatingEntities(UUID tenantId) {
        requireTenant(tenantId);
        return structureRepository.listOperatingEntities(tenantId);
    }

    @Transactional
    public PlatformOperatingEntity createOperatingEntity(
        UUID tenantId,
        PlatformOperatingEntityMutationCommand request,
        PlatformOperator operator
    ) {
        requireTenant(tenantId);
        NormalizedOperatingEntityInput input = normalizeOperatingEntityCreate(request);
        try {
            PlatformOperatingEntity entity = structureRepository.insertOperatingEntity(
                UUID.randomUUID(),
                tenantId,
                input.entityCode(),
                input.displayName(),
                input.status(),
                input.defaultLocale(),
                input.contactPhone(),
                input.address(),
                input.principalName()
            );
            auditService.recordOperatingEntityCreated(entity, operator);
            return entity;
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.OPERATING_ENTITY_CODE_CONFLICT);
        }
    }

    @Transactional
    public PlatformOperatingEntity updateOperatingEntity(
        UUID tenantId,
        UUID operatingEntityId,
        PlatformOperatingEntityMutationCommand request,
        PlatformOperator operator
    ) {
        requireTenant(tenantId);
        PlatformOperatingEntity existing = structureRepository.findOperatingEntity(tenantId, operatingEntityId)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.OPERATING_ENTITY_NOT_FOUND));
        NormalizedOperatingEntityInput input = normalizeOperatingEntityUpdate(existing, request);
        try {
            PlatformOperatingEntity entity = structureRepository.updateOperatingEntity(
                    tenantId,
                    operatingEntityId,
                    input.entityCode(),
                    input.displayName(),
                    input.status(),
                    input.defaultLocale(),
                    input.contactPhone(),
                    input.address(),
                    input.principalName()
                )
                .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.OPERATING_ENTITY_NOT_FOUND));
            auditService.recordOperatingEntityUpdated(existing, entity, operator);
            return entity;
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.OPERATING_ENTITY_CODE_CONFLICT);
        }
    }

    @Transactional(readOnly = true)
    public List<PlatformStore> listStores(UUID tenantId) {
        requireTenant(tenantId);
        return structureRepository.listStores(tenantId);
    }

    @Transactional
    public PlatformStore createStore(UUID tenantId, PlatformStoreMutationCommand request, PlatformOperator operator) {
        requireTenant(tenantId);
        NormalizedStoreInput input = normalizeStoreCreate(tenantId, request);
        try {
            PlatformStore store = structureRepository.insertStore(
                UUID.randomUUID(),
                tenantId,
                input.operatingEntityId(),
                input.storeCode(),
                input.storeName(),
                input.status(),
                input.timezone(),
                input.locale(),
                input.dateFormat(),
                input.timeFormat(),
                input.currency()
            );
            upsertStoreAdminIfRequested(tenantId, store.id(), input);
            UUID hostAliasId = structureRepository.upsertStoreHostAlias(
                tenantId,
                store.id(),
                store.storeCode(),
                store.status()
            );
            publicHostBindingService.syncBinding(
                hostAliasId,
                tenantId,
                store.storeCode(),
                "store",
                store.status()
            );
            auditService.recordStoreCreated(store, operator);
            return store;
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.STORE_CODE_CONFLICT);
        }
    }

    @Transactional
    public PlatformStore updateStore(
        UUID tenantId,
        UUID storeId,
        PlatformStoreMutationCommand request,
        PlatformOperator operator
    ) {
        requireTenant(tenantId);
        PlatformStore existing = structureRepository.findStore(tenantId, storeId)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.STORE_NOT_FOUND));
        NormalizedStoreInput input = normalizeStoreUpdate(tenantId, existing, request);
        try {
            PlatformStore store = structureRepository.updateStore(
                    tenantId,
                    storeId,
                    input.operatingEntityId(),
                    input.storeCode(),
                    input.storeName(),
                    input.status(),
                    input.timezone(),
                    input.locale(),
                    input.dateFormat(),
                    input.timeFormat(),
                    input.currency()
                )
                .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.STORE_NOT_FOUND));
            upsertStoreAdminIfRequested(tenantId, store.id(), input);
            UUID hostAliasId = structureRepository.upsertStoreHostAlias(
                tenantId,
                store.id(),
                store.storeCode(),
                store.status()
            );
            publicHostBindingService.syncBinding(
                hostAliasId,
                tenantId,
                store.storeCode(),
                "store",
                store.status()
            );
            auditService.recordStoreUpdated(existing, store, operator);
            return store;
        } catch (DataIntegrityViolationException exception) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.STORE_CODE_CONFLICT);
        }
    }

    @Transactional
    public PlatformStore deleteStore(UUID tenantId, UUID storeId, PlatformOperator operator) {
        requireTenant(tenantId);
        if (storeId == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        PlatformStore existing = structureRepository.findStore(tenantId, storeId)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.STORE_NOT_FOUND));
        PlatformStore deleted = structureRepository.softDeleteStore(tenantId, storeId)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.STORE_NOT_FOUND));
        structureRepository.archiveStoreHostAliases(tenantId, storeId)
            .forEach(publicHostBindingService::archiveBinding);
        storeAdminAccountRepository.archiveStoreManagers(tenantId, storeId);
        structureRepository.archiveStoreAccountAccess(tenantId, storeId);
        structureRepository.refreshDefaultStoreReferences(tenantId, storeId);
        structureRepository.cancelStoreSubscriptionItems(tenantId, storeId);
        structureRepository.refreshStoreSubscriptionAggregates(tenantId, storeId);
        auditService.recordStoreDeleted(existing, deleted, operator);
        return deleted;
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        tenantRepository.findById(tenantId, false)
            .orElseThrow(() -> new PlatformTenantServiceException(PlatformTenantServiceErrorCode.TENANT_NOT_FOUND));
    }

    private NormalizedOperatingEntityInput normalizeOperatingEntityCreate(PlatformOperatingEntityMutationCommand request) {
        if (request == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return new NormalizedOperatingEntityInput(
            requiredText(request.entityCode()),
            requiredText(request.displayName()),
            normalizeOperatingEntityStatus(firstText(request.status(), DEFAULT_STATUS)),
            optionalText(request.defaultLocale()),
            optionalText(request.contactPhone()),
            optionalText(request.address()),
            optionalText(request.principalName())
        );
    }

    private NormalizedOperatingEntityInput normalizeOperatingEntityUpdate(
        PlatformOperatingEntity existing,
        PlatformOperatingEntityMutationCommand request
    ) {
        if (request == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return new NormalizedOperatingEntityInput(
            firstText(request.entityCode(), existing.entityCode()),
            firstText(request.displayName(), existing.displayName()),
            normalizeOperatingEntityStatus(firstText(request.status(), existing.status())),
            optionalTextOrExisting(request.defaultLocale(), existing.defaultLocale()),
            optionalTextOrExisting(request.contactPhone(), existing.contactPhone()),
            optionalTextOrExisting(request.address(), existing.address()),
            optionalTextOrExisting(request.principalName(), existing.principalName())
        );
    }

    private NormalizedStoreInput normalizeStoreCreate(UUID tenantId, PlatformStoreMutationCommand request) {
        if (request == null || request.operatingEntityId() == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        validateOperatingEntity(tenantId, request.operatingEntityId());
        return new NormalizedStoreInput(
            request.operatingEntityId(),
            requiredText(request.storeCode()),
            requiredText(request.storeName()),
            normalizeStoreStatus(firstText(request.status(), DEFAULT_STATUS)),
            firstText(request.timezone(), DEFAULT_TIMEZONE),
            firstText(request.locale(), DEFAULT_LOCALE),
            firstText(request.dateFormat(), DEFAULT_DATE_FORMAT),
            firstText(request.timeFormat(), DEFAULT_TIME_FORMAT),
            firstText(request.currency(), DEFAULT_CURRENCY),
            normalizeCreateAdminUsername(request.adminUsername(), request.adminPassword(), request.storeCode()),
            optionalPassword(request.adminPassword())
        );
    }

    private NormalizedStoreInput normalizeStoreUpdate(
        UUID tenantId,
        PlatformStore existing,
        PlatformStoreMutationCommand request
    ) {
        if (request == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        UUID operatingEntityId = request.operatingEntityId() == null
            ? existing.operatingEntityId()
            : request.operatingEntityId();
        if (operatingEntityId == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        validateOperatingEntity(tenantId, operatingEntityId);
        return new NormalizedStoreInput(
            operatingEntityId,
            firstText(request.storeCode(), existing.storeCode()),
            firstText(request.storeName(), existing.storeName()),
            normalizeStoreStatus(firstText(request.status(), existing.status())),
            firstText(request.timezone(), existing.timezone()),
            firstText(request.locale(), existing.locale()),
            firstText(request.dateFormat(), existing.dateFormat()),
            firstText(request.timeFormat(), existing.timeFormat()),
            firstText(request.currency(), existing.currency()),
            optionalText(request.adminUsername()),
            optionalPassword(request.adminPassword())
        );
    }

    private void upsertStoreAdminIfRequested(UUID tenantId, UUID storeId, NormalizedStoreInput input) {
        if (input.adminUsername() == null && input.adminPassword() == null) {
            return;
        }
        if (
            !storeAdminAccountRepository.upsertStoreManager(
                tenantId,
                storeId,
                input.adminUsername(),
                storeAdminDisplayName(input.storeName()),
                input.adminPassword() == null ? null : passwordHash(input.adminPassword())
            )
        ) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
    }

    private void validateOperatingEntity(UUID tenantId, UUID operatingEntityId) {
        if (!structureRepository.activeOperatingEntityExists(tenantId, operatingEntityId)) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
    }

    private static String normalizeOperatingEntityStatus(String status) {
        String normalized = requiredText(status).toLowerCase(Locale.ROOT);
        if (!OPERATING_ENTITY_STATUSES.contains(normalized)) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String normalizeStoreStatus(String status) {
        String normalized = requiredText(status).toLowerCase(Locale.ROOT);
        if (!STORE_STATUSES.contains(normalized)) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private static String normalizeCreateAdminUsername(String adminUsername, String adminPassword, String storeCode) {
        String username = optionalText(adminUsername);
        String password = optionalText(adminPassword);
        if (username == null && password == null) {
            return null;
        }
        if (password == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return username == null ? requiredText(storeCode) : username;
    }

    private static String optionalPassword(String password) {
        String normalized = optionalText(password);
        if (normalized == null) {
            return null;
        }
        if (!normalized.matches(PASSWORD_PATTERN)) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private String passwordHash(String password) {
        return passwordEncoder.encode(password.toLowerCase(Locale.ROOT));
    }

    private static String storeAdminDisplayName(String storeName) {
        return requiredText(storeName) + " Manager";
    }

    private static String firstText(String first, String fallback) {
        String normalized = optionalText(first);
        return normalized == null ? requiredText(fallback) : normalized;
    }

    private static String optionalTextOrExisting(String value, String existing) {
        String normalized = optionalText(value);
        return normalized == null ? existing : normalized;
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requiredText(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new PlatformTenantServiceException(PlatformTenantServiceErrorCode.REQUEST_INVALID);
        }
        return normalized;
    }

    private record NormalizedOperatingEntityInput(
        String entityCode,
        String displayName,
        String status,
        String defaultLocale,
        String contactPhone,
        String address,
        String principalName
    ) {
    }

    private record NormalizedStoreInput(
        UUID operatingEntityId,
        String storeCode,
        String storeName,
        String status,
        String timezone,
        String locale,
        String dateFormat,
        String timeFormat,
        String currency,
        String adminUsername,
        String adminPassword
    ) {
    }
}
