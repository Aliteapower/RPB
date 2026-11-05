package com.rpb.reservation.catalog.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure line-level money calculation. Rates are configuration, never country defaults. */
public final class TaxCalculator {
 private TaxCalculator() {}
 public record Amounts(long netCents,long taxCents,long grossCents) {}
 public static Amounts calculate(long cents,int quantity,String mode,BigDecimal rate) {
  if(cents<0||cents>9999999999L||quantity<1||quantity>100000||rate==null||rate.signum()<0||rate.compareTo(BigDecimal.ONE)>0||!java.util.Set.of("inclusive","exclusive").contains(mode==null?"":mode))throw new IllegalArgumentException("Invalid tax quotation");
  long base=Math.multiplyExact(cents,quantity);
  if(mode.equals("inclusive")){long net=BigDecimal.valueOf(base).divide(BigDecimal.ONE.add(rate),0,RoundingMode.HALF_UP).longValueExact();return new Amounts(net,base-net,base);}
  long tax=BigDecimal.valueOf(base).multiply(rate).setScale(0,RoundingMode.HALF_UP).longValueExact();return new Amounts(base,tax,Math.addExact(base,tax));
 }
}
