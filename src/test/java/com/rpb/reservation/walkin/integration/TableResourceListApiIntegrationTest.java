package com.rpb.reservation.walkin.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

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
        table("70000000-0000-0000-0000-000000001251", AREA_A_ID, "A01", "A01 靠窗", 2, 4);
        table("70000000-0000-0000-0000-000000001252", AREA_A_ID, "A02", "A02 中央", 2, 4);
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
