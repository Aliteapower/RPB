package com.rpb.reservation.queue.persistence.repository;

import com.rpb.reservation.queue.persistence.entity.QueueTicketEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueTicketJpaRepository extends JpaRepository<QueueTicketEntity, UUID> {

    Optional<QueueTicketEntity> findByIdAndTenantIdAndStoreIdAndDeletedAtIsNull(UUID id, UUID tenantId, UUID storeId);

    @Query("""
        select ticket from QueueTicketEntity ticket
        where ticket.tenantId = :tenantId
          and ticket.storeId = :storeId
          and ticket.queueGroupId = :queueGroupId
          and ticket.businessDate = :businessDate
          and ticket.deletedAt is null
        order by ticket.ticketNumber asc, ticket.queuePosition asc
        """)
    List<QueueTicketEntity> findQueueForNumbering(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("queueGroupId") UUID queueGroupId,
        @Param("businessDate") LocalDate businessDate
    );

    @Query("""
        select ticket from QueueTicketEntity ticket
        where ticket.tenantId = :tenantId
          and ticket.storeId = :storeId
          and ticket.queueGroupId = :queueGroupId
          and ticket.businessDate = :businessDate
          and ticket.status = 'waiting'
          and ticket.deletedAt is null
        order by ticket.queuePosition asc, ticket.ticketNumber asc
        """)
    List<QueueTicketEntity> findWaitingQueue(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("queueGroupId") UUID queueGroupId,
        @Param("businessDate") LocalDate businessDate
    );

    @Query(value = """
        select *
        from queue_tickets
        where tenant_id = :tenantId
          and store_id = :storeId
          and queue_group_id = :queueGroupId
          and business_date = :businessDate
          and status = 'waiting'
          and deleted_at is null
        order by queue_position asc, ticket_number asc
        limit 1
        """, nativeQuery = true)
    Optional<QueueTicketEntity> findNextWaiting(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("queueGroupId") UUID queueGroupId,
        @Param("businessDate") LocalDate businessDate
    );

    @Query(value = """
        select *
        from queue_tickets
        where tenant_id = :tenantId
          and store_id = :storeId
          and reservation_id = :reservationId
          and status in ('waiting', 'called', 'skipped', 'rejoined')
          and deleted_at is null
        order by created_at desc
        limit 1
        """, nativeQuery = true)
    Optional<QueueTicketEntity> findActiveByReservationId(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("reservationId") UUID reservationId
    );

    @Query(value = """
        select exists (
            select 1
            from queue_tickets
            where tenant_id = :tenantId
              and store_id = :storeId
              and status in ('waiting', 'called', 'skipped', 'rejoined')
              and deleted_at is null
              and (
                  (:sourceType = 'reservation' and reservation_id = :sourceId)
                  or (:sourceType = 'walk_in' and walk_in_id = :sourceId)
              )
        )
        """, nativeQuery = true)
    boolean existsActiveSourceTicket(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("sourceType") String sourceType,
        @Param("sourceId") UUID sourceId
    );

    @Query(value = """
        select
            ticket.id as "queueTicketId",
            ticket.ticket_number as "queueTicketNumber",
            ticket.status as "queueTicketStatus",
            ticket.party_size as "partySize",
            queue_group.group_code as "partySizeGroup",
            reservation.id as "reservationId",
            reservation.reservation_code as "reservationCode",
            reservation.status as "reservationStatus",
            customer.display_name as "customerName",
            customer.phone_e164 as "customerPhoneE164",
            preassignment.resource_type as "assignedResourceType",
            coalesce(preassignment.table_id, preassignment.table_group_id) as "assignedResourceId",
            coalesce(assigned_table.table_code, assigned_group.group_code) as "assignedResourceCode",
            ticket.created_at as "createdAt",
            ticket.called_at as "calledAt",
            ticket.expires_at as "expiresAt"
        from queue_tickets ticket
        join queue_groups queue_group
          on queue_group.id = ticket.queue_group_id
         and queue_group.tenant_id = ticket.tenant_id
         and queue_group.store_id = ticket.store_id
         and queue_group.deleted_at is null
        left join reservations reservation
          on reservation.id = ticket.reservation_id
         and reservation.tenant_id = ticket.tenant_id
         and reservation.store_id = ticket.store_id
         and reservation.deleted_at is null
        left join customers customer
          on customer.tenant_id = ticket.tenant_id
         and customer.id = coalesce(ticket.customer_id, reservation.customer_id)
         and customer.deleted_at is null
        left join reservation_preassignments preassignment
          on preassignment.tenant_id = ticket.tenant_id
         and preassignment.store_id = ticket.store_id
         and preassignment.reservation_id = reservation.id
         and preassignment.status = 'active'
         and preassignment.deleted_at is null
        left join dining_tables assigned_table
          on assigned_table.tenant_id = ticket.tenant_id
         and assigned_table.store_id = ticket.store_id
         and assigned_table.id = preassignment.table_id
         and assigned_table.deleted_at is null
        left join table_groups assigned_group
          on assigned_group.tenant_id = ticket.tenant_id
         and assigned_group.store_id = ticket.store_id
         and assigned_group.id = preassignment.table_group_id
         and assigned_group.deleted_at is null
        where ticket.tenant_id = :tenantId
          and ticket.store_id = :storeId
          and ticket.deleted_at is null
          and ticket.status = coalesce(cast(:status as varchar), ticket.status)
        order by ticket.created_at asc, ticket.ticket_number asc
        limit :limit offset :offset
        """, nativeQuery = true)
    List<QueueTicketListProjection> findQueueTicketListRows(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query(value = """
        select count(*)
        from queue_tickets ticket
        where ticket.tenant_id = :tenantId
          and ticket.store_id = :storeId
          and ticket.deleted_at is null
          and ticket.status = coalesce(cast(:status as varchar), ticket.status)
        """, nativeQuery = true)
    int countQueueTicketListRows(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("status") String status
    );
}
