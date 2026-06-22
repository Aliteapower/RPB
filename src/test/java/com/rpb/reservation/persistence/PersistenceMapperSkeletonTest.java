package com.rpb.reservation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistenceMapperSkeletonTest {

    private static final List<String> MAPPERS = List.of(
        "com.rpb.reservation.tenant.persistence.mapper.TenantMapper",
        "com.rpb.reservation.store.persistence.mapper.StoreMapper",
        "com.rpb.reservation.store.persistence.mapper.StorePolicyMapper",
        "com.rpb.reservation.store.persistence.mapper.StoreAreaMapper",
        "com.rpb.reservation.table.persistence.mapper.DiningTableMapper",
        "com.rpb.reservation.table.persistence.mapper.TableGroupMapper",
        "com.rpb.reservation.table.persistence.mapper.TableLockMapper",
        "com.rpb.reservation.customer.persistence.mapper.CustomerMapper",
        "com.rpb.reservation.reservation.persistence.mapper.ReservationMapper",
        "com.rpb.reservation.reservation.persistence.mapper.ReservationPreassignmentMapper",
        "com.rpb.reservation.queue.persistence.mapper.QueueGroupMapper",
        "com.rpb.reservation.queue.persistence.mapper.QueueTicketMapper",
        "com.rpb.reservation.walkin.persistence.mapper.WalkInMapper",
        "com.rpb.reservation.seating.persistence.mapper.SeatingMapper",
        "com.rpb.reservation.seating.persistence.mapper.SeatingResourceMapper",
        "com.rpb.reservation.cleaning.persistence.mapper.CleaningMapper",
        "com.rpb.reservation.turnover.persistence.mapper.TurnoverMapper",
        "com.rpb.reservation.audit.persistence.mapper.BusinessEventMapper",
        "com.rpb.reservation.audit.persistence.mapper.StateTransitionLogMapper",
        "com.rpb.reservation.audit.persistence.mapper.AuditLogMapper",
        "com.rpb.reservation.idempotency.persistence.mapper.IdempotencyMapper",
        "com.rpb.reservation.i18n.persistence.mapper.ReasonCodeMapper",
        "com.rpb.reservation.i18n.persistence.mapper.I18nMessageMapper"
    );

    @Test
    void mapperSkeletonsExposeOnlyConversionMethods() throws Exception {
        for (String mapperName : MAPPERS) {
            Class<?> mapperClass = Class.forName(mapperName);

            assertThat(mapperClass.isInterface()).isTrue();
            assertThat(methodNames(mapperClass)).contains("toDomain", "toEntity");
            assertThat(methodNames(mapperClass)).doesNotContain("findById", "save", "assignTable", "validateTransition");
        }
    }

    @Test
    void mapperBoundaryTypesExistForXorGenericTargetAndJsonbMapping() throws Exception {
        assertThat(Class.forName("com.rpb.reservation.common.persistence.mapper.SeatingSourceMapping").isRecord()).isTrue();
        assertThat(Class.forName("com.rpb.reservation.common.persistence.mapper.SeatingResourceTargetMapping").isRecord()).isTrue();
        assertThat(Class.forName("com.rpb.reservation.common.persistence.mapper.CleaningResourceTargetMapping").isRecord()).isTrue();
        assertThat(Class.forName("com.rpb.reservation.common.persistence.mapper.TargetRef").isRecord()).isTrue();
        assertThat(Class.forName("com.rpb.reservation.common.persistence.mapper.MetadataPayload").isRecord()).isTrue();
        assertThat(Class.forName("com.rpb.reservation.common.persistence.mapper.SnapshotPayload").isRecord()).isTrue();
    }

    private static List<String> methodNames(Class<?> mapperClass) {
        return List.of(mapperClass.getDeclaredMethods()).stream()
            .map(Method::getName)
            .toList();
    }
}
