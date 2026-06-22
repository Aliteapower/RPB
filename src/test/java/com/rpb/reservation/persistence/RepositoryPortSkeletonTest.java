package com.rpb.reservation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryPortSkeletonTest {

    private static final List<String> PORTS = List.of(
        "com.rpb.reservation.tenant.application.port.out.TenantRepositoryPort",
        "com.rpb.reservation.store.application.port.out.StoreRepositoryPort",
        "com.rpb.reservation.table.application.port.out.DiningTableRepositoryPort",
        "com.rpb.reservation.table.application.port.out.TableGroupRepositoryPort",
        "com.rpb.reservation.table.application.port.out.TableLockRepositoryPort",
        "com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort",
        "com.rpb.reservation.reservation.application.port.out.ReservationRepositoryPort",
        "com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort",
        "com.rpb.reservation.walkin.application.port.out.WalkInRepositoryPort",
        "com.rpb.reservation.seating.application.port.out.SeatingRepositoryPort",
        "com.rpb.reservation.cleaning.application.port.out.CleaningRepositoryPort",
        "com.rpb.reservation.turnover.application.port.out.TurnoverRepositoryPort",
        "com.rpb.reservation.audit.application.port.out.BusinessEventRepositoryPort",
        "com.rpb.reservation.audit.application.port.out.StateTransitionLogRepositoryPort",
        "com.rpb.reservation.audit.application.port.out.AuditLogRepositoryPort",
        "com.rpb.reservation.idempotency.application.port.out.IdempotencyRepositoryPort",
        "com.rpb.reservation.i18n.application.port.out.ReasonCodeRepositoryPort",
        "com.rpb.reservation.i18n.application.port.out.I18nMessageRepositoryPort"
    );

    @Test
    void repositoryPortsAreInterfacesAndDoNotExposePersistenceOrSpringDataTypes() throws Exception {
        for (String portName : PORTS) {
            Class<?> portClass = Class.forName(portName);

            assertThat(portClass.isInterface()).isTrue();
            assertThat(Arrays.stream(portClass.getInterfaces()).map(Class::getName))
                .noneMatch(name -> name.startsWith("org.springframework.data"));

            for (Method method : portClass.getDeclaredMethods()) {
                assertThat(method.getReturnType().getName()).doesNotContain(".persistence.entity.");
                assertThat(method.getReturnType().getName()).doesNotStartWith("org.springframework.data");
                assertThat(Arrays.stream(method.getParameterTypes()).map(Class::getName))
                    .noneMatch(name -> name.contains(".persistence.entity.") || name.startsWith("org.springframework.data"));
            }
        }
    }

    @Test
    void repositoryPortMethodsRequireExplicitScope() throws Exception {
        for (String portName : PORTS) {
            Class<?> portClass = Class.forName(portName);

            assertThat(portClass.getDeclaredMethods()).isNotEmpty();
            for (Method method : portClass.getDeclaredMethods()) {
                assertThat(Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName))
                    .as(portName + "." + method.getName())
                    .anyMatch(name -> name.equals("TenantScope") || name.equals("StoreScope") || name.equals("PlatformScope"));
            }
        }
    }

    @Test
    void repositoryPortsAvoidMechanicalCrudShape() throws Exception {
        for (String portName : PORTS) {
            Class<?> portClass = Class.forName(portName);

            assertThat(Arrays.stream(portClass.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("findAll", "delete", "deleteById", "update", "saveAll", "getOne");
        }
    }
}
