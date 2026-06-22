package com.rpb.reservation.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersistenceEntitySkeletonTest {

    private static final Map<String, String> ENTITY_TABLES = Map.ofEntries(
        Map.entry("com.rpb.reservation.tenant.persistence.entity.TenantEntity", "tenants"),
        Map.entry("com.rpb.reservation.store.persistence.entity.StoreEntity", "stores"),
        Map.entry("com.rpb.reservation.store.persistence.entity.StorePolicyEntity", "store_policies"),
        Map.entry("com.rpb.reservation.store.persistence.entity.StoreAreaEntity", "store_areas"),
        Map.entry("com.rpb.reservation.table.persistence.entity.DiningTableEntity", "dining_tables"),
        Map.entry("com.rpb.reservation.table.persistence.entity.TableGroupEntity", "table_groups"),
        Map.entry("com.rpb.reservation.table.persistence.entity.TableGroupMemberEntity", "table_group_members"),
        Map.entry("com.rpb.reservation.table.persistence.entity.TableLockEntity", "table_locks"),
        Map.entry("com.rpb.reservation.customer.persistence.entity.CustomerEntity", "customers"),
        Map.entry("com.rpb.reservation.reservation.persistence.entity.ReservationEntity", "reservations"),
        Map.entry("com.rpb.reservation.reservation.persistence.entity.ReservationPreassignmentEntity", "reservation_preassignments"),
        Map.entry("com.rpb.reservation.queue.persistence.entity.QueueGroupEntity", "queue_groups"),
        Map.entry("com.rpb.reservation.queue.persistence.entity.QueueTicketEntity", "queue_tickets"),
        Map.entry("com.rpb.reservation.walkin.persistence.entity.WalkInEntity", "walk_ins"),
        Map.entry("com.rpb.reservation.seating.persistence.entity.SeatingEntity", "seatings"),
        Map.entry("com.rpb.reservation.seating.persistence.entity.SeatingResourceEntity", "seating_resources"),
        Map.entry("com.rpb.reservation.cleaning.persistence.entity.CleaningEntity", "cleanings"),
        Map.entry("com.rpb.reservation.turnover.persistence.entity.TurnoverEntity", "turnovers"),
        Map.entry("com.rpb.reservation.audit.persistence.entity.BusinessEventEntity", "business_events"),
        Map.entry("com.rpb.reservation.audit.persistence.entity.StateTransitionLogEntity", "state_transition_logs"),
        Map.entry("com.rpb.reservation.audit.persistence.entity.AuditLogEntity", "audit_logs"),
        Map.entry("com.rpb.reservation.idempotency.persistence.entity.IdempotencyRecordEntity", "idempotency_records"),
        Map.entry("com.rpb.reservation.i18n.persistence.entity.ReasonCodeEntity", "reason_codes"),
        Map.entry("com.rpb.reservation.i18n.persistence.entity.I18nMessageEntity", "i18n_message_catalog")
    );

    @Test
    void allPersistenceEntitiesMapToV001TablesAndExposeJpaConstructor() throws Exception {
        for (Map.Entry<String, String> entry : ENTITY_TABLES.entrySet()) {
            Class<?> entityClass = Class.forName(entry.getKey());

            assertThat(entityClass.getAnnotation(Entity.class)).isNotNull();
            assertThat(entityClass.getAnnotation(Table.class).name()).isEqualTo(entry.getValue());

            Constructor<?> constructor = entityClass.getDeclaredConstructor();
            assertThat(Modifier.isProtected(constructor.getModifiers())).isTrue();
        }
    }

    @Test
    void timestampDateAndJsonbPlaceholderMappingsStaySafe() throws Exception {
        assertThat(fieldType("com.rpb.reservation.reservation.persistence.entity.ReservationEntity", "reservedStartAt"))
            .isEqualTo(OffsetDateTime.class);
        assertThat(fieldType("com.rpb.reservation.reservation.persistence.entity.ReservationEntity", "businessDate"))
            .isEqualTo(LocalDate.class);
        assertThat(fieldType("com.rpb.reservation.audit.persistence.entity.AuditLogEntity", "metadata"))
            .isEqualTo(String.class);
        assertThat(fieldType("com.rpb.reservation.idempotency.persistence.entity.IdempotencyRecordEntity", "responseSnapshot"))
            .isEqualTo(String.class);
    }

    @Test
    void forbiddenEntitiesAreNotCreated() {
        assertThatThrownBy(() -> Class.forName("com.rpb.reservation.checkin.persistence.entity.CheckInEntity"))
            .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.rpb.reservation.customer.persistence.entity.MemberEntity"))
            .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.rpb.reservation.payment.persistence.entity.PaymentEntity"))
            .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.rpb.reservation.marketing.persistence.entity.MarketingEntity"))
            .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.rpb.reservation.pos.persistence.entity.PosEntity"))
            .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void entitySkeletonsDoNotExposeBusinessWorkflowMethods() throws Exception {
        for (String className : ENTITY_TABLES.keySet()) {
            Class<?> entityClass = Class.forName(className);

            assertThatCode(() -> entityClass.getDeclaredMethod("confirm"))
                .isInstanceOf(NoSuchMethodException.class);
            assertThatCode(() -> entityClass.getDeclaredMethod("call"))
                .isInstanceOf(NoSuchMethodException.class);
            assertThatCode(() -> entityClass.getDeclaredMethod("assignTable"))
                .isInstanceOf(NoSuchMethodException.class);
            assertThatCode(() -> entityClass.getDeclaredMethod("transitionTo", String.class))
                .isInstanceOf(NoSuchMethodException.class);
        }
    }

    private static Class<?> fieldType(String className, String fieldName) throws Exception {
        return Class.forName(className).getDeclaredField(fieldName).getType();
    }
}
