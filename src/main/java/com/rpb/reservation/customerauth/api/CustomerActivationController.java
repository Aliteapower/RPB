package com.rpb.reservation.customerauth.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.*;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import com.rpb.reservation.tenant.value.TenantId;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CustomerActivationController {
    private final CustomerActivationService activation;
    private final CurrentActorProvider actors;
    private final PublicBookingStoreRepositoryPort stores;
    public CustomerActivationController(CustomerActivationService activation,CurrentActorProvider actors,PublicBookingStoreRepositoryPort stores){this.activation=activation;this.actors=actors;this.stores=stores;}
    @PostMapping("/api/v1/stores/{storeId}/tenant-admin/customers/{customerId}/activation-codes")
    public CustomerActivationService.Issued issue(@PathVariable UUID storeId,@PathVariable UUID customerId,@RequestBody Confirmation request,HttpServletRequest http){
        var actor=actors.currentActor().orElseThrow(()->new CustomerAuthServiceException(CustomerAuthError.UNAUTHENTICATED));
        if(actor.tenantId()==null)throw new CustomerAuthServiceException(CustomerAuthError.FORBIDDEN);
        return activation.issue(new StoreScope(new TenantId(actor.tenantId()),storeId),actor,customerId,request.identityConfirmed(),http.getRemoteAddr());
    }
    @PostMapping("/api/v1/public/customer-auth/activate")
    public Success activate(@RequestBody Activation request,HttpServletRequest http){
        if(request.storeId()==null)throw new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID);
        var store=stores.findActiveStoreProfileByStoreId(request.storeId()).orElseThrow(()->new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID));
        activation.activate(store.scope(),request.phoneE164(),request.code(),request.newPassword(),http.getRemoteAddr());
        return new Success(true);
    }
    @ExceptionHandler(CustomerAuthServiceException.class) public ResponseEntity<?> error(CustomerAuthServiceException e){return CustomerAuthController.authError(e.error());}
    @ExceptionHandler(DataAccessException.class) public ResponseEntity<?> persistence(DataAccessException e){return CustomerAuthController.authError(CustomerAuthError.PERSISTENCE_ERROR);}
    public record Confirmation(boolean identityConfirmed){}
    public record Activation(UUID storeId,String phoneE164,String code,String newPassword){}
    public record Success(boolean success){}
}
