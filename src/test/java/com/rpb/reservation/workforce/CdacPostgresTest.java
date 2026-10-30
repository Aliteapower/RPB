package com.rpb.reservation.workforce;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.statutory.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class CdacPostgresTest extends StatutoryPolicyPostgresTest {
 PayProfile cdacProfile(String status,String reference){var old=configs.findProfile(tenant,profile).orElseThrow();return approvedProfile(admin,store,profile,new ProfileWrite(old.version(),company,employee,old.effectiveFrom(),template,old.rule(),old.cpf(),old.payment(),new CdacProfile(status,reference)));}
 @Test void cdacAutomaticOverrideAndFrozenPayslipUseSavedRevisionWithoutLeakingNotes(){
  cdacProfile("applicable","eligibility privately reviewed");jdbc.update("update workforce_employees set birth_date='1990-01-01' where tenant_id=? and id=?",tenant,employee);completed();var b=action(create(),"calculate");var l=b.calculation().lines().getFirst();assertThat(l.netPay()).isEqualTo("639.50");assertThat(l.deductions()).isEqualTo("0.50");assertThat(l.grossPay()).isEqualTo("800.00");assertThat(l.employeeCpf()).isEqualTo("160.00");assertThat(l.cdacCalculation().standardAmount()).isEqualTo("0.50");assertThat(l.cdacCalculation().mode()).isEqualTo("automatic");
  var input=new InputWrite(0L,null,null,true,false,List.of(),"Review",new CdacReview("2.50","private written reviewed instruction"));b=approvedInput(admin,store,b.batch().id(),employee,input);assertThat(approvedInput(admin,store,b.batch().id(),employee,input).inputs().getFirst().version()).isEqualTo(1);b=action(b,"calculate");assertThat(b.calculation().lines().getFirst().netPay()).isEqualTo("637.50");assertThat(b.calculation().lines().getFirst().cdacCalculation().standardAmount()).isEqualTo("0.50");
  time.at("2026-11-02T01:00:00Z");b=action(action(b,"confirm"),"publish");var frozen=b.calculation();var slip=payslips.mineDetail(worker,store,b.batch().id());assertThat(slip.cdacCalculation().amount()).isEqualTo("2.50");var json=new com.rpb.reservation.workforce.application.WorkforceJson(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());assertThat(json.write(slip)).doesNotContain("private written","eligibility privately","cdacReview");
  var p=policies.resolve(tenant,YearMonth.of(2026,10));var cdac=p.config().cdac();var next=new CdacRules.Policy(cdac.effectiveFrom(),cdac.effectiveTo(),cdac.source(),List.of(new CdacRules.Band("2000","0.80"),new CdacRules.Band(null,"3")));settings.save(admin,store,p.id(),write(p.version(),2026,true,p.config().withCdac(next)));
  assertThat(batches.detail(admin,store,b.batch().id()).sourceChanged()).isTrue();assertThat(batches.detail(admin,store,b.batch().id()).calculation()).isEqualTo(frozen);
 }
 @Test void cdacDuplicateAndMissingRulesCannotBeConfirmed(){
  cdacProfile("applicable","");completed();var b=action(create(),"calculate");b=approvedInput(admin,store,b.batch().id(),employee,new InputWrite(0L,null,null,true,false,List.of(new Item("Duplicate","cdac","0.50")),"Review"));b=action(b,"calculate");assertThat(b.calculation().issues()).contains("CDAC_DUPLICATE");time.at("2026-11-02T01:00:00Z");var duplicate=b;assertThatThrownBy(()->action(duplicate,"confirm")).isInstanceOf(WorkforceException.class);
  b=approvedInput(admin,store,b.batch().id(),employee,new InputWrite(1L,null,null,true,false,List.of(),"Removed duplicate"));var p=policies.resolve(tenant,YearMonth.of(2026,10));settings.save(admin,store,p.id(),write(p.version(),2026,true,p.config().withCdac(null)));b=action(b,"calculate");assertThat(b.calculation().issues()).contains("CDAC_POLICY_MISSING");var missing=b;assertThatThrownBy(()->action(missing,"confirm")).isInstanceOf(WorkforceException.class);
 }
 @Test void cdacExemptionRequiresEvidenceAndOldClientsDoNotEraseIt(){
  assertThatThrownBy(()->cdacProfile("exempt","")).isInstanceOf(WorkforceException.class);var p=cdacProfile("exempt","Reviewed opt-out form on file");approvedProfile(admin,store,profile,new ProfileWrite(p.version(),company,employee,p.effectiveFrom(),template,p.rule(),p.cpf()));assertThat(configs.findProfile(tenant,profile).orElseThrow().cdac()).isEqualTo(p.cdac());completed();var b=action(create(),"calculate");assertThat(b.calculation().lines().getFirst().netPay()).isEqualTo("640.00");assertThat(b.calculation().lines().getFirst().cdacCalculation().mode()).isEqualTo("exempt");
  var current=configs.findProfile(tenant,profile).orElseThrow();assertThatThrownBy(()->approvedProfile(worker,store,profile,new ProfileWrite(current.version(),company,employee,current.effectiveFrom(),template,current.rule(),current.cpf(),null,new CdacProfile("applicable","")))).isInstanceOf(WorkforceException.class);
 }
 @Test void cdacRuleDatesAndProfileDuplicatesAreValidatedAtSave(){
  var p=policies.resolve(tenant,YearMonth.of(2026,10));var c=p.config().cdac();var outside=new CdacRules.Policy(LocalDate.of(2025,1,1),c.effectiveTo(),c.source(),c.bands());assertThatThrownBy(()->settings.save(admin,store,p.id(),write(p.version(),2026,true,p.config().withCdac(outside)))).isInstanceOf(WorkforceException.class);
  var old=configs.findProfile(tenant,profile).orElseThrow();var badRule=new Rule(old.rule().basis(),old.rule().rate(),"8","44",false,"1.5",List.of(new Item("Manual","cdac","0.50")));assertThatThrownBy(()->approvedProfile(admin,store,profile,new ProfileWrite(old.version(),company,employee,old.effectiveFrom(),template,badRule,old.cpf(),null,new CdacProfile("applicable","")))).isInstanceOf(WorkforceException.class);
 }
 @Test void cdacOldClientEditsPreserveOverrideAndExplicitAutomaticClearsIt(){
  cdacProfile("applicable","");completed();var b=create();b=approvedInput(admin,store,b.batch().id(),employee,new InputWrite(0L,null,null,true,false,List.of(),"Review",new CdacReview("2.50","Written amount")));
  b=approvedInput(admin,store,b.batch().id(),employee,new InputWrite(1L,null,null,true,false,List.of(),"Legacy client edited other inputs"));assertThat(b.inputs().getFirst().cdacReview().amount()).isEqualTo("2.50");
  b=approvedInput(admin,store,b.batch().id(),employee,new InputWrite(2L,null,null,true,false,List.of(),"Explicitly return to automatic",null,true));assertThat(b.inputs().getFirst().cdacReview()).isNull();b=action(b,"calculate");assertThat(b.calculation().lines().getFirst().cdacCalculation().amount()).isEqualTo("0.50");assertThat(b.calculation().lines().getFirst().netPay()).isEqualTo("639.50");
 }
}
