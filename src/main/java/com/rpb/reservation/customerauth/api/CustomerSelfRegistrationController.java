package com.rpb.reservation.customerauth.api;
import com.rpb.reservation.customer.application.*;
import com.rpb.reservation.customerauth.application.*;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/customer-auth")
public class CustomerSelfRegistrationController {
 private final CustomerSelfRegistrationService registration;
 private final PublicBookingStoreRepositoryPort stores;
 public CustomerSelfRegistrationController(CustomerSelfRegistrationService registration,PublicBookingStoreRepositoryPort stores){this.registration=registration;this.stores=stores;}
 @GetMapping("/registration-notice")
 public Notice notice(@RequestParam UUID storeId){store(storeId);var n=CustomerPrivacyNotice.CURRENT;return new Notice(n.version(),n.textZh(),n.textEn(),"我已阅读上述个人资料用途，并同意为这些用途处理我的资料（PDPA）","I have read the data purposes above and consent to processing my data for those purposes (PDPA)");}
 @PostMapping("/register")
 public ResponseEntity<?> register(@RequestBody(required=false) RegistrationRequest request,HttpServletRequest http){
  if("cross-site".equalsIgnoreCase(http.getHeader("Sec-Fetch-Site")))return ResponseEntity.status(403).body(new Error(false,"request_invalid"));
  if(request==null||request.privacyConsent()==null)throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);
  var customer=registration.register(store(request.storeId()),new CustomerManagementCommand(request.displayName(),request.nickname(),request.phoneE164(),request.email()),request.privacyConsent().accepted(),request.privacyConsent().noticeVersion(),http.getRemoteAddr());
  return ResponseEntity.status(201).body(new Registered(true,customer.id()));
 }
 private com.rpb.reservation.common.scope.StoreScope store(UUID id){if(id==null)throw new CustomerManagementException(CustomerManagementError.REQUEST_INVALID);return stores.findActiveStoreProfileByStoreId(id).orElseThrow(()->new CustomerManagementException(CustomerManagementError.REQUEST_INVALID)).scope();}
 @ExceptionHandler(CustomerManagementException.class)
 public ResponseEntity<Error> profileError(CustomerManagementException e){boolean conflict=e.code()==CustomerManagementError.CUSTOMER_PHONE_CONFLICT;return ResponseEntity.status(conflict?409:400).body(new Error(false,conflict?"phone_already_registered":"request_invalid"));}
 @ExceptionHandler(CustomerAuthServiceException.class)
 public ResponseEntity<CustomerAuthController.CustomerAuthErrorResponse> authError(CustomerAuthServiceException e){return CustomerAuthController.authError(e.error());}
 @ExceptionHandler(DataAccessException.class)
 public ResponseEntity<Error> persistenceError(DataAccessException e){
  var cause=e.getMostSpecificCause();
  if(cause instanceof java.sql.SQLException sql&&"23505".equals(sql.getSQLState())&&sql.getMessage()!=null&&sql.getMessage().contains("ux_customers_phone_active"))return ResponseEntity.status(409).body(new Error(false,"phone_already_registered"));
  return ResponseEntity.internalServerError().body(new Error(false,"persistence_error"));
 }
 public record RegistrationRequest(UUID storeId,String displayName,String nickname,String phoneE164,String email,Consent privacyConsent){}
 public record Consent(Boolean accepted,String noticeVersion){}
 public record Notice(String version,String textZh,String textEn,String labelZh,String labelEn){}
 public record Registered(boolean success,UUID customerId){}
 public record Error(boolean success,String error){}
}
