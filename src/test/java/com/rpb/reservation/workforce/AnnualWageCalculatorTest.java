package com.rpb.reservation.workforce;
import static org.assertj.core.api.Assertions.*;
import static com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos.*;
import com.rpb.reservation.workforce.statutory.annual.*;
import com.rpb.reservation.workforce.statutory.*;
import com.rpb.reservation.workforce.payroll.WageEvents;
import com.fasterxml.jackson.databind.*;
import java.time.*;import java.util.*;import java.math.*;import java.nio.file.*;
import org.junit.jupiter.api.Test;

class AnnualWageCalculatorTest {
 final StatutoryPolicies.Policy policy=new StatutoryPolicies.Policy(UUID.randomUUID(),"V2",2026,"SGD",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),true,1,OfficialStatutoryTemplates.forYearWithAw(2026).config());
 WageEvent event(String month,String amount){var m=YearMonth.parse(month);return new WageEvent(new WageEvents.Evidence(UUID.nameUUIDFromBytes(month.getBytes()),"bonus","AW",LocalDate.of(2026,1,1),m.atEndOfMonth(),null,m.atEndOfMonth(),month,month,"contract","annual performance"),amount,"official-synthetic");}
 Context context(List<MonthFact> facts){return new Context(UUID.randomUUID(),UUID.randomUUID(),2026,1,"source",facts,List.of(),false);}
 @Test void allFivePublicResponsesMatchMonthlyTargetsAndIndependentOldResponsibilities()throws Exception{
  var root=new ObjectMapper().readTree(Files.readString(Path.of("docs/development/evidence/2026-10-30-cpf-aw-calculator-oracles.json")));
  for(var fixture:root.get("cases")){
   var request=fixture.get("request");var response=fixture.get("response").get("result").get(0).get("payload");var cpf=new StatutoryDtos.CpfProfile("citizen",LocalDate.parse(request.get("dateOfBirth").asText()),null,"GG","");var facts=new ArrayList<MonthFact>();int index=0;
   for(var row:request.get("wagesDetails")){String month=row.get("month").asText(),ow=row.get("ordinaryWagesAmount").asText(),aw=row.get("additionalWagesAmount").asText();var paid=request.get("contributionDetails").get(index++);var base=new StatutoryCalculator().calculateMonthly(YearMonth.parse(month),cpf,new BigDecimal(ow),BigDecimal.ZERO,BigDecimal.ZERO,policy.config());boolean knownPaid=request.get("computeCpfExcessContribution").asText().equals("Y");String ee=knownPaid?paid.get("employeeContributionAmount").asText():base.employeeCpf().toPlainString(),er=knownPaid?paid.get("employerContributionAmount").asText():base.employerCpf().toPlainString();facts.add(AnnualWageRepository.fact(YearMonth.parse(month),"actual",new BigDecimal(ow),cpf,policy,ee,er,"oracle",new BigDecimal(aw).signum()==0?List.of():List.of(event(month,aw)),List.of()));}
   var assessed=new AnnualWageCalculator().assess(context(facts));assertThat(assessed.issues()).as(fixture.get("caseId").asText()).isEmpty();
   for(var row:response.get("wageContributionResults")){var actual=assessed.months().stream().filter(m->m.month().equals(row.get("month").asText())).findFirst().orElseThrow();assertThat(new BigDecimal(actual.targetEmployeeCpf())).isEqualByComparingTo(decimal(row.get("cpfPayableOnOrdinaryWageEmployee")).add(decimal(row.get("cpfPayableOnAdditionalWageEmployee"))));assertThat(new BigDecimal(actual.targetEmployerCpf())).isEqualByComparingTo(decimal(row.get("cpfPayableOnOrdinaryWageEmployer")).add(decimal(row.get("cpfPayableOnAdditionalWageEmployer"))));}
   if(request.get("computeCpfExcessContribution").asText().equals("Y")){assertThat(new BigDecimal(assessed.employeeDelta())).isEqualByComparingTo(decimal(response.get("totalCpfExcessPaidEmployee")).negate());assertThat(new BigDecimal(assessed.employerDelta())).isEqualByComparingTo(decimal(response.get("totalCpfExcessPaidEmployer")).negate());}
   if(fixture.get("caseId").asText().equals("zero_total_opposite_share_differences")){assertThat(assessed.totalDelta()).isEqualTo("0.00");assertThat(assessed.employeeDelta()).isEqualTo("-1.00");assertThat(assessed.employerDelta()).isEqualTo("1.00");assertThat(assessed.reviewRequired()).isTrue();}
  }
 }
 BigDecimal decimal(JsonNode n){return new BigDecimal(n.asText().replace(",",""));}
 @Test void completeCappedOwHasSixThousandQuotaAndUnknownIsNotZero(){var cpf=new StatutoryDtos.CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG","");var facts=new ArrayList<MonthFact>();for(int m=1;m<=12;m++)facts.add(AnnualWageRepository.fact(YearMonth.of(2026,m),"actual",new BigDecimal("9000"),cpf,policy,"1600","1360","fixture",m==12?List.of(event("2026-12","10000")):List.of(),List.of()));var a=new AnnualWageCalculator().assess(context(facts));assertThat(a.annualOW()).isEqualTo("96000.00");assertThat(a.awCeiling()).isEqualTo("6000.00");assertThat(a.months().getLast().targetEmployeeCpf()).isEqualTo("2800.00");facts.set(0,AnnualWageRepository.fact(YearMonth.of(2026,1),"unknown",null,cpf,policy,null,null,"missing",List.of(),List.of("ANNUAL_WAGES_INCOMPLETE")));var missing=new AnnualWageCalculator().assess(context(facts));assertThat(missing.awCeiling()).isNull();assertThat(missing.employeeDelta()).isNull();}
}
