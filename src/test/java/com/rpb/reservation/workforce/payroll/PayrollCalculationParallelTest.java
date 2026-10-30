package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Employee;
import com.rpb.reservation.workforce.statutory.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PayrollCalculationParallelTest {
 private final YearMonth month=YearMonth.of(2026,10);
 private final UUID company=UUID.randomUUID();
 @Test void parallelSnapshotIsExactlySerialIncludingMoneyIssuesOrderAndMicrosecondTimestamp() {
  var clock=Clock.fixed(Instant.parse("2026-10-30T03:04:05.123456789Z"),ZoneOffset.UTC);
  var template=OfficialStatutoryTemplates.forYear(2026);
  var policy=new StatutoryPolicies.Policy(UUID.randomUUID(),template.name(),2026,"SGD",template.effectiveFrom(),template.effectiveTo(),true,7,template.config());
  var lines=IntStream.range(0,100).mapToObj(this::line).toList();
  for(var selected:Arrays.asList(policy,null)) {
   var capture=new PayrollSources.Capture("synthetic-fingerprint",new PayrollRepository.CompanyInfo("Synthetic","TEST",7,true,1),lines,Set.of(),selected);
   var expected=new PayrollCalculationService(clock).calculate(capture,month);
   try(var compute=new PayrollComputeExecutor(4)) {
    for(int i=0;i<3;i++)assertThat(new PayrollCalculationService(clock,compute).calculate(capture,month)).isEqualTo(expected);
   }
   assertThat(expected.lines()).hasSize(100);assertThat(expected.totalGross()).isNotEqualTo("0.00");
   if(selected!=null){assertThat(expected.totalEmployeeCpf()).isNotEqualTo("0.00");assertThat(expected.totalSdl()).isNotEqualTo("0.00");}
   assertThat(expected.calculatedAt()).isEqualTo(Instant.parse("2026-10-30T03:04:05.123456Z"));
  }
 }
 @Test void emptyCaptureRetainsSerialIssuesAndAmounts() {
  var clock=Clock.fixed(Instant.EPOCH,ZoneOffset.UTC);var capture=new PayrollSources.Capture("empty",new PayrollRepository.CompanyInfo("Synthetic","TEST",7,true,1),List.of(),Set.of(),null);
  try(var compute=new PayrollComputeExecutor(2)){assertThat(new PayrollCalculationService(clock,compute).calculate(capture,month)).isEqualTo(new PayrollCalculationService(clock).calculate(capture,month));}
 }
 private PayrollSources.CapturedLine line(int i) {
  var id=UUID.randomUUID();var employee=new Employee(id,"E"+i,"Synthetic "+i,"","","",month.atDay(1),null,"active",company,month.atDay(1),null,List.of(),null,1,false);
  var basis=i%3==0?"hourly":i%3==1?"daily":"monthly";
  var rule=new Rule(basis,basis.equals("hourly")?"13.17":basis.equals("daily")?"97.23":"3210.67","8","44",true,"1.5",List.of(new Item("Allowance","ow_allowance","25.17"),new Item("Expense","reimbursement","17.89")));
  var profile=new PayProfile(UUID.randomUUID(),company,id,employee.name(),month.atDay(1),null,0,rule,new StatutoryDtos.CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG",""),1,null,new CdacProfile("applicable","reviewed"));
  var input=new LineInput(id,1,"22",i%7==0?null:"17.5",i%7!=0,true,List.of(new Item("Fee","deduction","3.21")),"reviewed",null);
  var refs=List.of(new SourceRef(UUID.randomUUID(),UUID.randomUUID(),month.atDay(1),1,"source",37001+i,1800,600,true));
  return new PayrollSources.CapturedLine(employee,profile,input,refs,List.of("ISSUE_"+(i%5),"SHARED"),i%9==0);
 }
}
