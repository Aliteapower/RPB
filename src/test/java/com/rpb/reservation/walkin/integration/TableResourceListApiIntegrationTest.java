package com.rpb.reservation.walkin.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.time.BusinessDate;
import com.rpb.reservation.common.time.TimeRange;
import com.rpb.reservation.reservation.application.port.out.ReservationPreassignmentRepositoryPort;
import com.rpb.reservation.reservation.application.port.out.ReservationResourceAssignment;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class TableResourceListApiIntegrationTest {
    private static final String ENDPOINT = "/api/v1/stores/{storeId}/tables";
    private static final LocalPostgresTestDatabase DATABASE = LocalPostgresTestDatabase.start();

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000001251");
    private static final UUID STORE_ID = UUID.fromString("20000000-0000-0000-0000-000000001251");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000001251");
    private static final UUID AREA_A_ID = UUID.fromString("60000000-0000-0000-0000-000000001251");
    private static final UUID AREA_B_ID = UUID.fromString("60000000-0000-0000-0000-000000001252");
    private static final UUID AREA_VIP_ID = UUID.fromString("60000000-0000-0000-0000-000000001253");
    private static final UUID TABLE_A01_ID = UUID.fromString("70000000-0000-0000-0000-000000001251");
    private static final UUID TABLE_A02_ID = UUID.fromString("70000000-0000-0000-0000-000000001252");
    private static final UUID CUSTOMER_ID = UUID.fromString("40000000-0000-0000-0000-000000001251");
    private static final UUID RESERVATION_ID = UUID.fromString("50000000-0000-0000-0000-000000001251");
    private static final UUID PREASSIGNMENT_ID = UUID.fromString("51000000-0000-0000-0000-000000001251");
    private static final UUID SEATING_ID = UUID.fromString("52000000-0000-0000-0000-000000001251");
    private static final UUID SEATING_RESOURCE_ID = UUID.fromString("53000000-0000-0000-0000-000000001251");
    private static final UUID TEMP_GROUP_WALK_IN_ID = UUID.fromString("54000000-0000-0000-0000-000000001251");
    private static final UUID TEMP_GROUP_SEATING_ID = UUID.fromString("55000000-0000-0000-0000-000000001251");
    private static final UUID TEMP_GROUP_SEATING_RESOURCE_ID = UUID.fromString("56000000-0000-0000-0000-000000001251");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReservationPreassignmentRepositoryPort preassignmentRepository;

    @Autowired
    private TestCurrentActorProvider actorProvider;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::jdbcUrl);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    @AfterAll
    static void stopDatabase() {
        DATABASE.close();
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate table tenants cascade");
        actorProvider.actor.set(CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("table.view"),
            Set.of(STORE_ID)
        ));
        createBackendTableSetup();
    }

    @Test
    void listsBackendConfiguredTablesGroupedByAreaNames() throws Exception {
        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("partySize", "4")
                .param("includeGroups", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resources[0].code").value("A01"))
            .andExpect(jsonPath("$.resources[0].displayName").value("A01 靠窗"))
            .andExpect(jsonPath("$.resources[0].areaName").value("A区"))
            .andExpect(jsonPath("$.resources[1].code").value("A02"))
            .andExpect(jsonPath("$.resources[1].areaName").value("A区"))
            .andExpect(jsonPath("$.resources[2].code").value("B01"))
            .andExpect(jsonPath("$.resources[2].areaName").value("B区"))
            .andExpect(jsonPath("$.resources[3].code").value("B02"))
            .andExpect(jsonPath("$.resources[3].areaName").value("B区"))
            .andExpect(jsonPath("$.resources[4].code").value("VIP1"))
            .andExpect(jsonPath("$.resources[4].displayName").value("VIP-1"))
            .andExpect(jsonPath("$.resources[4].areaName").value("包厢"))
            .andExpect(jsonPath("$.resources[5].code").value("VIP2"))
            .andExpect(jsonPath("$.resources[5].areaName").value("包厢"));
    }

    @Test
    void overlaysReservationPreassignmentForSelectedBusinessDate() throws Exception {
        createConfirmedReservationWithTablePreassignment();

        Set<ReservationResourceAssignment> assignments = preassignmentRepository.findActiveResourceAssignmentsForDate(
            new StoreScope(new TenantId(TENANT_ID), STORE_ID),
            new BusinessDate(LocalDate.of(2026, 6, 24))
        );
        assertEquals(1, assignments.size());
        ReservationResourceAssignment assignment = assignments.iterator().next();
        assertEquals(RESERVATION_ID, assignment.reservationId());
        assertEquals(TABLE_A01_ID, assignment.resourceId());

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("businessDate", "2026-06-24")
                .param("includeGroups", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resources[0].code").value("A01"))
            .andExpect(jsonPath("$.resources[0].status").value("reserved"))
            .andExpect(jsonPath("$.resources[0].selectable").value(false))
            .andExpect(jsonPath("$.resources[0].selectionDisabledReason").value("reservation_preassigned"))
            .andExpect(jsonPath("$.resources[0].preassignedReservationId").value(RESERVATION_ID.toString()))
            .andExpect(jsonPath("$.resources[0].preassignedReservationCode").value("R-TABLE-1251"))
            .andExpect(jsonPath("$.resources[0].preassignedCustomerName").value("Table Guest"))
            .andExpect(jsonPath("$.resources[0].preassignedPhoneMasked").value("****1251"))
            .andExpect(jsonPath("$.resources[0].preassignedReservationStatus").value("confirmed"))
            .andExpect(jsonPath("$.resources[0].preassignedPartySize").value(2))
            .andExpect(jsonPath("$.resources[0].preassignedResourceCode").value("A01"));
    }

    @Test
    void seatedReservationMovedOffPreassignedTableDoesNotHoldOriginalTable() throws Exception {
        createSeatedReservationMovedOffPreassignedTable();

        StoreScope scope = new StoreScope(new TenantId(TENANT_ID), STORE_ID);
        Set<ReservationResourceAssignment> assignments = preassignmentRepository.findActiveResourceAssignmentsForDate(
            scope,
            new BusinessDate(LocalDate.of(2026, 6, 24))
        );
        assertEquals(0, assignments.size());
        assertFalse(preassignmentRepository.existsActiveResourceConflict(
            scope,
            "dining_table",
            TABLE_A02_ID,
            new BusinessDate(LocalDate.of(2026, 6, 24)),
            new TimeRange(
                Instant.parse("2026-06-24T06:15:00Z"),
                Instant.parse("2026-06-24T07:45:00Z")
            )
        ));

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("businessDate", "2026-06-24")
                .param("includeGroups", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resources[1].code").value("A02"))
            .andExpect(jsonPath("$.resources[1].status").value("available"))
            .andExpect(jsonPath("$.resources[1].selectable").value(true))
            .andExpect(jsonPath("$.resources[1].preassignedReservationId").doesNotExist())
            .andExpect(jsonPath("$.resources[1].preassignedReservationCode").doesNotExist());
    }

    @Test
    void savesAndDissolvesTemporaryTableGroupForTablePageManagement() throws Exception {
        actorProvider.actor.set(CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("table.view", "table.switch"),
            Set.of(STORE_ID)
        ));

        mockMvc.perform(post("/api/v1/stores/{storeId}/tables/temporary-groups", STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupName":"A区临组1","businessDate":"2026-06-25","tableIds":["%s","%s"]}
                    """.formatted(TABLE_A01_ID, TABLE_A02_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.groupName").value("A区临组1"))
            .andExpect(jsonPath("$.groupType").value("temporary"))
            .andExpect(jsonPath("$.status").value("created"))
            .andExpect(jsonPath("$.tableIds.length()").value(2));

        UUID groupId = jdbc.queryForObject(
            "select id from table_groups where tenant_id = ? and store_id = ? and group_code = ? and deleted_at is null",
            UUID.class,
            TENANT_ID,
            STORE_ID,
            "A区临组1"
        );
        assertEquals(2, jdbc.queryForObject(
            "select count(*) from table_group_members where table_group_id = ? and deleted_at is null",
            Integer.class,
            groupId
        ));

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("businessDate", "2026-06-25")
                .param("includeGroups", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resources[0].code").value("A01"))
            .andExpect(jsonPath("$.resources[0].selectable").value(false))
            .andExpect(jsonPath("$.resources[0].selectionDisabledReason").value("temporary_group_member"))
            .andExpect(jsonPath("$.resources[6].resourceType").value("table_group"))
            .andExpect(jsonPath("$.resources[6].groupType").value("temporary"))
            .andExpect(jsonPath("$.resources[6].code").value("A区临组1"))
            .andExpect(jsonPath("$.resources[6].status").value("created"))
            .andExpect(jsonPath("$.resources[6].selectable").value(true))
            .andExpect(jsonPath("$.resources[6].memberTableCodes[0]").value("A01"))
            .andExpect(jsonPath("$.resources[6].memberTableCodes[1]").value("A02"));

        mockMvc.perform(delete("/api/v1/stores/{storeId}/tables/temporary-groups/{tableGroupId}", STORE_ID, groupId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tableGroupId").value(groupId.toString()));

        assertEquals(0, jdbc.queryForObject(
            "select count(*) from table_groups where id = ? and deleted_at is null",
            Integer.class,
            groupId
        ));
        assertEquals(0, jdbc.queryForObject(
            "select count(*) from table_group_members where table_group_id = ? and deleted_at is null",
            Integer.class,
            groupId
        ));

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("businessDate", "2026-06-25")
                .param("includeGroups", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resources.length()").value(6))
            .andExpect(jsonPath("$.resources[0].selectable").value(true))
            .andExpect(jsonPath("$.resources[0].selectionDisabledReason").doesNotExist());
    }

    @Test
    void seatedTemporaryTableGroupIsExposedAsOccupiedAndCannotBeDissolved() throws Exception {
        actorProvider.actor.set(CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("table.view", "table.switch"),
            Set.of(STORE_ID)
        ));

        mockMvc.perform(post("/api/v1/stores/{storeId}/tables/temporary-groups", STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupName":"A区临组入桌","businessDate":"2026-06-25","tableIds":["%s","%s"]}
                    """.formatted(TABLE_A01_ID, TABLE_A02_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));

        UUID groupId = jdbc.queryForObject(
            "select id from table_groups where tenant_id = ? and store_id = ? and group_code = ? and deleted_at is null",
            UUID.class,
            TENANT_ID,
            STORE_ID,
            "A区临组入桌"
        );
        createActiveTemporaryGroupSeating(groupId);

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("businessDate", "2026-06-25")
                .param("includeGroups", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resources[6].resourceType").value("table_group"))
            .andExpect(jsonPath("$.resources[6].groupType").value("temporary"))
            .andExpect(jsonPath("$.resources[6].code").value("A区临组入桌"))
            .andExpect(jsonPath("$.resources[6].status").value("occupied"))
            .andExpect(jsonPath("$.resources[6].selectable").value(false))
            .andExpect(jsonPath("$.resources[6].selectionDisabledReason").value("status_unavailable"))
            .andExpect(jsonPath("$.resources[6].currentSeatingId").value(TEMP_GROUP_SEATING_ID.toString()))
            .andExpect(jsonPath("$.resources[6].currentPartySize").value(4));

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("businessDate", "2026-06-25")
                .param("status", "occupied")
                .param("includeGroups", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resources[0].code").value("A01"))
            .andExpect(jsonPath("$.resources[1].code").value("A02"))
            .andExpect(jsonPath("$.resources[2].code").value("A区临组入桌"));

        mockMvc.perform(delete("/api/v1/stores/{storeId}/tables/temporary-groups/{tableGroupId}", STORE_ID, groupId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("GROUP_NOT_DISSOLVABLE"));
    }

    @Test
    void temporaryTableGroupIsScopedToRequestedBusinessDate() throws Exception {
        actorProvider.actor.set(CurrentActor.storeStaff(
            TENANT_ID,
            ACTOR_ID,
            "staff",
            Set.of("store_staff"),
            Set.of("table.view", "table.switch"),
            Set.of(STORE_ID)
        ));

        mockMvc.perform(post("/api/v1/stores/{storeId}/tables/temporary-groups", STORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"groupName":"A区临组跨日","businessDate":"2026-06-25","tableIds":["%s","%s"]}
                    """.formatted(TABLE_A01_ID, TABLE_A02_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("businessDate", "2026-06-25")
                .param("includeGroups", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resources[0].selectable").value(false))
            .andExpect(jsonPath("$.resources[0].selectionDisabledReason").value("temporary_group_member"))
            .andExpect(jsonPath("$.resources[6].code").value("A区临组跨日"));

        mockMvc.perform(get(ENDPOINT, STORE_ID)
                .param("businessDate", "2026-06-26")
                .param("includeGroups", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resources.length()").value(6))
            .andExpect(jsonPath("$.resources[0].selectable").value(true))
            .andExpect(jsonPath("$.resources[0].selectionDisabledReason").doesNotExist());
    }

    private void createBackendTableSetup() {
        jdbc.update(
            """
            insert into tenants (id, tenant_code, display_name, status, default_locale)
            values (?, 'tenant-table-resource-it', 'Table Resource Tenant', 'active', 'zh-CN')
            """,
            TENANT_ID
        );
        jdbc.update(
            """
            insert into stores (
                id, tenant_id, store_code, display_name, status,
                timezone, locale, date_format, time_format, currency
            )
            values (?, ?, 'store-table-resource-it', 'Table Resource Store', 'active',
                'Asia/Singapore', 'zh-CN', 'yyyy-MM-dd', 'HH:mm', 'SGD')
            """,
            STORE_ID,
            TENANT_ID
        );
        area(AREA_A_ID, "A", "A区", 1);
        area(AREA_B_ID, "B", "B区", 2);
        area(AREA_VIP_ID, "VIP", "包厢", 3);
        enableReservationQueueApp();
        table(TABLE_A01_ID.toString(), AREA_A_ID, "A01", "A01 靠窗", 2, 4);
        table(TABLE_A02_ID.toString(), AREA_A_ID, "A02", "A02 中央", 2, 4);
        table("70000000-0000-0000-0000-000000001253", AREA_B_ID, "B01", "B01 四人桌", 2, 6);
        table("70000000-0000-0000-0000-000000001254", AREA_B_ID, "B02", "B02 四人桌", 2, 6);
        table("70000000-0000-0000-0000-000000001255", AREA_VIP_ID, "VIP1", "VIP-1", 2, 10);
        table("70000000-0000-0000-0000-000000001256", AREA_VIP_ID, "VIP2", "VIP-2", 2, 12);
    }

    private void area(UUID areaId, String areaCode, String displayName, int sortOrder) {
        jdbc.update(
            """
            insert into store_areas (id, tenant_id, store_id, area_code, display_name, status, sort_order)
            values (?, ?, ?, ?, ?, 'active', ?)
            """,
            areaId,
            TENANT_ID,
            STORE_ID,
            areaCode,
            displayName,
            sortOrder
        );
    }

    private void table(String tableId, UUID areaId, String tableCode, String displayName, int min, int max) {
        jdbc.update(
            """
            insert into dining_tables (
                id, tenant_id, store_id, area_id, table_code, display_name,
                capacity_min, capacity_max, status, is_combinable
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, 'available', true)
            """,
            UUID.fromString(tableId),
            TENANT_ID,
            STORE_ID,
            areaId,
            tableCode,
            displayName,
            min,
            max
        );
    }

    private void enableReservationQueueApp() {
        jdbc.update(
            """
            insert into tenant_app_entitlements (
                tenant_id, app_key, status, valid_from, valid_until, config_json, enabled_at
            )
            values (?, 'reservation_queue', 'enabled', now(), null, '{}'::jsonb, now())
            on conflict (tenant_id, app_key) do nothing
            """,
            TENANT_ID
        );
        jdbc.update(
            """
            insert into store_app_settings (
                tenant_id, store_id, app_key, is_enabled, entry_visible, config_json, enabled_at
            )
            values (?, ?, 'reservation_queue', true, true, '{}'::jsonb, now())
            on conflict (tenant_id, store_id, app_key) do update
            set is_enabled = true,
                entry_visible = true,
                disabled_at = null,
                updated_at = now()
            """,
            TENANT_ID,
            STORE_ID
        );
    }

    private void createConfirmedReservationWithTablePreassignment() {
        jdbc.update(
            """
            insert into customers (
                id, tenant_id, customer_code, customer_type, display_name,
                phone_e164, status
            )
            values (?, ?, 'C-TABLE-1251', 'regular', 'Table Guest',
                '+6599991251', 'active')
            """,
            CUSTOMER_ID,
            TENANT_ID
        );
        jdbc.update(
            """
            insert into reservations (
                id, tenant_id, store_id, customer_id, reservation_code, party_size,
                business_date, reserved_start_at, reserved_end_at, hold_until_at,
                status, source_channel
            )
            values (?, ?, ?, ?, 'R-TABLE-1251', 2,
                date '2026-06-24',
                timestamp with time zone '2026-06-24 13:30:00+08',
                timestamp with time zone '2026-06-24 15:00:00+08',
                timestamp with time zone '2026-06-24 13:30:00+08',
                'confirmed', 'staff')
            """,
            RESERVATION_ID,
            TENANT_ID,
            STORE_ID,
            CUSTOMER_ID
        );
        jdbc.update(
            """
            insert into reservation_preassignments (
                id, tenant_id, store_id, reservation_id, resource_type, table_id,
                status, preassigned_at
            )
            values (?, ?, ?, ?, 'dining_table', ?, 'active', now())
            """,
            PREASSIGNMENT_ID,
            TENANT_ID,
            STORE_ID,
            RESERVATION_ID,
            TABLE_A01_ID
        );
    }

    private void createActiveTemporaryGroupSeating(UUID groupId) {
        jdbc.update(
            "update dining_tables set status = 'occupied' where id in (?, ?)",
            TABLE_A01_ID,
            TABLE_A02_ID
        );
        jdbc.update(
            """
            insert into walk_ins (
                id, tenant_id, store_id, walk_in_code, party_size, business_date,
                arrived_at, status
            )
            values (?, ?, ?, 'W-TMP-GROUP-1251', 4, date '2026-06-25',
                timestamp with time zone '2026-06-25 13:10:00+08', 'seated')
            """,
            TEMP_GROUP_WALK_IN_ID,
            TENANT_ID,
            STORE_ID
        );
        jdbc.update(
            """
            insert into seatings (
                id, tenant_id, store_id, walk_in_id, seating_code, party_size_snapshot,
                status, seated_at
            )
            values (?, ?, ?, ?, 'S-TMP-GROUP-1251', 4,
                'occupied', timestamp with time zone '2026-06-25 13:15:00+08')
            """,
            TEMP_GROUP_SEATING_ID,
            TENANT_ID,
            STORE_ID,
            TEMP_GROUP_WALK_IN_ID
        );
        jdbc.update(
            """
            insert into seating_resources (
                id, tenant_id, store_id, seating_id, resource_type, table_group_id,
                assigned_at, status
            )
            values (?, ?, ?, ?, 'table_group', ?,
                timestamp with time zone '2026-06-25 13:15:00+08', 'active')
            """,
            TEMP_GROUP_SEATING_RESOURCE_ID,
            TENANT_ID,
            STORE_ID,
            TEMP_GROUP_SEATING_ID,
            groupId
        );
    }

    private void createSeatedReservationMovedOffPreassignedTable() {
        createConfirmedReservationWithTablePreassignment();
        jdbc.update(
            """
            update reservations
            set status = 'seated'
            where id = ?
            """,
            RESERVATION_ID
        );
        jdbc.update(
            """
            update reservation_preassignments
            set table_id = ?
            where id = ?
            """,
            TABLE_A02_ID,
            PREASSIGNMENT_ID
        );
        jdbc.update(
            """
            update dining_tables
            set status = 'occupied'
            where id = ?
            """,
            TABLE_A01_ID
        );
        jdbc.update(
            """
            insert into seatings (
                id, tenant_id, store_id, reservation_id, seating_code, party_size_snapshot,
                status, seated_at
            )
            values (?, ?, ?, ?, 'S-MOVED-1251', 2, 'occupied', timestamp with time zone '2026-06-24 14:15:00+08')
            """,
            SEATING_ID,
            TENANT_ID,
            STORE_ID,
            RESERVATION_ID
        );
        jdbc.update(
            """
            insert into seating_resources (
                id, tenant_id, store_id, seating_id, resource_type, table_id,
                assigned_at, status
            )
            values (?, ?, ?, ?, 'dining_table', ?, timestamp with time zone '2026-06-24 14:15:00+08', 'active')
            """,
            SEATING_RESOURCE_ID,
            TENANT_ID,
            STORE_ID,
            SEATING_ID,
            TABLE_A01_ID
        );
    }

    @TestConfiguration
    static class TestCurrentActorConfiguration {
        @Bean
        @Primary
        TestCurrentActorProvider testCurrentActorProvider() {
            return new TestCurrentActorProvider();
        }
    }

    static final class TestCurrentActorProvider implements CurrentActorProvider {
        private final AtomicReference<CurrentActor> actor = new AtomicReference<>();

        @Override
        public Optional<CurrentActor> currentActor() {
            return Optional.ofNullable(actor.get());
        }
    }
}
