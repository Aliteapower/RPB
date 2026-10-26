package com.rpb.reservation.customerauth.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.common.value.E164Phone;
import com.rpb.reservation.customerauth.application.port.out.*;
import com.rpb.reservation.customerauth.application.port.out.CustomerSecurityEmailPort.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verified recovery addresses and purpose-bound challenges; never uses a profile's unverified email. */
@Service
public class CustomerSecurityEmailService {
    private static final Pattern EMAIL=Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+[.][A-Z]{2,}$",Pattern.CASE_INSENSITIVE);
    private final CustomerSecurityEmailPort records;
    private final CustomerPasswordRepositoryPort passwords;
    private final CustomerEmailSettingsRepositoryPort settings;
    private final CustomerEmailDeliveryPort mail;
    private final CustomerSecurityThrottleService throttle;
    private final PasswordEncoder encoder;
    private final SecureRandom random=new SecureRandom();

    public CustomerSecurityEmailService(CustomerSecurityEmailPort records,CustomerPasswordRepositoryPort passwords,
        CustomerEmailSettingsRepositoryPort settings,CustomerEmailDeliveryPort mail,CustomerSecurityThrottleService throttle,PasswordEncoder encoder) {
        this.records=records;this.passwords=passwords;this.settings=settings;this.mail=mail;this.throttle=throttle;this.encoder=encoder;
    }

    @Transactional
    public void requestBinding(StoreScope scope,CustomerAuthPrincipal principal,String email,String ip) {
        requirePrincipal(scope,principal);
        String address=email(email);
        throttle.check(scope.tenantScope(),"bind:"+principal.customerId(),ip);
        passwords.lockCustomer(scope.tenantScope(),principal.customerId());
        Account account=bindingAccount(scope,principal,address);
        send(scope,account,"bind_email",address,configuration(scope));
    }

    @Transactional(noRollbackFor=CustomerAuthServiceException.class)
    public void confirmBinding(StoreScope scope,CustomerAuthPrincipal principal,String email,String code,String ip) {
        requirePrincipal(scope,principal);
        String address=email(email);
        throttle.check(scope.tenantScope(),"bind-confirm:"+principal.customerId(),ip);
        passwords.lockCustomer(scope.tenantScope(),principal.customerId());
        Account account=bindingAccount(scope,principal,address);
        Challenge challenge=verify(scope,account,"bind_email",address,code);
        records.bind(scope,account.id(),address);
        records.consume(scope,challenge.id());
    }

    @Transactional
    public void requestReset(StoreScope scope,String phone,String email,String ip) {
        String number=phone(phone),address=email(email);
        throttle.check(scope.tenantScope(),"reset:"+number,ip);
        CustomerEmailSettings config=configuration(scope); // same channel response for registered and unknown subjects
        var customer=records.customerByPhone(scope,number);
        if(customer.isEmpty())return;
        passwords.lockCustomer(scope.tenantScope(),customer.get());
        var account=records.account(scope,customer.get()).filter(a->a.verified()&&address.equalsIgnoreCase(a.email()));
        if(account.isEmpty())return;
        try { send(scope,account.get(),"reset_password",address,config); }
        catch(CustomerAuthServiceException e) {
            // Do not reveal a matched account through a mail-delivery error response.
            records.invalidate(scope,customer.get(),"reset_password");
        }
    }

