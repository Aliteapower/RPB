package com.rpb.reservation.customerauth.api;

import com.rpb.reservation.customerauth.application.*;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.rpb.reservation.customerauth.api.CustomerAuthController.*;

@RestController
@RequestMapping("/api/v1/public/customer-auth")
public class CustomerPasswordAuthController {
    private final CustomerPasswordAuthService passwords;
    private final CustomerAuthApplicationService sessions;
    private final CustomerAuthCookieService cookies;
    private final PublicBookingStoreRepositoryPort stores;

    public CustomerPasswordAuthController(CustomerPasswordAuthService passwords,CustomerAuthApplicationService sessions,
        CustomerAuthCookieService cookies,PublicBookingStoreRepositoryPort stores) {
        this.passwords=passwords;this.sessions=sessions;this.cookies=cookies;this.stores=stores;
    }

    @PostMapping("/password-login")
    public ResponseEntity<CustomerAuthLoginResponse> login(@RequestBody PasswordLogin request,
        HttpServletRequest http,HttpServletResponse response) {
        if (request == null || request.storeId() == null) throw new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID);
        var store = stores.findActiveStoreProfileByStoreId(request.storeId())
            .orElseThrow(() -> new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID));
        return loginResponse(passwords.login(store.scope().tenantId().value(),request.phoneE164(),request.password(),
            http.getRemoteAddr(),http.getHeader("User-Agent")),response);
    }

    @PostMapping("/password-change")
    public ResponseEntity<CustomerAuthLoginResponse> change(@RequestBody PasswordChange request,
        HttpServletRequest http,HttpServletResponse response) {
        var principal = cookies.readSessionToken(http).flatMap(sessions::authenticateSession)
            .orElseThrow(() -> new CustomerAuthServiceException(CustomerAuthError.UNAUTHENTICATED));
        if (request == null) throw new CustomerAuthServiceException(CustomerAuthError.REQUEST_INVALID);
        return loginResponse(passwords.change(principal,request.currentPassword(),request.newPassword(),
            http.getRemoteAddr(),http.getHeader("User-Agent")),response);
    }
    private ResponseEntity<CustomerAuthLoginResponse> loginResponse(CustomerAuthLoginResult result,HttpServletResponse response) {
        cookies.writeSessionCookie(response,result.sessionToken(),result.expiresAt());
        return ResponseEntity.ok(CustomerAuthLoginResponse.from(result));
    }
    @ExceptionHandler(CustomerAuthServiceException.class)
    public ResponseEntity<CustomerAuthErrorResponse> error(CustomerAuthServiceException exception) {return authError(exception.error());}
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CustomerAuthErrorResponse> invalid(IllegalArgumentException exception) {return authError(CustomerAuthError.REQUEST_INVALID);}

    public record PasswordLogin(UUID storeId,String phoneE164,String password) {}
    public record PasswordChange(String currentPassword,String newPassword) {}
}
