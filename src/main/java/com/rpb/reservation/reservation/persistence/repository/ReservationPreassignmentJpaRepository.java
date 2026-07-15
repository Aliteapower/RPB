package com.rpb.reservation.reservation.persistence.repository;

import com.rpb.reservation.reservation.persistence.entity.ReservationPreassignmentEntity;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationPreassignmentJpaRepository extends JpaRepository<ReservationPreassignmentEntity, UUID> {

    @Query(value = """
        select exists (
            select 1
            from reservation_preassignments rp
            join reservations r
              on r.id = rp.reservation_id
             and r.tenant_id = rp.tenant_id
             and r.store_id = rp.store_id
             and r.deleted_at is null
            where rp.tenant_id = :tenantId
              and rp.store_id = :storeId
              and rp.status = 'active'
              and rp.deleted_at is null
              and r.business_date = :businessDate
              and (
                  r.status in ('confirmed', 'arrived')
                  or (
                      r.status = 'seated'
                      and (
                          exists (
                              select 1
                              from seatings s
                              join seating_resources sr
                                on sr.tenant_id = s.tenant_id
                               and sr.store_id = s.store_id
                               and sr.seating_id = s.id
                               and sr.status = 'active'
                               and sr.deleted_at is null
                               and sr.resource_type = rp.resource_type
                               and coalesce(sr.table_id, sr.table_group_id) = coalesce(rp.table_id, rp.table_group_id)
                              where s.tenant_id = r.tenant_id
                                and s.store_id = r.store_id
                                and s.deleted_at is null
                                and s.status in ('planned', 'locked', 'occupied')
                                and (
                                    s.reservation_id = r.id
                                    or s.queue_ticket_id in (
                                        select qt_active.id
                                        from queue_tickets qt_active
                                        where qt_active.tenant_id = r.tenant_id
                                          and qt_active.store_id = r.store_id
                                          and qt_active.reservation_id = r.id
                                          and qt_active.deleted_at is null
                                    )
                                )
                          )
                          or exists (
                              select 1
                              from cleanings c
                              join seatings s
                                on s.tenant_id = c.tenant_id
                               and s.store_id = c.store_id
                               and s.id = c.seating_id
                               and s.deleted_at is null
                              where c.tenant_id = r.tenant_id
                                and c.store_id = r.store_id
                                and c.status = 'cleaning'
                                and c.deleted_at is null
                                and c.resource_type = rp.resource_type
                                and coalesce(c.table_id, c.table_group_id) = coalesce(rp.table_id, rp.table_group_id)
                                and (
                                    s.reservation_id = r.id
                                    or s.queue_ticket_id in (
                                        select qt_cleaning.id
                                        from queue_tickets qt_cleaning
                                        where qt_cleaning.tenant_id = r.tenant_id
                                          and qt_cleaning.store_id = r.store_id
                                          and qt_cleaning.reservation_id = r.id
                                          and qt_cleaning.deleted_at is null
                                    )
                                )
                          )
                      )
                  )
              )
              and rp.resource_type = :resourceType
              and coalesce(rp.table_id, rp.table_group_id) = :resourceId
        )
        """, nativeQuery = true)
    boolean existsActiveResourceConflict(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("resourceType") String resourceType,
        @Param("resourceId") UUID resourceId,
        @Param("businessDate") LocalDate businessDate
    );

    @Query(value = """
        select
            r.id as "reservationId",
            r.reservation_code as "reservationCode",
            r.status as "reservationStatus",
            r.party_size as "partySize",
            r.reserved_start_at as "reservedStartAt",
            r.reserved_end_at as "reservedEndAt",
            c.display_name as "customerName",
            c.phone_e164 as "customerPhoneE164",
            rp.resource_type as "resourceType",
            coalesce(rp.table_id, rp.table_group_id) as "resourceId",
            coalesce(dt.table_code, tg.group_code) as "resourceCode",
            qt.id as "queueTicketId",
            qt.ticket_number as "queueTicketNumber",
            qt.status as "queueTicketStatus"
        from reservation_preassignments rp
        join reservations r
          on r.id = rp.reservation_id
         and r.tenant_id = rp.tenant_id
         and r.store_id = rp.store_id
         and r.deleted_at is null
        left join customers c
          on c.id = r.customer_id
         and c.tenant_id = r.tenant_id
         and c.deleted_at is null
        left join dining_tables dt
          on dt.id = rp.table_id
         and dt.tenant_id = rp.tenant_id
         and dt.store_id = rp.store_id
         and dt.deleted_at is null
        left join table_groups tg
          on tg.id = rp.table_group_id
         and tg.tenant_id = rp.tenant_id
         and tg.store_id = rp.store_id
         and tg.deleted_at is null
        left join queue_tickets qt
          on qt.tenant_id = r.tenant_id
         and qt.store_id = r.store_id
         and qt.reservation_id = r.id
         and qt.deleted_at is null
         and qt.status in ('waiting', 'called', 'skipped', 'rejoined', 'seated')
        where rp.tenant_id = :tenantId
          and rp.store_id = :storeId
          and rp.status = 'active'
          and rp.deleted_at is null
          and r.business_date = :businessDate
          and (
              r.status in ('confirmed', 'arrived')
              or (
                  r.status = 'seated'
                  and (
                      exists (
                          select 1
                          from seatings s
                          join seating_resources sr
                            on sr.tenant_id = s.tenant_id
                           and sr.store_id = s.store_id
                           and sr.seating_id = s.id
                           and sr.status = 'active'
                           and sr.deleted_at is null
                           and sr.resource_type = rp.resource_type
                           and coalesce(sr.table_id, sr.table_group_id) = coalesce(rp.table_id, rp.table_group_id)
                          where s.tenant_id = r.tenant_id
                            and s.store_id = r.store_id
                            and s.deleted_at is null
                            and s.status in ('planned', 'locked', 'occupied')
                            and (
                                s.reservation_id = r.id
                                or s.queue_ticket_id in (
                                    select qt_active.id
                                    from queue_tickets qt_active
                                    where qt_active.tenant_id = r.tenant_id
                                      and qt_active.store_id = r.store_id
                                      and qt_active.reservation_id = r.id
                                      and qt_active.deleted_at is null
                                )
                            )
                      )
                      or exists (
                          select 1
                          from cleanings c
                          join seatings s
                            on s.tenant_id = c.tenant_id
                           and s.store_id = c.store_id
                           and s.id = c.seating_id
                           and s.deleted_at is null
                          where c.tenant_id = r.tenant_id
                            and c.store_id = r.store_id
                            and c.status = 'cleaning'
                            and c.deleted_at is null
                            and c.resource_type = rp.resource_type
                            and coalesce(c.table_id, c.table_group_id) = coalesce(rp.table_id, rp.table_group_id)
                            and (
                                s.reservation_id = r.id
                                or s.queue_ticket_id in (
                                    select qt_cleaning.id
                                    from queue_tickets qt_cleaning
                                    where qt_cleaning.tenant_id = r.tenant_id
                                      and qt_cleaning.store_id = r.store_id
                                      and qt_cleaning.reservation_id = r.id
                                      and qt_cleaning.deleted_at is null
                                )
                            )
                      )
                  )
              )
          )
        order by r.reserved_start_at asc, r.created_at asc
        """, nativeQuery = true)
    List<ReservationResourceAssignmentProjection> findActiveResourceAssignmentsForDate(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("businessDate") LocalDate businessDate
    );

    @Query(value = """
        select
            r.id as "reservationId",
            r.reservation_code as "reservationCode",
            r.status as "reservationStatus",
            r.party_size as "partySize",
            r.reserved_start_at as "reservedStartAt",
            r.reserved_end_at as "reservedEndAt",
            c.display_name as "customerName",
            c.phone_e164 as "customerPhoneE164",
            rp.resource_type as "resourceType",
            coalesce(rp.table_id, rp.table_group_id) as "resourceId",
            coalesce(dt.table_code, tg.group_code) as "resourceCode",
            cast(null as uuid) as "queueTicketId",
            cast(null as integer) as "queueTicketNumber",
            cast(null as varchar) as "queueTicketStatus"
        from reservation_preassignments rp
        join reservations r
          on r.id = rp.reservation_id
         and r.tenant_id = rp.tenant_id
         and r.store_id = rp.store_id
         and r.deleted_at is null
        left join customers c
          on c.id = r.customer_id
         and c.tenant_id = r.tenant_id
         and c.deleted_at is null
        left join dining_tables dt
          on dt.id = rp.table_id
         and dt.tenant_id = rp.tenant_id
         and dt.store_id = rp.store_id
         and dt.deleted_at is null
        left join table_groups tg
          on tg.id = rp.table_group_id
         and tg.tenant_id = rp.tenant_id
         and tg.store_id = rp.store_id
         and tg.deleted_at is null
        where rp.tenant_id = :tenantId
          and rp.store_id = :storeId
          and rp.status = 'active'
          and rp.deleted_at is null
          and r.status in ('confirmed', 'arrived')
          and r.business_date = :businessDate
          and r.reserved_start_at < :requestedEnd
          and r.reserved_end_at > :requestedStart
        order by r.reserved_start_at asc, r.created_at asc
        """, nativeQuery = true)
    List<ReservationResourceAssignmentProjection> findActiveResourceAssignmentsOverlapping(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("businessDate") LocalDate businessDate,
        @Param("requestedStart") OffsetDateTime requestedStart,
        @Param("requestedEnd") OffsetDateTime requestedEnd
    );

    @Query(value = """
        select *
        from (
            select
                r.id as "reservationId",
                r.reservation_code as "reservationCode",
                r.status as "reservationStatus",
                r.party_size as "partySize",
                r.reserved_start_at as "reservedStartAt",
                r.reserved_end_at as "reservedEndAt",
                c.display_name as "customerName",
                c.phone_e164 as "customerPhoneE164",
                rp.resource_type as "resourceType",
                coalesce(rp.table_id, rp.table_group_id) as "resourceId",
                coalesce(dt.table_code, tg.group_code) as "resourceCode",
                qt.id as "queueTicketId",
                qt.ticket_number as "queueTicketNumber",
                qt.status as "queueTicketStatus"
            from reservation_preassignments rp
            join reservations r
              on r.id = rp.reservation_id
             and r.tenant_id = rp.tenant_id
             and r.store_id = rp.store_id
             and r.deleted_at is null
            left join customers c
              on c.id = r.customer_id
             and c.tenant_id = r.tenant_id
             and c.deleted_at is null
            left join dining_tables dt
              on dt.id = rp.table_id
             and dt.tenant_id = rp.tenant_id
             and dt.store_id = rp.store_id
             and dt.deleted_at is null
            left join table_groups tg
              on tg.id = rp.table_group_id
             and tg.tenant_id = rp.tenant_id
             and tg.store_id = rp.store_id
             and tg.deleted_at is null
            left join queue_tickets qt
              on qt.tenant_id = r.tenant_id
             and qt.store_id = r.store_id
             and qt.reservation_id = r.id
             and qt.deleted_at is null
             and qt.status in ('waiting', 'called', 'skipped', 'rejoined', 'seated')
            where rp.tenant_id = :tenantId
              and rp.store_id = :storeId
              and rp.status = 'active'
              and rp.deleted_at is null
              and r.id = :reservationId
              and (
                  r.status in ('confirmed', 'arrived')
                  or (
                      r.status = 'seated'
                      and (
                          exists (
                              select 1
                              from seatings s
                              join seating_resources sr
                                on sr.tenant_id = s.tenant_id
                               and sr.store_id = s.store_id
                               and sr.seating_id = s.id
                               and sr.status = 'active'
                               and sr.deleted_at is null
                               and sr.resource_type = rp.resource_type
                               and coalesce(sr.table_id, sr.table_group_id) = coalesce(rp.table_id, rp.table_group_id)
                              where s.tenant_id = r.tenant_id
                                and s.store_id = r.store_id
                                and s.deleted_at is null
                                and s.status in ('planned', 'locked', 'occupied')
                                and (
                                    s.reservation_id = r.id
                                    or s.queue_ticket_id in (
                                        select qt_active.id
                                        from queue_tickets qt_active
                                        where qt_active.tenant_id = r.tenant_id
                                          and qt_active.store_id = r.store_id
                                          and qt_active.reservation_id = r.id
                                          and qt_active.deleted_at is null
                                    )
                                )
                          )
                          or exists (
                              select 1
                              from cleanings c
                              join seatings s
                                on s.tenant_id = c.tenant_id
                               and s.store_id = c.store_id
                               and s.id = c.seating_id
                               and s.deleted_at is null
                              where c.tenant_id = r.tenant_id
                                and c.store_id = r.store_id
                                and c.status = 'cleaning'
                                and c.deleted_at is null
                                and c.resource_type = rp.resource_type
                                and coalesce(c.table_id, c.table_group_id) = coalesce(rp.table_id, rp.table_group_id)
                                and (
                                    s.reservation_id = r.id
                                    or s.queue_ticket_id in (
                                        select qt_cleaning.id
                                        from queue_tickets qt_cleaning
                                        where qt_cleaning.tenant_id = r.tenant_id
                                          and qt_cleaning.store_id = r.store_id
                                          and qt_cleaning.reservation_id = r.id
                                          and qt_cleaning.deleted_at is null
                                    )
                                )
                          )
                      )
                  )
              )
            order by r.reserved_start_at asc, r.created_at asc
        ) active_assignment
        limit 1
        """, nativeQuery = true)
    Optional<ReservationResourceAssignmentProjection> findActiveAssignmentForReservation(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("reservationId") UUID reservationId
    );

    @Query(value = """
        select *
        from (
            select
                r.id as "reservationId",
                r.reservation_code as "reservationCode",
                r.status as "reservationStatus",
                r.party_size as "partySize",
                r.reserved_start_at as "reservedStartAt",
                r.reserved_end_at as "reservedEndAt",
                c.display_name as "customerName",
                c.phone_e164 as "customerPhoneE164",
                rp.resource_type as "resourceType",
                coalesce(rp.table_id, rp.table_group_id) as "resourceId",
                coalesce(dt.table_code, tg.group_code) as "resourceCode",
                qt.id as "queueTicketId",
                qt.ticket_number as "queueTicketNumber",
                qt.status as "queueTicketStatus"
            from reservation_preassignments rp
            join reservations r
              on r.id = rp.reservation_id
             and r.tenant_id = rp.tenant_id
             and r.store_id = rp.store_id
             and r.deleted_at is null
            left join customers c
              on c.id = r.customer_id
             and c.tenant_id = r.tenant_id
             and c.deleted_at is null
            left join dining_tables dt
              on dt.id = rp.table_id
             and dt.tenant_id = rp.tenant_id
             and dt.store_id = rp.store_id
             and dt.deleted_at is null
            left join table_groups tg
              on tg.id = rp.table_group_id
             and tg.tenant_id = rp.tenant_id
             and tg.store_id = rp.store_id
             and tg.deleted_at is null
            left join queue_tickets qt
              on qt.tenant_id = r.tenant_id
             and qt.store_id = r.store_id
             and qt.reservation_id = r.id
             and qt.deleted_at is null
             and qt.status in ('waiting', 'called', 'skipped', 'rejoined', 'seated')
            where rp.tenant_id = :tenantId
              and rp.store_id = :storeId
              and rp.status = 'active'
              and rp.deleted_at is null
              and r.business_date = :businessDate
              and rp.resource_type = :resourceType
              and coalesce(rp.table_id, rp.table_group_id) = :resourceId
              and (
                  r.status in ('confirmed', 'arrived')
                  or (
                      r.status = 'seated'
                      and (
                          exists (
                              select 1
                              from seatings s
                              join seating_resources sr
                                on sr.tenant_id = s.tenant_id
                               and sr.store_id = s.store_id
                               and sr.seating_id = s.id
                               and sr.status = 'active'
                               and sr.deleted_at is null
                               and sr.resource_type = rp.resource_type
                               and coalesce(sr.table_id, sr.table_group_id) = coalesce(rp.table_id, rp.table_group_id)
                              where s.tenant_id = r.tenant_id
                                and s.store_id = r.store_id
                                and s.deleted_at is null
                                and s.status in ('planned', 'locked', 'occupied')
                                and (
                                    s.reservation_id = r.id
                                    or s.queue_ticket_id in (
                                        select qt_active.id
                                        from queue_tickets qt_active
                                        where qt_active.tenant_id = r.tenant_id
                                          and qt_active.store_id = r.store_id
                                          and qt_active.reservation_id = r.id
                                          and qt_active.deleted_at is null
                                    )
                                )
                          )
                          or exists (
                              select 1
                              from cleanings c
                              join seatings s
                                on s.tenant_id = c.tenant_id
                               and s.store_id = c.store_id
                               and s.id = c.seating_id
                               and s.deleted_at is null
                              where c.tenant_id = r.tenant_id
                                and c.store_id = r.store_id
                                and c.status = 'cleaning'
                                and c.deleted_at is null
                                and c.resource_type = rp.resource_type
                                and coalesce(c.table_id, c.table_group_id) = coalesce(rp.table_id, rp.table_group_id)
                                and (
                                    s.reservation_id = r.id
                                    or s.queue_ticket_id in (
                                        select qt_cleaning.id
                                        from queue_tickets qt_cleaning
                                        where qt_cleaning.tenant_id = r.tenant_id
                                          and qt_cleaning.store_id = r.store_id
                                          and qt_cleaning.reservation_id = r.id
                                          and qt_cleaning.deleted_at is null
                                    )
                                )
                          )
                      )
                  )
              )
            order by r.reserved_start_at asc, r.created_at asc
        ) active_assignment
        limit 1
        """, nativeQuery = true)
    Optional<ReservationResourceAssignmentProjection> findActiveAssignmentForResource(
        @Param("tenantId") UUID tenantId,
        @Param("storeId") UUID storeId,
        @Param("resourceType") String resourceType,
        @Param("resourceId") UUID resourceId,
        @Param("businessDate") LocalDate businessDate
    );
}
