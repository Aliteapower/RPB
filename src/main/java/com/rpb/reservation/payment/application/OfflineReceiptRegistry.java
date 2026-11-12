package com.rpb.reservation.payment.application;
import com.rpb.reservation.common.scope.StoreScope;
import com.rpb.reservation.payment.persistence.OfflineReceiptClaimRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class OfflineReceiptRegistry {
 private final OfflineReceiptClaimRepository claims;
 public OfflineReceiptRegistry(OfflineReceiptClaimRepository claims){this.claims=claims;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void claim(StoreScope s,UUID source,String type,String channel,String account,String receipt){claims.claim(s,source,type,channel,account,receipt);}
}
