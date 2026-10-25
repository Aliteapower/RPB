package com.rpb.reservation.auth.application;

import com.rpb.reservation.auth.api.*;
import com.rpb.reservation.auth.persistence.*;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthPasswordService {
 private final AuthRepository sessions;private final AuthPasswordRepository passwords;private final PasswordEncoder encoder;
 public AuthPasswordService(AuthRepository sessions,AuthPasswordRepository passwords,PasswordEncoder encoder){this.sessions=sessions;this.passwords=passwords;this.encoder=encoder;}
 /** Retain failure counters on rejected credentials; successful writes remain one atomic transaction. */
 @Transactional(noRollbackFor=AuthApiException.class)
 public void change(String token,String currentPassword,String newPassword){
  if(token==null||token.isBlank())throw new AuthApiException(AuthApiErrorCode.UNAUTHENTICATED);
  String sessionHash=AuthApplicationService.hashSessionToken(token);
  var session=sessions.findActiveSessionByHash(sessionHash).orElseThrow(()->new AuthApiException(AuthApiErrorCode.UNAUTHENTICATED));
  if(currentPassword==null||currentPassword.isBlank()||currentPassword.length()>72)throw new AuthApiException(AuthApiErrorCode.REQUEST_INVALID);
  if(newPassword==null||!newPassword.matches("^[A-Za-z0-9]{6}$"))throw new AuthApiException(AuthApiErrorCode.PASSWORD_POLICY_VIOLATION);
  var account=passwords.lock(session.account().id(),session.account().tenantId(),sessionHash);
  if(account.locked())throw new AuthApiException(AuthApiErrorCode.RATE_LIMITED);
  if(!encoder.matches(currentPassword.toLowerCase(Locale.ROOT),account.passwordHash())){passwords.failed(account);throw new AuthApiException(AuthApiErrorCode.INVALID_CREDENTIALS);}
  String normalized=newPassword.toLowerCase(Locale.ROOT);
  if(encoder.matches(normalized,account.passwordHash()))throw new AuthApiException(AuthApiErrorCode.PASSWORD_POLICY_VIOLATION);
  passwords.change(account,encoder.encode(normalized),sessionHash);
 }
}