    @Transactional(noRollbackFor=CustomerAuthServiceException.class)
    public void reset(StoreScope scope,String phone,String email,String code,String newPassword,String ip) {
        String number=phone(phone),address=email(email);
        if(newPassword==null||newPassword.isBlank()||newPassword.length()<8||newPassword.getBytes(StandardCharsets.UTF_8).length>72)
            throw error(CustomerAuthError.REQUEST_INVALID);
        throttle.check(scope.tenantScope(),"reset-confirm:"+number,ip);
        UUID customer=records.customerByPhone(scope,number).orElseThrow(()->error(CustomerAuthError.CODE_MISMATCH));
        passwords.lockCustomer(scope.tenantScope(),customer);
        Account account=records.account(scope,customer).filter(a->a.verified()&&address.equalsIgnoreCase(a.email()))
            .orElseThrow(()->error(CustomerAuthError.CODE_MISMATCH));
        Challenge challenge=verify(scope,account,"reset_password",address,code);
        String existing=passwords.passwordHash(scope.tenantScope(),customer).orElse(null);
        if(existing!=null&&encoder.matches(newPassword,existing))throw error(CustomerAuthError.REQUEST_INVALID);
        passwords.savePassword(scope.tenantScope(),customer,encoder.encode(newPassword));
        passwords.completePasswordChange(scope.tenantScope(),customer);
        passwords.revokeSessions(scope.tenantScope(),customer);
        records.consume(scope,challenge.id());
        records.invalidate(scope,customer,"reset_password");
        records.invalidate(scope,customer,"bind_email");
    }

    private Challenge verify(StoreScope scope,Account account,String purpose,String email,String code) {
        Challenge c=records.latest(scope,account.customerId(),purpose).orElseThrow(()->error(CustomerAuthError.CODE_MISMATCH));
        if(c.attempts()>=5||!c.expiresAt().isAfter(Instant.now()))throw error(CustomerAuthError.CODE_MISMATCH);
        boolean match=account.id().equals(c.accountId())&&email.equals(c.email())&&code!=null&&code.matches("[0-9]{6}")
            &&MessageDigest.isEqual(c.codeHash().getBytes(StandardCharsets.US_ASCII),hash(c.id(),code).getBytes(StandardCharsets.US_ASCII));
        if(!match){records.fail(scope,c.id());throw error(CustomerAuthError.CODE_MISMATCH);}
        return c;
    }
    private void send(StoreScope scope,Account account,String purpose,String email,CustomerEmailSettings config) {
        UUID id=UUID.randomUUID();String code="%06d".formatted(random.nextInt(1_000_000));Instant expires=Instant.now().plusSeconds(600);
        records.issue(scope,account.customerId(),account.id(),purpose,id,email,hash(id,code),expires);
        try {mail.sendLoginCode(new CustomerEmailDeliveryMessage(scope.tenantId().value(),scope.storeId().value(),email,code,expires,purpose),config);}
        catch(RuntimeException e){throw error(CustomerAuthError.EMAIL_DELIVERY_FAILED);}
    }
    private Account bindingAccount(StoreScope scope,CustomerAuthPrincipal principal,String email) {
        Account account=records.account(scope,principal.customerId()).filter(a->a.id().equals(principal.authAccountId()))
            .orElseThrow(()->error(CustomerAuthError.UNAUTHENTICATED));
        if(account.email()!=null&&!email.equalsIgnoreCase(account.email())||!records.emailAvailable(scope,account.id(),email))
            throw error(CustomerAuthError.REQUEST_INVALID);
        return account;
    }
    private CustomerEmailSettings configuration(StoreScope scope) {
        return settings.findEmailSettings(scope.tenantId().value(),scope.storeId().value()).filter(CustomerEmailSettings::usableForLoginCode)
            .orElseThrow(()->error(CustomerAuthError.EMAIL_CHANNEL_NOT_CONFIGURED));
    }
    private static void requirePrincipal(StoreScope scope,CustomerAuthPrincipal p) {
        if(p==null||p.passwordChangeRequired()||!scope.tenantId().value().equals(p.tenantId()))throw error(CustomerAuthError.UNAUTHENTICATED);
    }
    private static String email(String value) {
        if(value==null||value.length()>254||!EMAIL.matcher(value.trim()).matches())throw error(CustomerAuthError.REQUEST_INVALID);
        return value.trim().toLowerCase(Locale.ROOT);
    }
    private static String phone(String value) {
        if(value==null||value.isBlank())throw error(CustomerAuthError.REQUEST_INVALID);
        try {return new E164Phone(value.trim()).value();}
        catch(IllegalArgumentException e){throw error(CustomerAuthError.REQUEST_INVALID);}
    }
    private static String hash(UUID id,String code) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((id+"|"+code).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static CustomerAuthServiceException error(CustomerAuthError error){return new CustomerAuthServiceException(error);}
}
