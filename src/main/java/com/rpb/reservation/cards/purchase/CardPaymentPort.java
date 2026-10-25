package com.rpb.reservation.cards.purchase;
import com.rpb.reservation.cards.application.CardDtos.*;
import com.rpb.reservation.common.scope.StoreScope;
public interface CardPaymentPort {
 PaymentReceipt confirm(StoreScope scope,PaymentConfirm command);
 void claim(StoreScope scope,java.util.UUID purchaseId,PaymentReceipt receipt);
 PaymentReceipt recovery(StoreScope scope,PaymentConfirm command);
}
