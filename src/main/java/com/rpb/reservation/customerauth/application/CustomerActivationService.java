package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customer.application.port.out.CustomerRepositoryPort;
import com.rpb.reservation.customer.domain.Customer;
import com.rpb.reservation.customer.value.CustomerId;
import com.rpb.reservation.customerauth.application.port.out.*;
import com.rpb.reservation.publicbooking.application.port.out.PublicBookingStoreRepositoryPort;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerActivationService {
    private final CustomerActivationPort codes;
    private final CustomerRepositoryPort customers;
    private final CustomerPasswordRepositoryPort passwords;
    private final CustomerAuthRepositoryPort accounts;
    private final CustomerSecurityEmailPort email;
    private final CustomerSecurityThrottleService throttle;
    private final PublicBookingStoreRepositoryPort stores;
    private final PasswordEncoder encoder;
    private final SecureRandom random=new SecureRandom();
    public CustomerActivationService(CustomerActivationPort codes,CustomerRepositoryPort customers,CustomerPasswordRepositoryPort passwords,
        CustomerAuthRepositoryPort accounts,CustomerSecurityEmailPort email,CustomerSecurityThrottleService throttle,
        PublicBookingStoreRepositoryPort stores,PasswordEncoder encoder){this.codes=codes;this.customers=customers;this.passwords=passwords;this.accounts=accounts;this.email=email;this.throttle=throttle;this.stores=stores;this.encoder=encoder;}
    public record Issued(boolean success,String code,Instant expiresAt){}

    @Transactional
    public Issued issue(StoreScope scope,CurrentActor actor,UUID customerId,boolean identityConfirmed,String ip){
        if(actor==null)throw error(CustomerAuthError.UNAUTHENTICATED);
        if(!scope.tenantId().value().equals(actor.tenantId())||!actor.canAccessStore(scope.storeId().value())
            ||!actor.roles().contains("tenant_admin")||!actor.hasPermission("tenant.admin.manage"))throw error(CustomerAuthError.FORBIDDEN);
        requireStore(scope);
        if(!identityConfirmed||customerId==null)throw error(CustomerAuthError.REQUEST_INVALID);
        throttle.check(scope.tenantScope(),"activation-issue:"+actor.actorId(),ip);
        passwords.lockCustomer(scope.tenantScope(),customerId);
        var customer=customers.findById(scope.tenantScope(),new CustomerId(customerId)).filter(c->"active".equals(c.status())&&c.phone().isPresent())
            .orElseThrow(()->error(CustomerAuthError.REQUEST_INVALID));
        if(!eligible(scope,customer))throw error(CustomerAuthError.REQUEST_INVALID);
        UUID id=UUID.randomUUID();StringBuilder value=new StringBuilder();
        for(int i=0;i<6;i++)value.append(random.nextInt(10));
        String raw=value.toString();Instant expires=Instant.now().plusSeconds(600);
        codes.issue(scope,customerId,actor.actorId(),id,hash(id,raw),expires);
        codes.audit(scope,customerId,actor.actorId(),"customer.activation.issued");
        return new Issued(true,raw,expires);
    }

    @Transactional(noRollbackFor=CustomerAuthServiceException.class)
    public void activate(StoreScope scope,String phone,String code,String newPassword,String ip){
        requireStore(scope);
        E164Phone number;
        try{if(phone==null||phone.isBlank())throw new IllegalArgumentException();number=new E164Phone(phone.trim());}
        catch(IllegalArgumentException e){throw error(CustomerAuthError.REQUEST_INVALID);}
        if(newPassword==null||newPassword.isBlank()||newPassword.length()<6||newPassword.getBytes(StandardCharsets.UTF_8).length>72)throw error(CustomerAuthError.REQUEST_INVALID);
        throttle.check(scope.tenantScope(),"activation-confirm:"+number.value(),ip);
        var found=customers.findByPhone(scope.tenantScope(),number).orElseThrow(()->error(CustomerAuthError.CODE_MISMATCH));
        passwords.lockCustomer(scope.tenantScope(),found.id().value());
        var customer=customers.findById(scope.tenantScope(),found.id()).filter(c->"active".equals(c.status())&&number.equals(c.phone()))
            .orElseThrow(()->error(CustomerAuthError.CODE_MISMATCH));
        if(!eligible(scope,customer))throw error(CustomerAuthError.CODE_MISMATCH);
        var challenge=codes.latest(scope,customer.id().value()).orElseThrow(()->error(CustomerAuthError.CODE_MISMATCH));
        if(challenge.attempts()>=5||!challenge.expiresAt().isAfter(Instant.now()))throw error(CustomerAuthError.CODE_MISMATCH);
        String raw=code==null||code.length()>32?"":code.replaceAll("[\\s-]","").toUpperCase(Locale.ROOT);
        if(!raw.matches("[0-9]{6}")||!MessageDigest.isEqual(challenge.codeHash().getBytes(StandardCharsets.US_ASCII),hash(challenge.id(),raw).getBytes(StandardCharsets.US_ASCII))){
            codes.fail(scope,challenge.id());throw error(CustomerAuthError.CODE_MISMATCH);
        }
        var account=accounts.findAccountByCustomerId(scope.tenantId().value(),customer.id().value());
        if(account.isEmpty())accounts.createAccount(UUID.randomUUID(),scope.tenantId().value(),customer.id().value(),null,customer.displayName());
        passwords.savePassword(scope.tenantScope(),customer.id().value(),encoder.encode(newPassword));
        passwords.completePasswordChange(scope.tenantScope(),customer.id().value());
        passwords.revokeSessions(scope.tenantScope(),customer.id().value());
        email.invalidateAll(scope.tenantScope(),customer.id().value());
        codes.consumeAll(scope.tenantScope(),customer.id().value());
        codes.audit(scope,customer.id().value(),customer.id().value(),"customer.activation.completed");
    }
    private boolean eligible(StoreScope scope,Customer customer){
        var id=customer.id().value();
        if(passwords.passwordHash(scope.tenantScope(),id).isPresent()&&!passwords.passwordChangeRequired(scope.tenantScope(),id))return false;
        if(accounts.findAccountByCustomerId(scope.tenantId().value(),id).filter(a->!"active".equals(a.status())).isPresent())return false;
        return email.account(scope,id).filter(a->a.verified()&&a.email()!=null).isEmpty();
    }
    private void requireStore(StoreScope scope){
        if(stores.findActiveStoreProfileByStoreId(scope.storeId().value()).filter(s->scope.equals(s.scope())).isEmpty())throw error(CustomerAuthError.FORBIDDEN);
    }
    private static String hash(UUID id,String code){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((id+"|"+code).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static CustomerAuthServiceException error(CustomerAuthError value){return new CustomerAuthServiceException(value);}
}
