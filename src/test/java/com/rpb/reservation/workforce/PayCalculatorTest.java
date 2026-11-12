package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.payroll.PayCalculator;
import com.rpb.reservation.workforce.payroll.PayCalculator.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.BigDecimal;import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PayCalculatorTest {
    final PayCalculator calculator=new PayCalculator();final LocalDate MON=LocalDate.of(2026,10,26);
    BigDecimal n(String s){return new BigDecimal(s);}
    Rule hourly(boolean overtime){return new Rule("hourly",n("20"),n("8"),n("44"),overtime,n("1.5"));}
    WorkDay day(int offset,int work,int leave,boolean payable){return new WorkDay(MON.plusDays(offset),work*3600L,leave*3600L,payable);}
    @Test void hourlyPaySeparatesNormalWorkAndPaidLeaveFromOvertime(){
        var r=calculator.calculate(hourly(true),List.of(day(0,10,2,true)),new Units(null,null),List.of(),List.of());assertThat(r.normalSeconds()).isEqualTo(8*3600);assertThat(r.overtimeSeconds()).isEqualTo(2*3600);assertThat(r.paidLeaveSeconds()).isEqualTo(2*3600);assertThat(r.basicPay()).isEqualByComparingTo("200.00");assertThat(r.overtimePay()).isEqualByComparingTo("60.00");
    }
    @Test void sameDayCrossStoreRowsAreAggregatedBeforeDailyThreshold(){
        var r=calculator.calculate(hourly(true),List.of(day(0,5,0,true),day(0,5,0,true)),new Units(null,null),List.of(),List.of());assertThat(r.overtimeSeconds()).isEqualTo(2*3600);assertThat(r.basicPay()).isEqualByComparingTo("160.00");
    }
    @Test void dailyAndWeeklyOvertimeAreNeverPaidTwice(){
        var r=calculator.calculate(hourly(true),List.of(day(0,10,0,true),day(1,10,0,true),day(2,10,0,true),day(3,10,0,true),day(4,8,0,true)),new Units(null,null),List.of(),List.of());assertThat(r.normalSeconds()).isEqualTo(40*3600);assertThat(r.overtimeSeconds()).isEqualTo(8*3600);assertThat(r.overtimePay()).isEqualByComparingTo("240.00");
        var weekly=calculator.calculate(hourly(true),List.of(day(0,8,0,true),day(1,8,0,true),day(2,8,0,true),day(3,8,0,true),day(4,8,0,true),day(5,8,0,true)),new Units(null,null),List.of(),List.of());assertThat(weekly.overtimeSeconds()).isEqualTo(4*3600);
    }
    @Test void previousMonthContextConsumesWeeklyThresholdButIsNotPaidAgain(){
        var r=calculator.calculate(hourly(true),List.of(day(0,8,0,false),day(1,8,0,false),day(2,8,0,false),day(3,8,0,false),day(4,8,0,false),day(5,8,0,true)),new Units(null,null),List.of(),List.of());assertThat(r.normalSeconds()).isEqualTo(4*3600);assertThat(r.overtimeSeconds()).isEqualTo(4*3600);assertThat(r.basicPay()).isEqualByComparingTo("80.00");assertThat(r.overtimePay()).isEqualByComparingTo("120.00");
    }
    @Test void paidLeaveDoesNotConsumeWeeklyOvertimeThreshold(){
        var r=calculator.calculate(hourly(true),List.of(day(0,0,8,true),day(1,8,0,true),day(2,8,0,true),day(3,8,0,true),day(4,8,0,true),day(5,8,0,true)),new Units(null,null),List.of(),List.of());assertThat(r.overtimeSeconds()).isZero();assertThat(r.basicPay()).isEqualByComparingTo("960.00");
    }
    @Test void weekResetsOnMondayAndInputOrderDoesNotChangeResult(){
        var days=List.of(day(6,8,0,true),day(0,8,0,true),day(5,8,0,true),day(4,8,0,true),day(3,8,0,true),day(2,8,0,true),day(1,8,0,true),day(7,8,0,true));var r=calculator.calculate(hourly(true),days,new Units(null,null),List.of(),List.of());assertThat(r.overtimeSeconds()).isEqualTo(12*3600);assertThat(r.normalSeconds()).isEqualTo(52*3600);
    }
    @Test void monthlySalaryAndFixedOrdinaryAllowancesUseReviewedDayRatio(){
        var rule=new Rule("monthly",n("2600"),n("8"),n("44"),false,n("1.5"));var r=calculator.calculate(rule,List.of(day(0,8,0,true)),new Units(n("26"),n("13")),List.of(new Item("Fixed allowance","ow_allowance",n("200")),new Item("Expense","reimbursement",n("20"))),List.of(new Item("One-off","ow_allowance",n("50"))));assertThat(r.basicPay()).isEqualByComparingTo("1300");assertThat(r.allowances()).isEqualByComparingTo("150");assertThat(r.reimbursements()).isEqualByComparingTo("20");assertThat(r.grossPay()).isEqualByComparingTo("1450");assertThat(r.netBeforeCpf()).isEqualByComparingTo("1470");
    }
    @Test void monthlyAndDailyOvertimeUseTheirCorrectBasicHourlyRates(){
        var monthly=calculator.calculate(new Rule("monthly",n("2288"),n("8"),n("44"),true,n("1.5")),List.of(day(0,10,0,true)),new Units(n("22"),n("22")),List.of(),List.of());assertThat(monthly.overtimePay()).isEqualByComparingTo("36.00");
        var daily=calculator.calculate(new Rule("daily",n("160"),n("8"),n("44"),true,n("1.5")),List.of(day(0,10,0,true)),new Units(null,n("1.5")),List.of(),List.of());assertThat(daily.basicPay()).isEqualByComparingTo("240");assertThat(daily.overtimePay()).isEqualByComparingTo("60");
    }
    @Test void deductionsDoNotLowerGrossAndEmployerChargesDoNotLowerNet(){
        var r=calculator.calculate(hourly(false),List.of(day(0,8,0,true)),new Units(null,null),List.of(),List.of(new Item("Expense","reimbursement",n("10")),new Item("Deduction","deduction",n("20")),new Item("Levy","employer_charge",n("30"))));assertThat(r.grossPay()).isEqualByComparingTo("160");assertThat(r.netBeforeCpf()).isEqualByComparingTo("150");assertThat(r.employerCharges()).isEqualByComparingTo("30");
    }
    @Test void explicitNoOvertimeContractPaysAllHourlyFactsAtBasicRate(){
        var r=calculator.calculate(hourly(false),List.of(day(0,10,0,true)),new Units(null,null),List.of(),List.of());assertThat(r.normalSeconds()).isEqualTo(10*3600);assertThat(r.overtimeSeconds()).isZero();assertThat(r.basicPay()).isEqualByComparingTo("200");
    }
    @Test void eachMonetaryComponentRoundsToCentsWithoutRoundingHours(){
        var r=calculator.calculate(new Rule("hourly",n("17.33"),n("8"),n("44"),false,n("1.5")),List.of(new WorkDay(MON,61,0,true)),new Units(null,null),List.of(),List.of());assertThat(r.basicPay()).isEqualByComparingTo("0.29");
    }
    @Test void invalidDaysNegativeNetAndUnknownWageClassificationAreRejected(){
        var monthly=new Rule("monthly",n("2600"),n("8"),n("44"),false,n("1.5"));
        assertThatThrownBy(()->calculator.calculate(monthly,List.of(),new Units(null,null),List.of(),List.of())).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->calculator.calculate(monthly,List.of(),new Units(n("20"),n("21")),List.of(),List.of())).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->calculator.calculate(monthly,List.of(),new Units(n("20"),n("1.25")),List.of(),List.of())).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->calculator.calculate(hourly(false),List.of(day(0,1,0,true)),new Units(null,null),List.of(),List.of(new Item("Overdeduction","deduction",n("100"))))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->calculator.calculate(hourly(false),List.of(day(0,1,0,true)),new Units(null,null),List.of(),List.of(new Item("Bonus","AW",n("100"))))).isInstanceOf(WorkforceException.class);
    }
}
