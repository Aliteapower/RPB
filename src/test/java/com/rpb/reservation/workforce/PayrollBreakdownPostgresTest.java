package com.rpb.reservation.workforce;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.reporting.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class PayrollBreakdownPostgresTest extends WorkforcePayrollPostgresTest {
 @Test void snapshotCategoriesBankAndReportsAreFrozenAndPrivate(){
  var old=configs.findProfile(tenant,profile).orElseThrow();
  var rule=new Rule("hourly","100","8","44",false,"1.5",List.of(new Item("Extra ordinary pay","ow_additional","100"),new Item("Cash housing","ow_accommodation","200"),new Item("Travel allowance","ow_allowance","50"),new Item("Other deduction","deduction","40"),new Item("CDAC reviewed","cdac","0.50"),new Item("Employer only","employer_charge","30")));
  approvedProfile(admin,store,profile,new ProfileWrite(1L,company,employee,old.effectiveFrom(),template,rule,old.cpf(),new PaymentPreference("bank","DBS")));
  jdbc.update("update workforce_employees set birth_date='1990-01-01' where tenant_id=? and id=?",tenant,employee);
  var b=published();String frozen=jdbc.queryForObject("select calculation::text from workforce_pay_batches where tenant_id=? and id=?",String.class,tenant,b.batch().id());
  var reports=new PayrollReportService(new PayrollAccess(access,directory,jdbc),new PayrollReportRepository(jdbc),payRepository,sources,new WorkforceJson(new ObjectMapper().findAndRegisterModules()),time);
  var r=reports.read(admin,store,"2026-10",List.of(company));var e=r.employees().getFirst();assertThat(e.breakdown().additional()).isEqualTo("100.00");assertThat(e.breakdown().accommodation()).isEqualTo("200.00");assertThat(e.breakdown().allowance()).isEqualTo("50.00");assertThat(e.breakdown().cdac()).isEqualTo("0.50");assertThat(e.totals().gross()).isEqualTo("1150.00");assertThat(e.totals().net()).isEqualTo("879.50");assertThat(e.paymentMethod()).isEqualTo("bank");assertThat(e.bank()).isEqualTo("DBS");assertThat(r.frozen().breakdown().sdl()).isEqualTo("2.00");
  assertThat(reports.export(admin,store,"2026-10",List.of(company),"items",r.fingerprint())).contains("Extra ordinary pay","deductions","employer_costs").doesNotContain("birthDate","prDate","Checked");
  var slip=payslips.mineDetail(worker,store,b.batch().id());assertThat(slip.breakdown().cdac()).isEqualTo("0.50");assertThat(slip.payment().bank()).isEqualTo("DBS");
  assertThat(jdbc.queryForObject("select calculation::text from workforce_pay_batches where tenant_id=? and id=?",String.class,tenant,b.batch().id())).isEqualTo(frozen);
  assertThatThrownBy(()->reports.read(worker,store,"2026-10",List.of(company))).isInstanceOf(WorkforceException.class);
 }
 @Test void actualBankPaynowIdempotencyReversalAndScopeAreEnforced(){
  var b=published();UUID id=UUID.randomUUID();var c=new PaymentWrite(employee,"200",LocalDate.of(2026,11,2),"paynow","TX1","Synthetic payment",null,"UOB");
  var first=payments.register(admin,store,b.batch().id(),id,c);assertThat(first.payments().getFirst().bank()).isEqualTo("UOB");assertThat(payments.register(admin,store,b.batch().id(),id,c).payments()).hasSize(1);
  assertThatThrownBy(()->payments.register(admin,store,b.batch().id(),id,new PaymentWrite(employee,"200",c.paymentDate(),"paynow","TX1",c.note(),null,"DBS"))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->payments.register(admin,store,b.batch().id(),UUID.randomUUID(),new PaymentWrite(employee,"1",c.paymentDate(),"bank","T",c.note(),null,""))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->payments.register(worker,store,b.batch().id(),UUID.randomUUID(),c)).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->payments.register(admin,store,b.batch().id(),UUID.randomUUID(),new PaymentWrite(employee,"200",c.paymentDate(),"paynow","REV",c.note(),id,"DBS"))).isInstanceOf(WorkforceException.class);
  payments.register(admin,store,b.batch().id(),UUID.randomUUID(),new PaymentWrite(employee,"200",c.paymentDate(),"paynow","REV",c.note(),id,"UOB"));assertThat(payslips.mineDetail(worker,store,b.batch().id()).paidAmount()).isEqualTo("0.00");
 }
}
