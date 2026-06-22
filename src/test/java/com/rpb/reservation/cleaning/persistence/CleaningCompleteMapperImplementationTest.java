package com.rpb.reservation.cleaning.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpb.reservation.cleaning.domain.Cleaning;
import com.rpb.reservation.cleaning.persistence.entity.CleaningEntity;
import com.rpb.reservation.cleaning.persistence.mapper.DefaultCleaningMapper;
import com.rpb.reservation.cleaning.status.CleaningStatus;
import com.rpb.reservation.cleaning.value.CleaningId;
import com.rpb.reservation.common.persistence.mapper.CleaningResourceTargetMapping;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.seating.value.SeatingId;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CleaningCompleteMapperImplementationTest {

    private final TenantId tenantId = new TenantId(UUID.randomUUID());
    private final StoreId storeId = new StoreId(UUID.randomUUID());
    private final StoreScope scope = new StoreScope(tenantId, storeId);

    @Test
    void cleaningMapperPreservesDiningTableTargetXorBoundary() {
        UUID tableId = UUID.randomUUID();
        Cleaning cleaning = new Cleaning(
            new CleaningId(UUID.randomUUID()),
            scope,
            new SeatingId(UUID.randomUUID()),
            "dining_table",
            tableId,
            CleaningStatus.CLEANING
        );

        DefaultCleaningMapper mapper = new DefaultCleaningMapper();
        CleaningEntity entity = mapper.toEntity(cleaning);

        assertThat(entity.getResourceType()).isEqualTo("dining_table");
        assertThat(entity.getTableId()).isEqualTo(tableId);
        assertThat(entity.getTableGroupId()).isNull();
        assertThat(entity.getStatus()).isEqualTo("cleaning");

        CleaningResourceTargetMapping target = mapper.toTargetMapping(entity);
        assertThat(target.resourceType()).isEqualTo("dining_table");
        assertThat(target.tableId()).isEqualTo(tableId);
        assertThat(target.tableGroupId()).isNull();
        assertThat(mapper.toDomain(entity).resourceId()).isEqualTo(tableId);
    }

    @Test
    void cleaningMapperPreservesTableGroupTargetXorBoundary() {
        UUID tableGroupId = UUID.randomUUID();
        Cleaning cleaning = new Cleaning(
            new CleaningId(UUID.randomUUID()),
            scope,
            new SeatingId(UUID.randomUUID()),
            "table_group",
            tableGroupId,
            CleaningStatus.RELEASED
        );

        DefaultCleaningMapper mapper = new DefaultCleaningMapper();
        CleaningEntity entity = mapper.toEntity(cleaning);

        assertThat(entity.getResourceType()).isEqualTo("table_group");
        assertThat(entity.getTableId()).isNull();
        assertThat(entity.getTableGroupId()).isEqualTo(tableGroupId);
        assertThat(entity.getStatus()).isEqualTo("released");
        assertThat(entity.getCompletedAt()).isNotNull();
        assertThat(entity.getReleasedAt()).isNotNull();

        CleaningResourceTargetMapping target = mapper.toTargetMapping(entity);
        assertThat(target.resourceType()).isEqualTo("table_group");
        assertThat(target.tableId()).isNull();
        assertThat(target.tableGroupId()).isEqualTo(tableGroupId);
        assertThat(mapper.toDomain(entity).resourceId()).isEqualTo(tableGroupId);
    }

    @Test
    void cleaningMapperRejectsInvalidResourceXorShape() {
        CleaningEntity invalid = CleaningEntity.of(
            UUID.randomUUID(),
            tenantId.value(),
            storeId.value(),
            UUID.randomUUID(),
            "dining_table",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "cleaning",
            OffsetDateTime.now(),
            null,
            null,
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            null,
            0
        );

        assertThatThrownBy(() -> new DefaultCleaningMapper().toTargetMapping(invalid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid_cleaning_resource_target");
    }
}
