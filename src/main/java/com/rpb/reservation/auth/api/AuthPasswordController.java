package com.rpb.reservation.auth.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.rpb.reservation.auth.application.AuthPasswordService;
import com.rpb.reservation.auth.security.AuthCookieService;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthPasswordController {
 private final AuthPasswordService passwords;private final AuthCookieService cookies;
 public AuthPasswordController(AuthPasswordService passwords,AuthCookieService cookies){this.passwords=passwords;this.cookies=cookies;}
 @PostMapping(value="/api/v1/auth/password-change",consumes=MediaType.APPLICATION_JSON_VALUE)
 public ResponseEntity<AuthLogoutResponse> change(@RequestBody(required=false) PasswordChange body,HttpServletRequest request){
  String site=request.getHeader("Sec-Fetch-Site");
  if(!"1".equals(request.getHeader("X-RPB-Account"))||"cross-site".equals(site)||"same-site".equals(site))throw new AuthApiException(AuthApiErrorCode.FORBIDDEN);
  String token=cookies.readSessionToken(request).orElseThrow(()->new AuthApiException(AuthApiErrorCode.UNAUTHENTICATED));
  if(body==null)throw new AuthApiException(AuthApiErrorCode.REQUEST_INVALID);
  passwords.change(token,body.currentPassword(),body.newPassword());
  return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(AuthLogoutResponse.ok());
 }
 @ExceptionHandler(AuthApiException.class)
 public ResponseEntity<AuthApiErrorResponse> error(AuthApiException e){return ResponseEntity.status(e.code().httpStatus()).cacheControl(CacheControl.noStore().cachePrivate()).body(AuthApiErrorResponse.of(e.code(),e.details()));}
 @ExceptionHandler(HttpMessageNotReadableException.class)
 public ResponseEntity<AuthApiErrorResponse> malformed(){return error(new AuthApiException(AuthApiErrorCode.REQUEST_INVALID));}
 public record PasswordChange(String currentPassword,String newPassword){
  @JsonAnySetter public void rejectOverride(String name,Object value){throw new AuthApiException(AuthApiErrorCode.REQUEST_INVALID);}
 }
}
