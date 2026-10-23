package com.rpb.reservation.wallet.transactions;

import com.rpb.reservation.wallet.domain.WalletException;
import java.util.Set;

/** Read-only business folders and viewer-relative flow; never changes money or points. */
final class TransactionFilters {
    private TransactionFilters() {}
    static String category(String requested,String type,Set<String> sources) {
        if(!requested.equals("auto"))return requested;
        if(type.startsWith("points_"))return "points";
        if(type.equals("reversal"))return sources.contains("points")?"points":"topup";
        if(type.equals("topup"))return "topup";
        if(sources.contains("bill")||sources.contains("refund"))return "consumption";
        return sources.contains("points")?"points":"topup";
    }
    static String folder(String category,String type,Set<String> sources) {
        Set<String> allowed;
        String sql;
        switch(category) {
            case "consumption" -> {allowed=Set.of("all","payment","refund");if(!sources.contains("bill")&&!sources.contains("refund"))throw WalletException.forbidden();sql="(source_type='bill' or (source_type='refund' and original_source_type='bill'))";}
            case "topup" -> {allowed=Set.of("all","topup","refund","reversal");if(!sources.contains("topup")&&!sources.contains("refund")&&!sources.contains("money_reversal"))throw WalletException.forbidden();sql="(source_type in ('topup','money_reversal') or (source_type='refund' and original_source_type='topup'))";}
            case "points" -> {allowed=Set.of("all","points_grant","points_deduct","points_redemption","reversal");if(!sources.contains("points"))throw WalletException.forbidden();sql="source_type='points'";}
            default -> {return "true";}
        }
        if(!allowed.contains(type))throw WalletException.invalid();return sql;
    }
    static String flow(boolean customer) {
        return customer?"direction":"case when source_type='points' then direction when type in ('payment','topup') then 'in' else 'out' end";
    }
}
