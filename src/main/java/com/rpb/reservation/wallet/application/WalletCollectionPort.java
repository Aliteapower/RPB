package com.rpb.reservation.wallet.application;

import com.rpb.reservation.common.scope.StoreScope;
import java.util.UUID;

/** Collection capability only; never changes a wallet balance or grants payment-product access. */
public interface WalletCollectionPort {
    boolean paynowConfigured(StoreScope scope);
    String canonicalAccount(StoreScope scope,String channel,String account,String terminal);
    String canonicalRecoveryAccount(StoreScope scope,String channel,String account,String terminal);
    String paynowQr(StoreScope scope,long cents,UUID source);
    WalletViews.CollectionSettings settings(StoreScope scope);
    WalletViews.CollectionSettings configure(StoreScope scope,WalletCommands.CollectionSettingsUpdate command);
}
