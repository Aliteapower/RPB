package com.rpb.reservation.wallet.integration;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.application.OfflinePayNowCollectionService;
import com.rpb.reservation.wallet.application.WalletCollectionPort;
import com.rpb.reservation.wallet.domain.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WalletOfflineCollectionAdapter implements WalletCollectionPort {
    private final OfflinePayNowCollectionService paynow;
    public WalletOfflineCollectionAdapter(OfflinePayNowCollectionService paynow) {this.paynow=paynow;}
    public boolean paynowConfigured(StoreScope scope) {return paynow.configured(scope);}
    public String canonicalAccount(StoreScope scope,String channel,String account,String terminal) {
        return switch(WalletMoney.channelType(channel)) {
            case "other" -> "offline:"+WalletMoney.text(account).toUpperCase()+":"+WalletMoney.text(terminal).toUpperCase();
            case "cash" -> "cash:"+scope.storeId().value()+":"+WalletMoney.text(terminal).toUpperCase();
            case "paynow" -> {
                if(!paynow.configured(scope)) throw WalletException.conflict("请先在统一收款设置配置 PayNow");
                var expected=paynow.collectionAccount(scope);
                String actual=WalletMoney.text(account);String proxy=expected.substring(expected.lastIndexOf(':')+1);
                if(proxy.startsWith("+65") && actual.matches("[689]\\d{7}"))actual="+65"+actual;
                if(!expected.equalsIgnoreCase(actual) && !proxy.equalsIgnoreCase(actual))throw WalletException.conflict("实收账户与门店配置不符，请转异常复核");
                yield expected;
            }
            default -> "card:"+WalletMoney.text(account).toUpperCase()+":"+WalletMoney.text(terminal).toUpperCase();
        };
    }
    public String paynowQr(StoreScope scope,long cents,UUID source) {
        if(!paynow.configured(scope)) throw WalletException.conflict("门店未配置 PayNow");
        return paynow.preview(scope,cents,WalletPayNowReference.forSource(source));
    }
    public String canonicalRecoveryAccount(StoreScope scope,String channel,String account,String terminal) {
        if(!"paynow".equals(WalletMoney.channelType(channel)))return canonicalAccount(scope,channel,account,terminal);
        // Manager-attested external facts must remain recordable after profile changes/disablement.
        // This path never credits a wallet or settles a bill.
        String actual=WalletMoney.text(account).toUpperCase(java.util.Locale.ROOT);
        if(actual.matches("PAYNOW:(UEN|MOBILE):[A-Z0-9+]{1,32}"))return "paynow:"+actual.substring(7,actual.lastIndexOf(':')).toLowerCase(java.util.Locale.ROOT)+actual.substring(actual.lastIndexOf(':'));
        if(actual.matches("[689]\\d{7}"))actual="+65"+actual;
        if(!actual.matches("[A-Z0-9+]{1,32}"))throw WalletException.invalid();
        return "paynow:"+(actual.startsWith("+")?"mobile:":"uen:")+actual;
    }
    public com.rpb.reservation.wallet.application.WalletViews.CollectionSettings settings(StoreScope scope) {
        return paynow.profile(scope).map(p->new com.rpb.reservation.wallet.application.WalletViews.CollectionSettings(p.storeId()==null?null:p.version(),p.storeId()==null,p.status(),p.paynowType(),p.paynowMobile(),p.paynowUen(),p.merchantName()))
            .orElse(new com.rpb.reservation.wallet.application.WalletViews.CollectionSettings(null,false,"disabled","uen",null,null,null));
    }
    public com.rpb.reservation.wallet.application.WalletViews.CollectionSettings configure(StoreScope scope,com.rpb.reservation.wallet.application.WalletCommands.CollectionSettingsUpdate c) {
        try {paynow.configure(scope,c.version(),c.status(),c.paynowType(),c.paynowMobile(),c.paynowUen(),c.merchantName());return settings(scope);}
        catch(com.rpb.reservation.payment.application.PaymentServiceException e) {
            if(e.code()==com.rpb.reservation.payment.application.PaymentServiceErrorCode.VERSION_CONFLICT)throw WalletException.conflict("收款资料已更新，请刷新后核对");
            throw WalletException.invalid();
        }
    }
}
