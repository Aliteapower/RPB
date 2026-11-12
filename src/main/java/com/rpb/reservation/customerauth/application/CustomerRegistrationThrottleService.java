package com.rpb.reservation.customerauth.application;
import com.rpb.reservation.common.scope.TenantScope;
import com.rpb.reservation.customerauth.application.port.out.CustomerPasswordRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class CustomerRegistrationThrottleService {
 private final CustomerPasswordRepositoryPort limits;
 public CustomerRegistrationThrottleService(CustomerPasswordRepositoryPort limits){this.limits=limits;}
 @Transactional(propagation=Propagation.REQUIRES_NEW,noRollbackFor=CustomerAuthServiceException.class)
 public void check(TenantScope scope,String phone,String remoteAddr){limits.recordAttempt(scope,"registration:"+phone,remoteAddr);}
}
