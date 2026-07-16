package com.rpb.reservation.reservation.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpb.reservation.reservation.application.PlatformReservationShareTemplateSeed;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedService;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedServiceErrorCode;
import com.rpb.reservation.reservation.application.service.PlatformReservationShareTemplateSeedServiceException;
import com.rpb.reservation.reservation.application.service.ReservationShareTemplateCatalog;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformReservationShareTemplateSeedControllerTest {
    private static final String ENDPOINT = "/api/v1/platform/reservation/share-template-seed";
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000971");
    private static final UUID ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000971");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PlatformReservationShareTemplateSeedService service;
    private MutableCurrentActorProvider actorProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PlatformReservationShareTemplateSeedService.class);
        actorProvider = new MutableCurrentActorProvider(actor(
            Set.of("platform_admin"),
            Set.of("platform.reservation_share_template.manage")
        ));
        mockMvc = MockMvcBuilders.standaloneSetup(new PlatformReservationShareTemplateSeedController(service, actorProvider)).build();
    }

    @Test
    void mapsGetAndPatchDefaultReservationShareTemplateSeed() throws Exception {
        when(service.getDefaultSeed("en-SG")).thenReturn(seed("Restaurant reservation confirmation template V1", "en-SG", 1));
        when(service.updateDefaultSeed(any())).thenReturn(seed("Reservation confirmation template", 2));

        mockMvc.perform(get(ENDPOINT).param("locale", "en-SG"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.seed.seedKey").value(ReservationShareTemplateCatalog.defaultSeedKey()))
            .andExpect(jsonPath("$.seed.locale").value("en-SG"))
            .andExpect(jsonPath("$.seed.templateText").value(org.hamcrest.Matchers.containsString("{{guestSalutation}}")))
            .andExpect(jsonPath("$.seed.allowedVariables").isArray());

        mockMvc.perform(patch(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new PlatformReservationShareTemplateSeedRequest(
                    "Reservation confirmation template",
                    "en-SG",
                    "Booking no.: {{reservationNo}}\\nTable: {{tableCode}}",
                    "active",
                    1
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.seed.displayName").value("Reservation confirmation template"))
            .andExpect(jsonPath("$.seed.version").value(2));

        verify(service).getDefaultSeed("en-SG");
        verify(service).updateDefaultSeed(any());
    }

    @Test
    void mapsGetDefaultReservationShareTemplateSeedForChineseLocale() throws Exception {
        when(service.getDefaultSeed("zh-CN")).thenReturn(seed(
            "餐厅预约确认模板 V1",
            "zh-CN",
            "感谢选择 {{storeName}}",
            4
        ));

        mockMvc.perform(get(ENDPOINT).param("locale", "zh-CN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.seed.displayName").value("餐厅预约确认模板 V1"))
            .andExpect(jsonPath("$.seed.locale").value("zh-CN"))
            .andExpect(jsonPath("$.seed.templateText").value(org.hamcrest.Matchers.containsString("感谢选择")))
            .andExpect(jsonPath("$.seed.version").value(4));

        verify(service).getDefaultSeed("zh-CN");
    }

    @Test
    void rejectsBroadPlatformTenantManagerWithoutTemplatePermission() throws Exception {
        actorProvider.actor = actor(Set.of("platform_admin"), Set.of("platform.tenant.manage"));

        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnknownVariablesAndVersionConflictsAsStableErrors() throws Exception {
        doThrow(new PlatformReservationShareTemplateSeedServiceException(
            PlatformReservationShareTemplateSeedServiceErrorCode.TEMPLATE_UNKNOWN_VARIABLE
        )).when(service).updateDefaultSeed(any());

        mockMvc.perform(patch(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName": "预约确认模板",
                      "locale": "zh-CN",
                      "templateText": "{{unsupportedVariable}}",
                      "status": "active",
                      "version": 1
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("TEMPLATE_UNKNOWN_VARIABLE"));

        doThrow(new PlatformReservationShareTemplateSeedServiceException(
            PlatformReservationShareTemplateSeedServiceErrorCode.VERSION_CONFLICT
        )).when(service).updateDefaultSeed(any());

        mockMvc.perform(patch(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName": "预约确认模板",
                      "locale": "zh-CN",
                      "templateText": "{{reservationNo}}",
                      "status": "active",
                      "version": 0
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
    }

    @Test
    void requiresPlatformAdminRoleAndPlatformTemplateManagePermission() throws Exception {
        actorProvider.actor = actor(Set.of("store_staff"), Set.of("platform.reservation_share_template.manage"));
        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.actor = actor(Set.of("platform_admin"), Set.of("queue.display.view"));
        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        actorProvider.actor = null;
        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsPersistenceErrorsToStableCode() throws Exception {
        when(service.getDefaultSeed(null)).thenThrow(new DataAccessResourceFailureException("db_down"));

        mockMvc.perform(get(ENDPOINT))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error.code").value("PERSISTENCE_ERROR"));
    }

    private static PlatformReservationShareTemplateSeed seed() {
        return seed("Restaurant reservation confirmation template V1", "en-SG", 1);
    }

    private static PlatformReservationShareTemplateSeed seed(String displayName, int version) {
        return seed(displayName, "en-SG", version);
    }

    private static PlatformReservationShareTemplateSeed seed(String displayName, String locale, int version) {
        return seed(displayName, locale, ReservationShareTemplateCatalog.defaultTemplate(), version);
    }

    private static PlatformReservationShareTemplateSeed seed(String displayName, String locale, String templateText, int version) {
        return new PlatformReservationShareTemplateSeed(
            ReservationShareTemplateCatalog.defaultSeedKey(),
            displayName,
            locale,
            templateText,
            "active",
            version,
            ReservationShareTemplateCatalog.allowedVariables()
        );
    }

    private static CurrentActor actor(Set<String> roles, Set<String> permissions) {
        return CurrentActor.storeStaff(TENANT_ID, ACTOR_ID, "platform_admin", roles, permissions, Set.of());
    }

    private static final class MutableCurrentActorProvider implements CurrentActorProvider {
        private CurrentActor actor;

        private MutableCurrentActorProvider(CurrentActor actor) {
            this.actor = actor;
        }

        @Override
        public Optional<CurrentActor> currentActor() {
            return Optional.ofNullable(actor);
        }
    }
}
