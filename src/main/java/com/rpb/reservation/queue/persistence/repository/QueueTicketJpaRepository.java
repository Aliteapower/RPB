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
            ticket.status as "status",
            queue_group.group_code as "partySizeGroup",
            count(*) as "ticketCount",
            coalesce(sum(ticket.party_size), 0) as "partySizeTotal"
        from queue_tickets ticket
        join queue_groups queue_group
          on queue_group.id = ticket.queue_group_id
         and queue_group.tenant_id = ticket.tenant_id
         and queue_group.store_id = ticket.store_id
         and queue_group.deleted_at is null
        where ticket.tenant_id = :tenantId
          and ticket.store_id = :storeId
          and ticket.business_date = :businessDate
          and ticket.deleted_at is null
        group by ticket.status, queue_group.group_code
        """, nativeQuery = true)
    List<QueueTicketOverviewMetricProjection> findOverviewMetrics(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("businessDate") LocalDate businessDate
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
            coalesce(ticket_customer.display_name, reservation_customer.display_name) as "customerName",
            coalesce(ticket_customer.phone_e164, reservation_customer.phone_e164) as "customerPhoneE164",
            preassignment.resource_type as "assignedResourceType",
            coalesce(preassignment.table_id, preassignment.table_group_id) as "assignedResourceId",
            coalesce(assigned_table.table_code, assigned_group.group_code) as "assignedResourceCode",
            assigned_group.group_type as "assignedResourceGroupType",
            case
                when assigned_table.id is not null then concat('桌号 ', assigned_table.table_code)
                when assigned_group.id is not null and assigned_group.group_type = 'temporary'
                    then concat('临时组 ', coalesce(nullif(assigned_group.display_name, ''), assigned_group.group_code))
                when assigned_group.id is not null
                    then concat('桌组 ', coalesce(nullif(assigned_group.display_name, ''), assigned_group.group_code))
                else null
            end as "assignedResourceLabel",
            coalesce(assigned_table_area.display_name, assigned_group_area.area_names) as "assignedResourceAreaName",
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
        left join customers ticket_customer
          on ticket_customer.tenant_id = ticket.tenant_id
         and ticket_customer.id = ticket.customer_id
         and ticket_customer.deleted_at is null
        left join customers reservation_customer
          on reservation_customer.tenant_id = ticket.tenant_id
         and reservation_customer.id = reservation.customer_id
         and reservation_customer.deleted_at is null
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
        left join store_areas assigned_table_area
          on assigned_table_area.tenant_id = assigned_table.tenant_id
         and assigned_table_area.store_id = assigned_table.store_id
         and assigned_table_area.id = assigned_table.area_id
         and assigned_table_area.deleted_at is null
        left join table_groups assigned_group
          on assigned_group.tenant_id = ticket.tenant_id
         and assigned_group.store_id = ticket.store_id
         and assigned_group.id = preassignment.table_group_id
         and assigned_group.deleted_at is null
        left join lateral (
            select string_agg(distinct member_area.display_name, ' + ' order by member_area.display_name) as area_names
            from table_group_members member
            join dining_tables member_table
              on member_table.tenant_id = member.tenant_id
             and member_table.store_id = member.store_id
             and member_table.id = member.table_id
             and member_table.deleted_at is null
            join store_areas member_area
              on member_area.tenant_id = member_table.tenant_id
             and member_area.store_id = member_table.store_id
             and member_area.id = member_table.area_id
             and member_area.deleted_at is null
            where member.tenant_id = assigned_group.tenant_id
              and member.store_id = assigned_group.store_id
              and member.table_group_id = assigned_group.id
              and member.deleted_at is null
        ) assigned_group_area on true
        where ticket.tenant_id = :tenantId
          and ticket.store_id = :storeId
          and ticket.business_date = :businessDate
          and ticket.deleted_at is null
          and ticket.status = coalesce(cast(:status as varchar), ticket.status)
          and (:partySize is null or ticket.party_size = :partySize)
          and (
              cast(:phoneDigits as varchar) is null
              or regexp_replace(coalesce(ticket_customer.phone_e164, ''), '[^0-9]', '', 'g') like concat('%', cast(:phoneDigits as varchar), '%')
              or regexp_replace(coalesce(reservation_customer.phone_e164, ''), '[^0-9]', '', 'g') like concat('%', cast(:phoneDigits as varchar), '%')
          )
          and (
              cast(:tableArea as varchar) is null
              or (
                  cast(:tableArea as varchar) in ('__unassigned__', '未指定分区')
                  and coalesce(assigned_table_area.display_name, assigned_group_area.area_names) is null
              )
              or assigned_table_area.display_name = cast(:tableArea as varchar)
              or exists (
                  select 1
                  from table_group_members filter_member
                  join dining_tables filter_table
                    on filter_table.tenant_id = filter_member.tenant_id
                   and filter_table.store_id = filter_member.store_id
                   and filter_table.id = filter_member.table_id
                   and filter_table.deleted_at is null
                  join store_areas filter_area
                    on filter_area.tenant_id = filter_table.tenant_id
                   and filter_area.store_id = filter_table.store_id
                   and filter_area.id = filter_table.area_id
                   and filter_area.deleted_at is null
                  where filter_member.tenant_id = assigned_group.tenant_id
                    and filter_member.store_id = assigned_group.store_id
                    and filter_member.table_group_id = assigned_group.id
                    and filter_member.deleted_at is null
                    and filter_area.display_name = cast(:tableArea as varchar)
              )
          )
        order by ticket.created_at asc, ticket.ticket_number asc
        limit :limit offset :offset
        """, nativeQuery = true)
    List<QueueTicketListProjection> findQueueTicketListRows(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("businessDate") LocalDate businessDate,
        @Param("status") String status,
        @Param("tableArea") String tableArea,
        @Param("partySize") Integer partySize,
        @Param("phoneDigits") String phoneDigits,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query(value = """
        select count(*)
        from queue_tickets ticket
        left join reservations reservation
          on reservation.id = ticket.reservation_id
         and reservation.tenant_id = ticket.tenant_id
         and reservation.store_id = ticket.store_id
         and reservation.deleted_at is null
        left join customers ticket_customer
          on ticket_customer.tenant_id = ticket.tenant_id
         and ticket_customer.id = ticket.customer_id
         and ticket_customer.deleted_at is null
        left join customers reservation_customer
          on reservation_customer.tenant_id = ticket.tenant_id
         and reservation_customer.id = reservation.customer_id
         and reservation_customer.deleted_at is null
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
        left join store_areas assigned_table_area
          on assigned_table_area.tenant_id = assigned_table.tenant_id
         and assigned_table_area.store_id = assigned_table.store_id
         and assigned_table_area.id = assigned_table.area_id
         and assigned_table_area.deleted_at is null
        left join table_groups assigned_group
          on assigned_group.tenant_id = ticket.tenant_id
         and assigned_group.store_id = ticket.store_id
         and assigned_group.id = preassignment.table_group_id
         and assigned_group.deleted_at is null
        left join lateral (
            select string_agg(distinct member_area.display_name, ' + ' order by member_area.display_name) as area_names
            from table_group_members member
            join dining_tables member_table
              on member_table.tenant_id = member.tenant_id
             and member_table.store_id = member.store_id
             and member_table.id = member.table_id
             and member_table.deleted_at is null
            join store_areas member_area
              on member_area.tenant_id = member_table.tenant_id
             and member_area.store_id = member_table.store_id
             and member_area.id = member_table.area_id
             and member_area.deleted_at is null
            where member.tenant_id = assigned_group.tenant_id
              and member.store_id = assigned_group.store_id
              and member.table_group_id = assigned_group.id
              and member.deleted_at is null
        ) assigned_group_area on true
        where ticket.tenant_id = :tenantId
          and ticket.store_id = :storeId
          and ticket.business_date = :businessDate
          and ticket.deleted_at is null
          and ticket.status = coalesce(cast(:status as varchar), ticket.status)
          and (:partySize is null or ticket.party_size = :partySize)
          and (
              cast(:phoneDigits as varchar) is null
              or regexp_replace(coalesce(ticket_customer.phone_e164, ''), '[^0-9]', '', 'g') like concat('%', cast(:phoneDigits as varchar), '%')
              or regexp_replace(coalesce(reservation_customer.phone_e164, ''), '[^0-9]', '', 'g') like concat('%', cast(:phoneDigits as varchar), '%')
          )
          and (
              cast(:tableArea as varchar) is null
              or (
                  cast(:tableArea as varchar) in ('__unassigned__', '未指定分区')
                  and coalesce(assigned_table_area.display_name, assigned_group_area.area_names) is null
              )
              or assigned_table_area.display_name = cast(:tableArea as varchar)
              or exists (
                  select 1
                  from table_group_members filter_member
                  join dining_tables filter_table
                    on filter_table.tenant_id = filter_member.tenant_id
                   and filter_table.store_id = filter_member.store_id
                   and filter_table.id = filter_member.table_id
                   and filter_table.deleted_at is null
                  join store_areas filter_area
                    on filter_area.tenant_id = filter_table.tenant_id
                   and filter_area.store_id = filter_table.store_id
                   and filter_area.id = filter_table.area_id
                   and filter_area.deleted_at is null
                  where filter_member.tenant_id = assigned_group.tenant_id
                    and filter_member.store_id = assigned_group.store_id
                    and filter_member.table_group_id = assigned_group.id
                    and filter_member.deleted_at is null
                    and filter_area.display_name = cast(:tableArea as varchar)
              )
          )
        """, nativeQuery = true)
    int countQueueTicketListRows(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("businessDate") LocalDate businessDate,
        @Param("status") String status,
        @Param("tableArea") String tableArea,
        @Param("partySize") Integer partySize,
        @Param("phoneDigits") String phoneDigits
    );
}
