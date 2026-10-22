package com.rpb.reservation.wallet.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** Exact minor-unit arithmetic; no browser or floating-point balance authority. */
public final class WalletMoney {
    public static final long MAX_CENTS = 9_999_999_999_999L;
    private WalletMoney() {}
    public static long cents(String amount) {
        try {
            if (amount == null || !amount.matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw WalletException.invalid();
            long cents = new BigDecimal(amount).movePointRight(2).longValueExact();
            if (cents <= 0 || cents > MAX_CENTS) throw WalletException.invalid();
            return cents;
        } catch (NumberFormatException | ArithmeticException e) { throw WalletException.invalid(); }
    }
    public static long debit(long available, long outstanding) {
        if (available < 0 || outstanding < 0) throw WalletException.invalid();
        return Math.min(available,outstanding);
    }
    public static String status(long total, long wallet, long external) {
        if (wallet < 0 || external < 0 || wallet > total || external > total-wallet) throw WalletException.conflict("收款超过账单金额");
        return wallet+external == 0 ? "unpaid" : wallet+external == total ? "paid" : "partially_paid";
    }
    public static Map<UUID,Long> allocate(long amount, Map<UUID,Long> available) {
        long total = available.values().stream().mapToLong(Long::longValue).sum();
        if (amount<=0 || amount>total || available.values().stream().anyMatch(v -> v<0))
            throw new WalletException("WALLET_REFUND_EXCEEDS_LIMIT",409,"超过可退款金额");
        Map<UUID,Long> result=new LinkedHashMap<>(); Map<UUID,BigInteger> remainders=new HashMap<>();
        available.keySet().stream().sorted(Comparator.comparing(UUID::toString)).forEach(id -> {
            BigInteger[] qr=BigInteger.valueOf(amount).multiply(BigInteger.valueOf(available.get(id))).divideAndRemainder(BigInteger.valueOf(total));
            result.put(id,qr[0].longValueExact()); remainders.put(id,qr[1]);
        });
        long left=amount-result.values().stream().mapToLong(Long::longValue).sum();
        List<UUID> order=new ArrayList<>(result.keySet());
        order.sort(Comparator.<UUID,BigInteger>comparing(remainders::get).reversed().thenComparing(UUID::toString));
        for(UUID id:order) if(left>0 && result.get(id)<available.get(id)) { result.put(id,result.get(id)+1); left--; }
        if(left!=0) throw WalletException.conflict("退款分摊失败");
        return result;
    }
    public static String channel(String channel) {
        if(!Set.of("cash","paynow","credit_card").contains(Objects.toString(channel,""))) throw WalletException.invalid();
        return channel;
    }
    public static String text(String value) {
        if(value==null || value.isBlank() || value.length()>200) throw WalletException.invalid();
        return value.trim();
    }
}
