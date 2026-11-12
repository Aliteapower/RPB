package com.rpb.reservation.wallet.funding.domain;

import java.math.BigInteger;

/** Exact integer cents. Cumulative rounding prevents tiny repeated purchases favouring principal. */
public final class FundingAllocation {
    private FundingAllocation() {}
    public record Split(long principal,long bonus) { public long total(){return Math.addExact(principal,bonus);} }
    public static Split debit(String policy,long initialP,long initialG,long spentP,long spentG,long availableP,long availableG,long amount) {
        if(amount<0 || availableP<0 || availableG<0 || amount>Math.addExact(availableP,availableG))throw new IllegalArgumentException("funding availability");
        if("principal_first".equals(policy) || initialG==0)return new Split(Math.min(amount,availableP),amount-Math.min(amount,availableP));
        if(!"proportional".equals(policy) || initialP<=0 || initialG<0)throw new IllegalArgumentException("funding policy");
        long target=rounded(Math.addExact(Math.addExact(spentP,spentG),amount),initialP,Math.addExact(initialP,initialG));
        long p=Math.max(Math.max(0,amount-availableG),Math.min(Math.min(amount,availableP),target-spentP));
        return new Split(p,amount-p);
    }
    public static Split refund(long originalP,long originalG,long returnedP,long returnedG,long amount) {
        if(amount<0 || returnedP<0 || returnedG<0 || returnedP>originalP || returnedG>originalG || amount>originalP+originalG-returnedP-returnedG)throw new IllegalArgumentException("refund allocation");
        long target=rounded(returnedP+returnedG+amount,originalP,originalP+originalG);
        long p=Math.max(Math.max(0,amount-(originalG-returnedG)),Math.min(Math.min(amount,originalP-returnedP),target-returnedP));
        return new Split(p,amount-p);
    }
    private static long rounded(long amount,long numerator,long denominator) {
        if(denominator<=0)return 0;
        var d=BigInteger.valueOf(denominator);return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(numerator)).multiply(BigInteger.TWO).add(d).divide(d.multiply(BigInteger.TWO)).longValueExact();
    }
}
