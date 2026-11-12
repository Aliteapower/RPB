package com.rpb.reservation.workforce.payroll;
import static org.assertj.core.api.Assertions.*;
import com.rpb.reservation.workforce.payroll.supplemental.SupplementalMoney;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupplementalMoneyTest {
 @Test void originalOwOnlyPolicyStillProducesKnownMonthlyLiabilityWithoutInventingAwCeiling(){
  var template=com.rpb.reservation.workforce.statutory.OfficialStatutoryTemplates.forYear(2026);var policy=new com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy(java.util.UUID.randomUUID(),template.name(),2026,"SGD",template.effectiveFrom(),template.effectiveTo(),true,1,template.config());var cpf=new com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile("citizen",java.time.LocalDate.of(1990,1,1),null,"GG","");var months=new java.util.ArrayList<com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos.MonthFact>();for(int i=1;i<=12;i++)months.add(com.rpb.reservation.workforce.statutory.annual.AnnualWageRepository.fact(java.time.YearMonth.of(2026,i),"actual",new BigDecimal("1000"),cpf,policy,"0","0","original policy",List.of(),List.of()));var context=new com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos.Context(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),2026,1,"original",months,List.of(),false);var assessment=new com.rpb.reservation.workforce.statutory.annual.AnnualWageCalculator().assess(context);assertThat(assessment.months()).hasSize(12);assertThat(assessment.months().getFirst().targetEmployeeCpf()).isEqualTo("200.00");assertThat(assessment.awCeiling()).isNull();assertThat(assessment.issues()).contains("STATUTORY_CAPABILITY_UNSUPPORTED");
 }
 @Test void monthlyTargetSubtractsRecognisedButBalanceSubtractsRemitted(){
  var value=SupplementalMoney.balance("2.00","1.50","1.50","0.00");
  assertThat(value.liabilityDelta()).isEqualTo("0.50");
  assertThat(value.deductionDelta()).isEqualTo("0.50");
  assertThat(value.remittanceBalance()).isEqualTo("2.00");
 }
 @Test void missingDeductionOrRemittanceRemainsUnknown(){
  var value=SupplementalMoney.balance("370.00","370.00",null,null);
  assertThat(value.liabilityDelta()).isEqualTo("0.00");
  assertThat(value.deductionDelta()).isNull();assertThat(value.remittanceBalance()).isNull();
 }
 @Test void companyFloorIsAppliedToTargetsRatherThanDelta(){
  assertThat(SupplementalMoney.companySdlDelta(List.of(new BigDecimal("2"),new BigDecimal("11.25"),new BigDecimal("3")),new BigDecimal("15"))).isEqualByComparingTo("1");
 }
 @Test void negativeLiabilityDoesNotBecomeNegativeCash(){
  assertThat(SupplementalMoney.cash("0.00","-1.00","0.00")).isEqualTo("0.00");
  assertThat(SupplementalMoney.cash("101.00","21.00","0.00")).isEqualTo("80.00");
 }
}
