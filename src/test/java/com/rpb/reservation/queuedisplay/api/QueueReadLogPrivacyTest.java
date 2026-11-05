package com.rpb.reservation.queuedisplay.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rpb.reservation.i18n.application.I18nMessageResolver;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.application.query.QueueTicketListQuery;
import com.rpb.reservation.queue.application.service.QueueTicketListApplicationService;
import com.rpb.reservation.queuedisplay.application.*;
import com.rpb.reservation.queuedisplay.persistence.QueueDisplayRepository;
import com.rpb.reservation.store.application.port.out.StoreRepositoryPort;
import com.rpb.reservation.store.domain.Store;
import com.rpb.reservation.store.value.StoreId;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class QueueReadLogPrivacyTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void databaseExceptionDetailsNeverEnterReadFailureLogs(boolean display) {
        var tenant = UUID.randomUUID(); var store = UUID.randomUUID(); var actor = UUID.randomUUID();
        var stores = mock(StoreRepositoryPort.class);
        when(stores.findById(any())).thenReturn(Optional.of(Store.skeleton(new StoreId(store),
            new TenantId(tenant), "test", "Asia/Singapore", "zh-CN", "active")));
        var failure = new IllegalStateException("SQL parameters: +6591234567 alice@example.com token=secret-test-value");
        var type = display ? QueueDisplayApplicationService.class : QueueTicketListApplicationService.class;
        var logger = (Logger) LoggerFactory.getLogger(type);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            if (display) {
                var repository = mock(QueueDisplayRepository.class);
                when(repository.findCurrentCall(any(), any(), any())).thenThrow(failure);
                var service = new QueueDisplayApplicationService(stores, repository, mock(I18nMessageResolver.class), Clock.systemUTC());
                assertThat(service.getState(new QueueDisplayQuery(tenant, store, actor, "staff")).error())
                    .isEqualTo(QueueDisplayError.PERSISTENCE_ERROR);
            } else {
                var repository = mock(QueueTicketRepositoryPort.class);
                when(repository.findQueueTicketList(any(), any(), any(), anyInt(), anyInt(), any(), any(), any())).thenThrow(failure);
                var service = new QueueTicketListApplicationService(stores, repository, Clock.systemUTC());
                assertThat(service.listQueueTickets(new QueueTicketListQuery(tenant, store, actor, "staff", null, null, null, null, null, null)).error())
                    .isEqualTo(com.rpb.reservation.queue.application.QueueTicketListError.PERSISTENCE_ERROR);
            }
            assertThat(appender.list).hasSize(1);
            var event = appender.list.get(0);
            assertThat(event.getFormattedMessage()).contains("read_failed", "IllegalStateException")
                .doesNotContain("+6591234567", "alice@example.com", "secret-test-value", "SQL parameters");
            assertThat(event.getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender); appender.stop();
        }
    }
}
