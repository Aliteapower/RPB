package com.rpb.reservation.queue.persistence.adapter;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.queue.application.port.out.QueueTicketRepositoryPort;
import com.rpb.reservation.queue.application.port.out.QueueTicketListRow;
import com.rpb.reservation.queue.application.port.out.QueueTicketListRows;
import com.rpb.reservation.queue.domain.QueueTicket;
import com.rpb.reservation.queue.persistence.entity.QueueTicketEntity;
import com.rpb.reservation.queue.persistence.mapper.QueueTicketMapper;
import com.rpb.reservation.queue.persistence.repository.QueueTicketJpaRepository;
import com.rpb.reservation.queue.persistence.repository.QueueTicketListProjection;
import com.rpb.reservation.queue.policy.QueueTicketNumberConflictException;
import com.rpb.reservation.queue.status.QueueTicketStatus;
import com.rpb.reservation.queue.value.QueueTicketId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class QueueTicketPersistenceAdapter implements QueueTicketRepositoryPort {

    private final QueueTicketJpaRepository repository;
    private final QueueTicketMapper mapper;
    private final EntityManager entityManager;

    public QueueTicketPersistenceAdapter(
        QueueTicketJpaRepository repository,
        QueueTicketMapper mapper,
        EntityManager entityManager
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<QueueTicket> findById(StoreScope scope, QueueTicketId queueTicketId) {
        return repository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
            queueTicketId.value(),
            scope.tenantId().value(),
            scope.storeId().value()
        ).map(mapper::toDomain);
    }

    @Override
    public List<QueueTicket> findActiveQueue(StoreScope scope, UUID queueGroupId, BusinessDate businessDate) {
        return repository.findQueueForNumbering(
            scope.tenantId().value(),
            scope.storeId().value(),
            queueGroupId,
            businessDate.value()
        ).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<QueueTicket> findNextCallable(StoreScope scope, UUID queueGroupId, BusinessDate businessDate) {
        return repository.findNextWaiting(
            scope.tenantId().value(),
            scope.storeId().value(),
            queueGroupId,
            businessDate.value()
        ).map(mapper::toDomain);
    }

    @Override
    public Optional<QueueTicket> findActiveByReservationId(StoreScope scope, UUID reservationId) {
        return repository.findActiveByReservationId(
            scope.tenantId().value(),
            scope.storeId().value(),
            reservationId
        ).map(mapper::toDomain);
    }

    @Override
    public boolean existsActiveSourceTicket(StoreScope scope, String sourceType, UUID sourceId) {
        return repository.existsActiveSourceTicket(
            scope.tenantId().value(),
            scope.storeId().value(),
            sourceType,
            sourceId
        );
    }

    @Override
    public QueueTicketListRows findQueueTicketList(StoreScope scope, QueueTicketStatus status, int limit, int offset) {
        String statusCode = status == null ? null : status.code();
        List<QueueTicketListRow> rows = repository.findQueueTicketListRows(
            scope.tenantId().value(),
            scope.storeId().value(),
            statusCode,
            limit,
            offset
        ).stream().map(QueueTicketPersistenceAdapter::toListRow).toList();
        int total = repository.countQueueTicketListRows(
            scope.tenantId().value(),
            scope.storeId().value(),
            statusCode
        );
        return new QueueTicketListRows(rows, total);
    }

    @Override
    public QueueTicket save(StoreScope scope, QueueTicket queueTicket) {
        try {
            QueueTicketEntity mapped = mapper.toEntity(queueTicket);
            Optional<QueueTicketEntity> existing = repository.findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(
                queueTicket.id().value(),
                scope.tenantId().value(),
                scope.storeId().value()
            );
            if (existing.isPresent()) {
                return mapper.toDomain(repository.save(existingEntity(mapped, existing.get())));
            }
            QueueTicketEntity newEntity = newEntity(mapped);
            entityManager.persist(newEntity);
            entityManager.flush();
            return mapper.toDomain(newEntity);
        } catch (DataIntegrityViolationException | PersistenceException exception) {
            throw new QueueTicketNumberConflictException("queue_ticket_persistence_conflict");
        }
    }

    private static QueueTicketListRow toListRow(QueueTicketListProjection projection) {
        return new QueueTicketListRow(
            projection.getQueueTicketId(),
            projection.getQueueTicketNumber(),
            projection.getQueueTicketStatus(),
            projection.getPartySize(),
            projection.getPartySizeGroup(),
            projection.getReservationId(),
            projection.getReservationCode(),
            projection.getReservationStatus(),
            projection.getCustomerName(),
            projection.getCustomerPhoneE164(),
            projection.getCreatedAt(),
            projection.getCalledAt(),
            projection.getExpiresAt()
        );
    }

    private static QueueTicketEntity newEntity(QueueTicketEntity entity) {
        return QueueTicketEntity.of(
            entity.getId(),
            entity.getTenantId(),
            entity.getStoreId(),
            entity.getQueueGroupId(),
            entity.getCustomerId(),
            entity.getReservationId(),
            entity.getWalkInId(),
            entity.getTicketNumber(),
            entity.getPartySize(),
            entity.getBusinessDate(),
            entity.getStatus(),
            entity.getQueuePosition(),
            entity.getCalledAt(),
            entity.getSkippedAt(),
            entity.getRejoinedAt(),
            entity.getExpiresAt(),
            entity.getCancellationReasonCode(),
            entity.getNote(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt(),
            null
        );
    }

    private static QueueTicketEntity existingEntity(QueueTicketEntity mapped, QueueTicketEntity existing) {
        return QueueTicketEntity.of(
            mapped.getId(),
            mapped.getTenantId(),
            mapped.getStoreId(),
            mapped.getQueueGroupId(),
            mapped.getCustomerId(),
            mapped.getReservationId(),
            mapped.getWalkInId(),
            mapped.getTicketNumber(),
            mapped.getPartySize(),
            mapped.getBusinessDate(),
            mapped.getStatus(),
            mapped.getQueuePosition(),
            mapped.getCalledAt(),
            mapped.getSkippedAt(),
            mapped.getRejoinedAt(),
            mapped.getExpiresAt(),
            mapped.getCancellationReasonCode(),
            mapped.getNote(),
            existing.getCreatedAt(),
            mapped.getUpdatedAt(),
            existing.getDeletedAt(),
            existing.getVersion()
        );
    }
}
