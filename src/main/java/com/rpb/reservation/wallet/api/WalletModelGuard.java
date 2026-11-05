package com.rpb.reservation.wallet.api;
import com.rpb.reservation.wallet.domain.WalletException;
public final class WalletModelGuard {
 private WalletModelGuard(){}
 public static void require(jakarta.servlet.http.HttpServletRequest request){require(request.getHeader("X-RPB-Wallet-Model"));}
 public static void require(String model){if(!"components-v2".equals(model))throw new WalletException("WALLET_CLIENT_UPGRADE_REQUIRED",409,"钱包账务版本已升级，请刷新页面后重新核对");}
}
