package com.rpb.reservation.payment.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.provider.*;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** A narrow internal collection-preview boundary. A QR preview is never a received-payment fact. */
@Service
public class OfflinePayNowCollectionService {
    private final PaymentMethodProfileService profiles;
    private final PayNowQrPayloadBuilder qr;
    public OfflinePayNowCollectionService(PaymentMethodProfileService profiles,PayNowQrPayloadBuilder qr) {this.profiles=profiles;this.qr=qr;}
    // A tenant default does not identify a wallet issuer. Require the explicitly reviewed store profile.
    private Optional<PaymentMethodProfile> active(StoreScope scope) {return profiles.findStoreProfile(scope).filter(p->"active".equals(p.status()) && "SGD".equals(p.currency()));}
    public Optional<PaymentMethodProfile> profile(StoreScope scope) {return profiles.findEffectiveProfile(scope);}
    public PaymentMethodProfile configure(StoreScope scope,Integer version,String status,String type,String mobile,String uen,String name) {
        var current=profiles.findStoreProfile(scope);
        if(current.isPresent()?(version==null || version!=current.get().version()):version!=null)throw new PaymentServiceException(PaymentServiceErrorCode.VERSION_CONFLICT);
        String config=profiles.findEffectiveProfile(scope).map(PaymentMethodProfile::configJson).orElse("{}");
        // -1 makes an unexpected concurrent insert conflict instead of overwriting its version 0.
        return profiles.updateProfile(scope,new PaymentMethodProfileCommand("paynow",status,type,mobile,uen,name,"SGD",config,version==null?-1:version));
    }
    public boolean configured(StoreScope scope) {return active(scope).isPresent();}
    public String collectionAccount(StoreScope scope) {
        var p=active(scope).orElseThrow(()->new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_PROFILE_NOT_FOUND));
        return "paynow:"+p.paynowType()+":"+("uen".equals(p.paynowType())?p.paynowUen():p.paynowMobile()).trim().toUpperCase();
    }
    public String preview(StoreScope scope,long cents,String reference) {
        var p=active(scope).orElseThrow(()->new PaymentServiceException(PaymentServiceErrorCode.PAYMENT_PROFILE_NOT_FOUND));
        return qr.build(new PayNowQrPayloadRequest(p.paynowType(),p.paynowMobile(),p.paynowUen(),p.merchantName(),BigDecimal.valueOf(cents,2),reference));
    }
}
