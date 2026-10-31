package com.rpb.reservation.workforce.payroll;

import java.util.*;
import com.rpb.reservation.workforce.payroll.PayrollProgressDtos.Comparison;

/** Internal optimization evidence, deliberately separate from immutable wage/receipt DTOs. */
public final class PayrollComputation {
 private PayrollComputation(){}
 public record State(PayrollIncremental.Cache cache,Comparison lastCalculation,int recalculated,int reused,long batchVersion,Set<UUID> comparisonStores){
  public State(PayrollIncremental.Cache cache,Comparison comparison,int recalculated,int reused,long version){this(cache,comparison,recalculated,reused,version,cache==null||cache.snapshot()==null?Set.of():PayrollViews.stores(cache.snapshot()));}
 }
}
