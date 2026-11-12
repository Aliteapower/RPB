package com.rpb.reservation.publicbooking.application;

import com.rpb.reservation.appgate.application.AppGateService;
import com.rpb.reservation.appgate.domain.AppGateDecision;
import com.rpb.reservation.appgate.domain.AppGateDenyReason;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.CustomerAuthApplicationService;
import com.rpb.reservation.customerauth.application.CustomerAuthPrincipal;
import com.rpb.reservation.customerauth.api.CustomerAuthCookieService;
import com.rpb.reservation.publicbooking.api.PublicBookingController;
import com.rpb.reservation.publicbooking.application.port.out.*;
import com.rpb.reservation.reservation.application.port.out.ReservationMealPeriodRepositoryPort;
import com.rpb.reservation.reservation.application.service.ReservationCreateApplicationService;
import com.rpb.reservation.tenant.value.TenantId;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PublicBookingCommercialAccessTest {
    final UUID tenant = UUID.randomUUID(), store = UUID.randomUUID();
    final StoreScope scope = new StoreScope(new TenantId(tenant), store);
    final PublicBookingStoreProfile profile = new PublicBookingStoreProfile(scope,"Store","UTC",null,null,null,null,null);
    final PublicBookingStoreRepositoryPort stores = mock(PublicBookingStoreRepositoryPort.class);
    final PublicBookingSettingsRepositoryPort settings = mock(PublicBookingSettingsRepositoryPort.class);
    final ReservationCreateApplicationService reservations = mock(ReservationCreateApplicationService.class);
    final CustomerAuthApplicationService customers = mock(CustomerAuthApplicationService.class);
    final AppGateService gate = mock(AppGateService.class);
    final ReservationMealPeriodRepositoryPort periods = mock(ReservationMealPeriodRepositoryPort.class);

    PublicBookingApplicationService service() {
        when(stores.findActiveStoreProfileByStoreId(store)).thenReturn(Optional.of(profile));
        when(stores.findEnabledPublicBookingStoreProfilesByTenantCode("tenant")).thenReturn(List.of(profile));
        when(settings.findSettings(scope)).thenReturn(Optional.of(new PublicBookingSettings(true,false,"percentage",100,null,null,0,30)));
        when(settings.findAvailabilityRules(scope)).thenReturn(List.of());
        return new PublicBookingApplicationService(stores,settings,(t,s,p)->Optional.empty(),(t,s)->Optional.empty(),customers,reservations,periods,
            Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"),ZoneOffset.UTC),gate);
    }
    PublicBookingCreateCommand command(String token) {
        return new PublicBookingCreateCommand(store,2,Instant.parse("2026-07-01T11:00:00Z"),LocalDate.of(2026,7,1),"+6591234567",null,"idem",token);
    }
    @ParameterizedTest
    @EnumSource(value=AppGateDenyReason.class,names={"TENANT_APP_NOT_ENABLED","TENANT_APP_EXPIRED","STORE_APP_NOT_ENABLED","APP_DISABLED"})
    void unavailableProductHidesContextAndEntryAndRejectsNewBooking(AppGateDenyReason reason) {
        var service=service();
        when(gate.evaluateCommercial("reservation_queue",tenant,store)).thenReturn(AppGateDecision.deny("reservation_queue",tenant,store,null,reason));
        assertThat(service.getContext(store,"2026-07-01")).isEmpty();
        assertThat(service.createBooking(command(null)).error()).isEqualTo(PublicBookingError.BOOKING_DISABLED);
        var cookies=mock(CustomerAuthCookieService.class);
        when(cookies.readSessionToken(any())).thenReturn(Optional.empty());
        var controller=new PublicBookingController(service,cookies);
        assertThat(controller.context(store,"2026-07-01").getStatusCode().value()).isEqualTo(404);
        var request=new PublicBookingController.PublicBookingCreateRequest(2,Instant.parse("2026-07-01T11:00:00Z"),LocalDate.of(2026,7,1),"+6591234567",null);
        var response=controller.createReservation(store,"idem",request,new org.springframework.mock.web.MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(((PublicBookingController.PublicBookingErrorResponse)response.getBody()).error()).isEqualTo("booking_disabled");
        assertThat(new PublicBookingEntryApplicationService(stores,gate).resolveTenantEntry("tenant").error()).isEqualTo(PublicBookingEntryError.STORE_NOT_FOUND);
        verifyNoInteractions(reservations);
        verify(settings,never()).findSettings(any());
    }
    @Test void validAuthorizationRestoresContextEntryAndCreation() {
        var service=service();
        when(gate.evaluateCommercial("reservation_queue",tenant,store)).thenReturn(AppGateDecision.deny("reservation_queue",tenant,store,null,AppGateDenyReason.TENANT_APP_EXPIRED));
        assertThat(service.getContext(store,"2026-07-01")).isEmpty();
        assertThat(service.createBooking(command(null)).error()).isEqualTo(PublicBookingError.BOOKING_DISABLED);
        when(gate.evaluateCommercial("reservation_queue",tenant,store)).thenReturn(AppGateDecision.allow("reservation_queue",tenant,store,null));
        when(periods.findUsePlatformSeed(scope)).thenReturn(Optional.of(false));
        when(periods.findStorePeriods(scope)).thenReturn(List.of());
        assertThat(service.getContext(store,"2026-07-01")).isPresent();
        assertThat(new PublicBookingEntryApplicationService(stores,gate).resolveTenantEntry("tenant").success()).isTrue();
        when(reservations.createReservation(any())).thenReturn(com.rpb.reservation.reservation.application.ReservationCreateResult.success(
            UUID.randomUUID(),null,"R-1",2,LocalDate.of(2026,7,1),Instant.parse("2026-07-01T11:00:00Z"),
            Instant.parse("2026-07-01T12:00:00Z"),Instant.parse("2026-07-01T11:15:00Z"),"confirmed","completed",List.of(),List.of(),UUID.randomUUID()));
        assertThat(service.createBooking(command(null)).success()).isTrue();
        verify(reservations).createReservation(any());
    }
    @Test void optionalLoginCannotCarryAnotherTenantsIdentity() {
        var service=service();
        when(gate.evaluateCommercial("reservation_queue",tenant,store)).thenReturn(AppGateDecision.allow("reservation_queue",tenant,store,null));
        when(customers.authenticateSession("foreign")).thenReturn(Optional.of(new CustomerAuthPrincipal(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"x@example.test","Guest")));
        assertThat(service.createBooking(command("foreign")).error()).isEqualTo(PublicBookingError.INVALID_SESSION);
        verifyNoInteractions(reservations);
    }
    @Test void missingStoreCannotUseTheCommercialGateAsDiscovery() {
        var service=service();
        UUID missing=UUID.randomUUID();
        when(stores.findActiveStoreProfileByStoreId(missing)).thenReturn(Optional.empty());
        assertThat(service.getContext(missing,"2026-07-01")).isEmpty();
        verifyNoInteractions(gate,reservations);
    }
    @Test void commercialAuthorizationDoesNotReenableAdministratorsBookingSwitch() {
        var service=service();
        when(gate.evaluateCommercial("reservation_queue",tenant,store)).thenReturn(AppGateDecision.allow("reservation_queue",tenant,store,null));
        when(settings.findSettings(scope)).thenReturn(Optional.of(PublicBookingSettings.disabled()));
        assertThat(service.createBooking(command(null)).error()).isEqualTo(PublicBookingError.BOOKING_DISABLED);
        verifyNoInteractions(reservations);
    }
}
