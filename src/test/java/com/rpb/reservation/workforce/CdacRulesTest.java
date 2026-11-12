package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.statutory.CdacRules;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;import java.math.*;import java.util.*;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class CdacRulesTest {
 @Test void officialBracketsUseTotalWagesAndInclusiveUpperBounds(){
  var policy=CdacRules.official(2026);String[] wages={"0","0.01","2000","2000.01","3500","3500.01","5000","5000.01","7500","7500.01","10000"},expected={"0","0.50","0.50","1","1","1.50","1.50","2","2","3","3"};
  for(int i=0;i<wages.length;i++)assertThat(CdacRules.amount(policy,YearMonth.of(2026,10),new BigDecimal(wages[i])).amount()).isEqualByComparingTo(expected[i]);
 }
 @Test void malformedRangesAndMissingMonthAreRejected(){
  var p=CdacRules.official(2026);
  assertThatThrownBy(()->CdacRules.amount(p,YearMonth.of(2027,1),new BigDecimal("1800"))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->CdacRules.validate(new CdacRules.Policy(p.effectiveFrom(),p.effectiveTo(),p.source(),List.of(new CdacRules.Band("2000","0.50"),new CdacRules.Band("1000","1"),new CdacRules.Band(null,"3"))))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->CdacRules.validate(new CdacRules.Policy(LocalDate.of(2026,1,2),p.effectiveTo(),p.source(),p.bands()))).isInstanceOf(WorkforceException.class);
 }
}
