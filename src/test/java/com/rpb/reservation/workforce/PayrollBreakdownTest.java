package com.rpb.reservation.workforce;
import com.rpb.reservation.workforce.payroll.*;
import com.rpb.reservation.workforce.statutory.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class PayrollBreakdownTest {
 @Test void wageEventIsIncludedInAdditionalDisplayWithoutChangingItsClassification()throws Exception{
  var line=new ObjectMapper().readValue("""
   {"basicPay":"3000.00","overtimePay":"0.00","allowances":"500.00","reimbursements":"0.00","deductions":"0.00","grossPay":"3500.00","employeeCpf":"700.00","employerCpf":"595.00","sdl":"8.75","employerCharges":"0.00","netPay":"2800.00","components":[{"label":"AWS","kind":"wage_event","amount":"500.00"}]}
   """,PayrollDtos.PayrollLine.class);
  var display=PayBreakdown.of(line);assertThat(display.additional()).isEqualTo("500.00");assertThat(n(display.basic()).add(n(display.overtime())).add(n(display.additional())).add(n(display.allowance())).add(n(display.accommodation()))).isEqualByComparingTo(display.gross());assertThat(display.items().getFirst().kind()).isEqualTo("wage_event");
 }
 @Test void configurableIncomeDeductionAndEmployerCostsStaySeparate(){
  var c=new PayCalculator().calculate(new PayCalculator.Rule("monthly",n("1800"),n("8"),n("44"),false,n("1.5")),List.of(),new PayCalculator.Units(n("26"),n("26")),List.of(new PayCalculator.Item("Additional","ow_additional",n("100")),new PayCalculator.Item("Housing","ow_accommodation",n("200")),new PayCalculator.Item("CDAC","cdac",n("1"))),List.of(new PayCalculator.Item("Other","deduction",n("400")),new PayCalculator.Item("Levy","employer_charge",n("50")),new PayCalculator.Item("Claim","reimbursement",n("20"))));
  assertThat(c.grossPay()).isEqualByComparingTo("2100");assertThat(c.netBeforeCpf()).isEqualByComparingTo("1719");assertThat(c.deductions()).isEqualByComparingTo("401");assertThat(c.employerCharges()).isEqualByComparingTo("50");
  var cpf=new StatutoryCalculator().calculate(YearMonth.of(2026,10),new StatutoryDtos.CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG",""),c.grossPay(),c.grossPay());assertThat(cpf.employeeCpf()).isEqualByComparingTo("420");assertThat(cpf.sdl()).isEqualByComparingTo("5.25");
 }
 @Test void legacyProfileJsonIsUnchangedWhenPaymentIsAbsent(){
  var json=new WorkforceJson(new ObjectMapper().findAndRegisterModules());var p=new PayrollDtos.PayProfile(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Synthetic",LocalDate.of(2026,10,1),UUID.randomUUID(),1,new PayrollDtos.Rule("monthly","1800","8","44",false,"1.5",List.of()),new StatutoryDtos.CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG",""),1);
  String before=json.write(p);assertThat(before).doesNotContain("\"payment\"");assertThat(json.write(json.read(before,PayrollDtos.PayProfile.class))).isEqualTo(before);
 }
 private BigDecimal n(String s){return new BigDecimal(s);}
 @Test void anonymousReference24RowsReconcilesNetButFlagsSdlBasisDifference(){
  String[] gross={"1566.67","2100","3000","2700","2400","2100","4800","1800","1800","1800","2500","2900","3000","3000","1800","1800","1800","1800","1800","1800","1800","1800","1800","1800"};
  String[] other={"0","400","0","0","200","300","1600"};
  BigDecimal total=n("0"),net=n("0"),ee=n("0"),er=n("0"),sdl=n("0");
  var config=OfficialStatutoryTemplates.forYear(2026).config();
  for(int i=0;i<gross.length;i++){
   // Synthetic identities selected only to exercise the reference amounts; real ages/statuses are unknown.
   var birth=LocalDate.of(i==15?1963:i==20?1968:i==22?1950:1990,1,1);
   var cpf=new StatutoryCalculator().calculate(YearMonth.of(2026,10),new StatutoryDtos.CpfProfile(i<7?"foreign":"citizen",birth,null,"GG",""),n(gross[i]),n(gross[i]),config);
   var cdac=i<7?n("0"):i>=10&&i<=13?n("1"):n("0.50");
   total=total.add(n(gross[i]));ee=ee.add(cpf.employeeCpf());er=er.add(cpf.employerCpf());sdl=sdl.add(cpf.sdl());net=net.add(n(gross[i]).subtract(cpf.employeeCpf()).subtract(cdac).subtract(i<7?n(other[i]):n("0")));
  }
  assertThat(total).isEqualByComparingTo("53466.67");assertThat(ee).isEqualByComparingTo("6519");assertThat(er).isEqualByComparingTo("5646");assertThat(net).isEqualByComparingTo("44437.17");assertThat(sdl).isEqualByComparingTo("132.916675");assertThat(sdl.setScale(0,RoundingMode.FLOOR)).isEqualByComparingTo("132");
 }
}
