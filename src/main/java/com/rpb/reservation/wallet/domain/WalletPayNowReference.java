package com.rpb.reservation.wallet.domain;
import java.util.UUID;
public final class WalletPayNowReference {
    private WalletPayNowReference(){}
    public static String forSource(UUID source){return "W"+source.toString().replace("-","").substring(0,20);}
}
