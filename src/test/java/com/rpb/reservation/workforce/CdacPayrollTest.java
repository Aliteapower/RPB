package com.rpb.reservation.workforce;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.payroll.*;import com.rpb.reservation.workforce.statutory.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;import java.math.*;import java.util.*;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class CdacPayrollTest {
 final YearMonth month=YearMonth.of(2026,10);
 Rule rule(List<Item> items){return new Rule("monthly","1800","8","44",false,"1.5",items);}
 PayProfile profile(String status,List<Item> items){return new PayProfile(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Synthetic",LocalDate.of(2026,1,1),UUID.randomUUID(),1,rule(items),new StatutoryDtos.CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG",""),1,null,status==null?null:new CdacProfile(status,"Reviewed evidence"));}
 StatutoryPolicies.Policy policy(){var t=OfficialStatutoryTemplates.forYear(2026);return new StatutoryPolicies.Policy(UUID.randomUUID(),t.name(),2026,"SGD",t.effectiveFrom(),t.effectiveTo(),true,2,t.config());}
 LineInput input(List<Item> items,CdacReview review){return new LineInput(UUID.randomUUID(),1,"26","26",true,true,items,"reviewed",review);}
 @Test void automaticAndManualOverrideRetainStandardAmountAndPolicyVersion(){
  var p=profile("applicable",List.of());var policy=policy();var automatic=CdacPayroll.evaluate(month,p,input(List.of(),null),policy,new BigDecimal("9000"));assertThat(automatic.deduction()).isEqualByComparingTo("3");assertThat(automatic.calculation().basis()).isEqualTo("9000.00");assertThat(automatic.calculation().policyVersion()).endsWith("/v2");
  var manual=CdacPayroll.evaluate(month,p,input(List.of(),new CdacReview("2.50","Written reviewed contribution")),policy,new BigDecimal("1800"));assertThat(manual.deduction()).isEqualByComparingTo("2.50");assertThat(manual.calculation().standardAmount()).isEqualTo("0.50");assertThat(manual.calculation().mode()).isEqualTo("override");
  assertThatThrownBy(()->CdacPayroll.evaluate(month,p,input(List.of(),new CdacReview("0","")),policy,new BigDecimal("1800"))).isInstanceOf(WorkforceException.class);
 }
 @Test void exemptAndNotApplicableDoNotNeedRulesButCannotHideManualDuplicates(){
  for(String status:List.of("exempt","not_applicable")){assertThat(CdacPayroll.evaluate(month,profile(status,List.of()),input(List.of(),null),null,new BigDecimal("1800")).deduction()).isZero();assertThatThrownBy(()->CdacPayroll.evaluate(month,profile(status,List.of()),input(List.of(new Item("Old CDAC","cdac","0.50")),null),policy(),new BigDecimal("1800"))).isInstanceOfSatisfying(WorkforceException.class,e->assertThat(e.code()).isEqualTo("CDAC_DUPLICATE"));}
 }
 @Test void missingOrOutOfPeriodPolicyBlocksAutomaticAndLegacyRemainsManual(){
  assertThatThrownBy(()->CdacPayroll.evaluate(month,profile("applicable",List.of()),input(List.of(),null),null,new BigDecimal("1800"))).isInstanceOfSatisfying(WorkforceException.class,e->assertThat(e.code()).isEqualTo("CDAC_POLICY_MISSING"));
  var old=profile(null,List.of(new Item("Legacy CDAC","cdac","0.50")));assertThat(CdacPayroll.evaluate(month,old,input(List.of(),null),null,new BigDecimal("1800")).calculation()).isNull();
  assertThatThrownBy(()->CdacPayroll.profile(new CdacProfile("exempt",""),old.cpf(),old.rule())).isInstanceOf(WorkforceException.class);
 }
 @Test void oldConfigAndPlanAndInputJsonDoNotGainNullFields(){
  var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());var old=profile(null,List.of());assertThat(json.write(old)).doesNotContain("\"cdac\"");var i=input(List.of(),null);assertThat(json.write(i)).doesNotContain("cdacReview");
  var config=OfficialStatutoryTemplates.forYear(2026).config().withCdac(null);assertThat(json.write(config)).doesNotContain("\"cdac\"");assertThat(json.write(json.read(json.write(config),StatutoryPolicyConfig.class))).isEqualTo(json.write(config));
 }
}
