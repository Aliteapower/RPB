package com.rpb.reservation.customerauth.api;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.customerauth.application.*;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/customer-auth")
public class CustomerSecurityEmailController {
    private final CustomerSecurityEmailService security;
    private final CustomerAuthApplicationService sessions;
    private final CustomerAuthCookieService cookies;
    private final PublicBookingStoreRepositoryPort stores;
    public CustomerSecurityEmailController(CustomerSecurityEmailService security,CustomerAuthApplicationService sessions,
        CustomerAuthCookieService cookies,PublicBookingStoreRepositoryPort stores){this.security=security;this.sessions=sessions;this.cookies=cookies;this.stores=stores;}

    @PostMapping("/recovery-email-code") public Success requestBinding(@RequestBody Binding r,HttpServletRequest http){
        security.requestBinding(scope(r.storeId()),principal(http),r.email(),http.getRemoteAddr());return new Success(true);
    }
    @PostMapping("/recovery-email-confirm") public Success confirmBinding(@RequestBody Binding r,HttpServletRequest http){
        security.confirmBinding(scope(r.storeId()),principal(http),r.email(),r.code(),http.getRemoteAddr());return new Success(true);
    }
    @PostMapping("/password-reset-code") public Success requestReset(@RequestBody Reset r,HttpServletRequest http){
        security.requestReset(scope(r.storeId()),r.phoneE164(),r.email(),http.getRemoteAddr());return new Success(true);
    }
    @PostMapping("/password-reset") public Success reset(@RequestBody Reset r,HttpServletRequest http){
        security.reset(scope(r.storeId()),r.phoneE164(),r.email(),r.code(),r.newPassword(),http.getRemoteAddr());return new Success(true);
    }
    private StoreScope scope(UUID id){
        if(id==null)throw new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID);
        return stores.findActiveStoreProfileByStoreId(id).orElseThrow(()->new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID)).scope();
    }
    private CustomerAuthPrincipal principal(HttpServletRequest r){return cookies.readSessionToken(r).flatMap(sessions::authenticateSession)
        .orElseThrow(()->new CustomerAuthServiceException(CustomerAuthError.UNAUTHENTICATED));}
    @ExceptionHandler(CustomerAuthServiceException.class) public ResponseEntity<?> error(CustomerAuthServiceException e){return CustomerAuthController.authError(e.error());}
    @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<?> conflict(DataIntegrityViolationException e){return CustomerAuthController.authError(CustomerAuthError.REQUEST_INVALID);}
    @ExceptionHandler(DataAccessException.class) public ResponseEntity<?> persistence(DataAccessException e){return CustomerAuthController.authError(CustomerAuthError.PERSISTENCE_ERROR);}
    public record Binding(UUID storeId,String email,String code){}
    public record Reset(UUID storeId,String phoneE164,String email,String code,String newPassword){}
    public record Success(boolean success){}
}
