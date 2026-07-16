package com.rpb.reservation.reservation.application.service;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.reservation.application.ReservationMealPeriod;
import com.rpb.reservation.reservation.application.ReservationMealPeriodCommand;
import com.rpb.reservation.reservation.application.StoreReservationMealPeriodSettings;
import com.rpb.reservation.reservation.application.port.out.ReservationMealPeriodRepositoryPort;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationMealPeriodManagementService {
    private final ReservationMealPeriodRepositoryPort repository;

    public ReservationMealPeriodManagementService(ReservationMealPeriodRepositoryPort repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ReservationMealPeriod> getPlatformSeedPeriods() {
        return repository.findPlatformSeedPeriods();
    }

    @Transactional
    public List<ReservationMealPeriod> replacePlatformSeedPeriods(List<ReservationMealPeriodCommand> periods) {
        List<ReservationMealPeriodCommand> normalized = normalizePeriods(periods);
        try {
            return repository.replacePlatformSeedPeriods(normalized);
        } catch (RuntimeException exception) {
            throw new ReservationMealPeriodServiceException(ReservationMealPeriodServiceErrorCode.PERSISTENCE_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public StoreReservationMealPeriodSettings getStoreSettings(StoreScope scope) {
        boolean usePlatformSeed = repository.findUsePlatformSeed(scope).orElse(true);
        List<ReservationMealPeriod> platformPeriods = repository.findPlatformSeedPeriods();
        List<ReservationMealPeriod> storePeriods = repository.findStorePeriods(scope);
        return new StoreReservationMealPeriodSettings(
            usePlatformSeed,
            platformPeriods,
            storePeriods,
            usePlatformSeed ? platformPeriods : storePeriods
        );
    }

    @Transactional
    public StoreReservationMealPeriodSettings updateStoreSettings(
        StoreScope scope,
        Boolean usePlatformSeed,
        Boolean copyPlatformSeed,
        List<ReservationMealPeriodCommand> periods
    ) {
        boolean usePlatform = usePlatformSeed == null || usePlatformSeed;
        try {
            if (usePlatform) {
                repository.upsertStoreMealPeriodSetting(scope, true);
            } else if (Boolean.TRUE.equals(copyPlatformSeed)) {
                repository.upsertStoreMealPeriodSetting(scope, false);
                repository.copyPlatformSeedPeriodsToStore(scope);
            } else {
                repository.upsertStoreMealPeriodSetting(scope, false);
                repository.replaceStorePeriods(scope, normalizePeriods(periods));
            }
        } catch (ReservationMealPeriodServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ReservationMealPeriodServiceException(ReservationMealPeriodServiceErrorCode.PERSISTENCE_ERROR);
        }
        return getStoreSettings(scope);
    }

    private static List<ReservationMealPeriodCommand> normalizePeriods(List<ReservationMealPeriodCommand> periods) {
        if (periods == null || periods.isEmpty()) {
            throw new ReservationMealPeriodServiceException(ReservationMealPeriodServiceErrorCode.REQUEST_INVALID);
        }
        List<ReservationMealPeriodCommand> normalized = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        int index = 0;
        for (ReservationMealPeriodCommand period : periods) {
            String key = requiredText(period == null ? null : period.periodKey());
            if (!keys.add(key)) {
                throw new ReservationMealPeriodServiceException(ReservationMealPeriodServiceErrorCode.REQUEST_INVALID);
            }
            String displayName = requiredText(period.displayName());
            LocalTime start = requiredTime(period.startLocalTime());
            LocalTime end = requiredTime(period.endLocalTime());
            boolean crossesNextDay = Boolean.TRUE.equals(period.crossesNextDay());
            if (!crossesNextDay && end.isBefore(start)) {
                throw new ReservationMealPeriodServiceException(ReservationMealPeriodServiceErrorCode.REQUEST_INVALID);
            }
            int interval = period.slotIntervalMinutes() == null ? 30 : period.slotIntervalMinutes();
            if (interval < 5 || interval > 240) {
                throw new ReservationMealPeriodServiceException(ReservationMealPeriodServiceErrorCode.REQUEST_INVALID);
            }
            String status = period.status() == null || period.status().isBlank() ? "active" : period.status().trim();
            if (!"active".equals(status) && !"disabled".equals(status)) {
                throw new ReservationMealPeriodServiceException(ReservationMealPeriodServiceErrorCode.REQUEST_INVALID);
            }
            normalized.add(new ReservationMealPeriodCommand(
                key,
                displayName,
                start,
                end,
                crossesNextDay,
                interval,
                status,
                period.sortOrder() == null ? index * 10 : period.sortOrder(),
                period.version()
            ));
            index++;
        }
        return normalized;
    }

    private static LocalTime requiredTime(LocalTime value) {
        if (value == null) {
            throw new ReservationMealPeriodServiceException(ReservationMealPeriodServiceErrorCode.REQUEST_INVALID);
        }
        return value;
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new ReservationMealPeriodServiceException(ReservationMealPeriodServiceErrorCode.REQUEST_INVALID);
        }
        return value.trim();
    }
}
