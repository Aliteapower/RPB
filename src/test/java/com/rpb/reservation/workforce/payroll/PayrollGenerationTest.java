package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PayrollGenerationTest {
 final UUID employee=UUID.randomUUID();
 final YearMonth month=YearMonth.of(2026,10);
 Rule rule(String basis,String mode,List<Integer> days){return new Rule(basis,"3000","8","44",false,"1.5",List.of(),new Generation(mode,days));}
 SourceRef source(LocalDate date,long work,long paid,long unpaid){return new SourceRef(UUID.randomUUID(),UUID.randomUUID(),date,1,"hash",work,paid,unpaid,true);}
 @Test void fixedMonthNeedsNeitherAttendanceNorManualReview(){
  var original=new LineInput(employee,7,null,null,false,false,List.of(new Item("Bonus","ow_additional","100")),"",new CdacReview("0","Reviewed"));
  var result=PayrollGeneration.generate(rule("monthly","fixed",List.of()),original,List.of(),month,month.atDay(15),month.atDay(20));
  assertThat(result.issues()).isEmpty();assertThat(result.input().workingDays()).isEqualTo("1");assertThat(result.input().payableDays()).isEqualTo("1");assertThat(result.input().generation()).isEqualTo("fixed");assertThat(result.input().items()).isEqualTo(original.items());assertThat(result.input().version()).isEqualTo(7);assertThat(result.input().cdacReview()).isEqualTo(original.cdacReview());
 }
 @Test void monthlyWorkingCalendarIsRegeneratedForEachMonth(){
  var r=rule("monthly","attendance",List.of(1,2,3,4,5));
  for(var m:List.of(month,month.plusMonths(1))){var refs=m.atDay(1).datesUntil(m.plusMonths(1).atDay(1)).filter(d->d.getDayOfWeek().getValue()<=5).map(d->source(d,28800,0,0)).toList();
   var result=PayrollGeneration.generate(r,PayrollSources.defaultInput(employee),refs,m,m.atDay(1),m.atEndOfMonth());assertThat(result.issues()).isEmpty();assertThat(result.input().workingDays()).isEqualTo(m.equals(month)?"22":"21");assertThat(result.input().payableDays()).isEqualTo(result.input().workingDays());}
 }
 @Test void dailyUnitsUseExactApprovedWorkAndPaidLeaveNotLastMonth(){
  var result=PayrollGeneration.generate(rule("daily","attendance",List.of()),PayrollSources.defaultInput(employee),List.of(source(month.atDay(1),7200,3600,0)),month,month.atDay(1),month.atEndOfMonth());
  assertThat(result.issues()).isEmpty();assertThat(result.input().payableDays()).isEqualTo("0.375");
 }
 @Test void missingScheduledWorkdayAndUnexpectedWorkingDayBlockInsteadOfBeingAssumedPaid(){
  var result=PayrollGeneration.generate(rule("monthly","attendance",List.of(1,2,3,4,5)),PayrollSources.defaultInput(employee),List.of(source(month.atDay(3),28800,0,0)),month,month.atDay(1),month.atEndOfMonth());
  assertThat(result.issues()).contains("AUTO_SCHEDULE_MISSING","AUTO_UNEXPECTED_DAY");
 }
 @Test void monthlyAttendanceProratesOnlyEmploymentDaysAndDoesNotUseUnpaidLeaveAsPaidTime(){
  var result=PayrollGeneration.generate(rule("monthly","attendance",List.of(1,2,3,4,5)),PayrollSources.defaultInput(employee),List.of(source(month.atDay(15),14400,7200,7200)),month,month.atDay(15),month.atDay(15));
  assertThat(result.issues()).isEmpty();assertThat(result.input().workingDays()).isEqualTo("22");assertThat(result.input().payableDays()).isEqualTo("0.75");
 }
 @Test void incompatibleOrAmbiguousGenerationRulesAreRejected(){
  assertThatThrownBy(()->PayrollGeneration.validate(rule("hourly","fixed",List.of()))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->PayrollGeneration.validate(rule("monthly","attendance",List.of()))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->PayrollGeneration.validate(rule("monthly","attendance",List.of(1,1)))).isInstanceOf(WorkforceException.class);
  assertThatThrownBy(()->PayrollGeneration.validate(rule("monthly","unknown",List.of()))).isInstanceOf(WorkforceException.class);
 }
 @Test void legacyManualInputRemainsUnchanged(){var i=PayrollSources.defaultInput(employee);var r=new Rule("monthly","3000","8","44",false,"1.5",List.of());assertThat(PayrollGeneration.generate(r,i,List.of(),month,month.atDay(1),month.atEndOfMonth()).input()).isSameAs(i);}
 @Test void generationNeverInventsAnAdministratorReviewNote(){var result=PayrollGeneration.generate(rule("monthly","fixed",List.of()),PayrollSources.defaultInput(employee),List.of(),month,month.atDay(1),month.atEndOfMonth());assertThat(result.input().note()).isEmpty();}
 @Test void oldJsonRoundTripsWithoutAddingNewNullFields()throws Exception{
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();String old="{\"basis\":\"monthly\",\"rate\":\"3000\",\"dayHours\":\"8\",\"weekHours\":\"44\",\"overtimeEnabled\":false,\"overtimeMultiplier\":\"1.5\",\"items\":[{\"label\":\"Meal\",\"kind\":\"ow_allowance\",\"amount\":\"200\"}]}";
  var read=mapper.readValue(old,Rule.class);assertThat(mapper.readTree(mapper.writeValueAsString(read))).isEqualTo(mapper.readTree(old));assertThat(mapper.writeValueAsString(PayrollSources.defaultInput(employee))).doesNotContain("generation");
 }
 @Test void fullAndProratedRecurringIncomeHaveDifferentAmounts(){
  var r=new PayCalculator.Rule("monthly",new java.math.BigDecimal("3000"),new java.math.BigDecimal("8"),new java.math.BigDecimal("44"),false,new java.math.BigDecimal("1.5"));
  var c=new PayCalculator().calculate(r,List.of(),new PayCalculator.Units(new java.math.BigDecimal("22"),new java.math.BigDecimal("11")),List.of(new PayCalculator.Item("Full","ow_allowance",new java.math.BigDecimal("200"),"full"),new PayCalculator.Item("Prorated","ow_allowance",new java.math.BigDecimal("200"),"payable")),List.of());
  assertThat(c.basicPay()).isEqualByComparingTo("1500");assertThat(c.allowances()).isEqualByComparingTo("300");
 }
}
