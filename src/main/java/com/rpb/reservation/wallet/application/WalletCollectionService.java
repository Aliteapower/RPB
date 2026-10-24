package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.wallet.domain.*;
import com.rpb.reservation.wallet.persistence.WalletCollectionRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.rpb.reservation.wallet.application.WalletCommands.*;

@Service
public class WalletCollectionService {
    private final WalletCollectionRepository repository;
    private final WalletCollectionPort port;
    private final com.rpb.reservation.wallet.proof.WalletCollectionProofService proofs;
    public WalletCollectionService(WalletCollectionRepository repository,WalletCollectionPort port) {this(repository,port,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public WalletCollectionService(WalletCollectionRepository repository,WalletCollectionPort port,com.rpb.reservation.wallet.proof.WalletCollectionProofService proofs) {this.repository=repository;this.port=port;this.proofs=proofs;}
    public boolean paynowConfigured(StoreScope scope) {return port.paynowConfigured(scope);}
    public WalletViews.Collection confirm(StoreScope scope,UUID topup,UUID bill,long expected,CollectionConfirm c,UUID actor,List<String> channels) {
        return confirm(scope,topup,bill,expected,c,actor,channels,false);
    }
    public WalletViews.Collection confirm(StoreScope scope,UUID topup,UUID bill,long expected,CollectionConfirm c,UUID actor,List<String> channels,boolean exception) {
        if(c==null || !c.verified() || WalletMoney.cents(c.amount())!=expected || !channels.contains(WalletMoney.channel(c.channel()))) throw WalletException.invalid();
        String terminal=WalletMoney.text(c.terminalCode()).toUpperCase(Locale.ROOT);
        String account=exception?port.canonicalRecoveryAccount(scope,c.channel(),c.collectionAccount(),terminal):port.canonicalAccount(scope,c.channel(),c.collectionAccount(),terminal);
        boolean cashTopupWithoutReceipt="cash".equals(WalletMoney.channelType(c.channel())) && topup!=null && bill==null && !exception
            && (c.receiptReference()==null || c.receiptReference().isBlank());
        String receipt=cashTopupWithoutReceipt ? "CASH-TOPUP-"+topup : WalletMoney.text(c.receiptReference()).toUpperCase(Locale.ROOT);
        String note=Objects.toString(c.note(),"").trim();if(note.length()>500)throw WalletException.invalid();
        if("paynow".equals(WalletMoney.channelType(c.channel()))&&receipt.equalsIgnoreCase(WalletPayNowReference.forSource(topup!=null?topup:bill)))throw WalletException.conflict("系统付款参考号不能代替银行交易 Ref");
        if(c.proofId()!=null){if(proofs==null||exception)throw WalletException.invalid();proofs.validate(scope,topup,bill,expected,c,account);}
        var collection=repository.confirm(scope,topup,bill,c.channel(),expected,account,receipt,terminal,note,actor,exception);
        if(c.proofId()!=null)proofs.bind(scope,collection,c,actor);
        return collection;
    }
}
