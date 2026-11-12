package com.rpb.reservation.workforce;

import com.rpb.reservation.workforce.statutory.StatutoryCalculator;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StatutoryCalculatorTest {
    final StatutoryCalculator calculator=new StatutoryCalculator();
    final YearMonth OCT=YearMonth.of(2026,10);
    BigDecimal money(String s){return new BigDecimal(s);}
    CpfProfile citizen(String birth){return new CpfProfile("citizen",LocalDate.parse(birth),null,"GG","");}
    CpfProfile spr(String birth,String date,String mode){return new CpfProfile("spr",LocalDate.parse(birth),LocalDate.parse(date),mode,mode.equals("GG")?"":"CPF approved election");}
    @Test void contributionBoundariesApplyLowWageFormulaBeforeNormalRates(){
        var p=citizen("1990-01-01");
        assertThat(calculator.calculate(OCT,p,money("50"),money("50")).employerCpf()).isEqualByComparingTo("0");
        assertThat(calculator.calculate(OCT,p,money("50.01"),money("50.01")).employerCpf()).isEqualByComparingTo("9");
        assertThat(calculator.calculate(OCT,p,money("500"),money("500")).employeeCpf()).isEqualByComparingTo("0");
        var ramp=calculator.calculate(OCT,p,money("600"),money("600"));assertThat(ramp.employeeCpf()).isEqualByComparingTo("60");assertThat(ramp.employerCpf()).isEqualByComparingTo("102");
        var edge=calculator.calculate(OCT,p,money("750"),money("750"));assertThat(edge.employeeCpf()).isEqualByComparingTo("150");assertThat(edge.employerCpf()).isEqualByComparingTo("128");
        assertThat(calculator.calculate(OCT,p,money("750.01"),money("750.01")).employeeCpf()).isEqualByComparingTo("150");
    }
    @Test void allFiveFullRateAgeGroupsUseTheirOwnRates(){
        String[] births={"1990-01-01","1968-01-01","1963-01-01","1958-01-01","1950-01-01"};
        String[] ee={"240","216","150","90","60"},er={"204","192","150","108","90"};
        for(int i=0;i<births.length;i++){var r=calculator.calculate(OCT,citizen(births[i]),money("1200"),money("1200"));assertThat(r.employeeCpf()).isEqualByComparingTo(ee[i]);assertThat(r.employerCpf()).isEqualByComparingTo(er[i]);}
    }
    @Test void birthdayMonthKeepsPreviousBandAndNextMonthChangesIt(){
        var p=citizen("1971-10-15");assertThat(calculator.calculate(OCT,p,money("1200"),money("1200")).employeeCpf()).isEqualByComparingTo("240");
        assertThat(calculator.calculate(OCT.plusMonths(1),p,money("1200"),money("1200")).employeeCpf()).isEqualByComparingTo("216");
        var leap=citizen("1956-02-29");assertThat(calculator.calculate(YearMonth.of(2026,2),leap,money("1200"),money("1200")).employeeCpf()).isEqualByComparingTo("90");assertThat(calculator.calculate(YearMonth.of(2026,3),leap,money("1200"),money("1200")).employeeCpf()).isEqualByComparingTo("60");
    }
    @Test void firstSecondThirdSprYearsChangeInMonthAfterAnniversary(){
        var first=spr("1990-01-01","2025-10-15","GG");
        assertThat(calculator.calculate(OCT,first,money("1200"),money("1200")).employeeCpf()).isEqualByComparingTo("60");
        assertThat(calculator.calculate(OCT.plusMonths(1),first,money("1200"),money("1200")).employeeCpf()).isEqualByComparingTo("180");
        var third=spr("1990-01-01","2024-10-15","GG");assertThat(calculator.calculate(OCT.plusMonths(1),third,money("1200"),money("1200")).employeeCpf()).isEqualByComparingTo("240");
    }
    @Test void graduatedSprAgeBandsAreNotReplacedWithCitizenRates(){
        String[] births={"1990-01-01","1968-01-01","1963-01-01","1958-01-01","1950-01-01"};
        String[] er1={"48","48","42","42","42"},ee2={"180","150","90","60","60"},er2={"108","72","42","42","42"};
        for(int i=0;i<births.length;i++){var a=calculator.calculate(OCT,spr(births[i],"2026-01-01","GG"),money("1200"),money("1200"));assertThat(a.employeeCpf()).isEqualByComparingTo("60");assertThat(a.employerCpf()).isEqualByComparingTo(er1[i]);var b=calculator.calculate(OCT,spr(births[i],"2025-01-01","GG"),money("1200"),money("1200"));assertThat(b.employeeCpf()).isEqualByComparingTo(ee2[i]);assertThat(b.employerCpf()).isEqualByComparingTo(er2[i]);}
    }
    @Test void approvedFullEmployerAndFullBothOptionsUseSeparateTables(){
        var fg=calculator.calculate(OCT,spr("1990-01-01","2026-01-01","FG"),money("1200"),money("1200"));assertThat(fg.employeeCpf()).isEqualByComparingTo("60");assertThat(fg.employerCpf()).isEqualByComparingTo("204");
        var fg2=calculator.calculate(OCT,spr("1968-01-01","2025-01-01","FG"),money("1200"),money("1200"));assertThat(fg2.employeeCpf()).isEqualByComparingTo("150");assertThat(fg2.employerCpf()).isEqualByComparingTo("192");
        var ff=calculator.calculate(OCT,spr("1990-01-01","2026-01-01","FF"),money("1200"),money("1200"));assertThat(ff.employeeCpf()).isEqualByComparingTo("240");assertThat(ff.employerCpf()).isEqualByComparingTo("204");
        assertThatThrownBy(()->calculator.calculate(OCT,new CpfProfile("spr",LocalDate.of(1990,1,1),LocalDate.of(2026,1,1),"FG",""),money("1200"),money("1200"))).isInstanceOf(WorkforceException.class);
    }
    @Test void ordinaryWageCeilingAndWholeDollarRoundingAreAppliedOnce(){
        var cap=calculator.calculate(OCT,citizen("1990-01-01"),money("10000"),money("10000"));assertThat(cap.cpfWages()).isEqualByComparingTo("8000");assertThat(cap.employeeCpf()).isEqualByComparingTo("1600");assertThat(cap.employerCpf()).isEqualByComparingTo("1360");
        var cents=calculator.calculate(OCT,citizen("1990-01-01"),money("1003.27"),money("1003.27"));assertThat(cents.employeeCpf()).isEqualByComparingTo("200");assertThat(cents.employerCpf()).isEqualByComparingTo("171");
    }
    @Test void sdlKeepsIndividualPrecisionUntilCompanyAggregation(){
        var p=new CpfProfile("foreign",null,null,"GG","");assertThat(calculator.calculate(OCT,p,money("0"),money("0")).sdl()).isEqualByComparingTo("0");assertThat(calculator.calculate(OCT,p,money("100"),money("100")).sdl()).isEqualByComparingTo("2");
        var a=calculator.calculate(OCT,p,money("1000.01"),money("1000.01"));assertThat(a.employeeCpf()).isEqualByComparingTo("0");assertThat(a.employerCpf()).isEqualByComparingTo("0");assertThat(a.sdl()).isEqualByComparingTo("2.500025");
        assertThat(calculator.calculate(OCT,p,money("5000"),money("5000")).sdl()).isEqualByComparingTo("11.25");
    }
    @Test void unsupportedYearAndPartialSprConversionMonthCannotSilentlyUseWrongRate(){
        assertThatThrownBy(()->calculator.calculate(YearMonth.of(2027,1),citizen("1990-01-01"),money("1000"),money("1000"))).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_PAYROLL_UNSUPPORTED");
        assertThatThrownBy(()->calculator.calculate(OCT,spr("1990-01-01","2026-10-15","GG"),money("1000"),money("1000"))).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_CPF_PARTIAL_MONTH");
        assertThat(calculator.calculate(OCT,spr("1990-01-01","2026-10-01","GG"),money("1000"),money("1000")).employeeCpf()).isEqualByComparingTo("50");
    }
    @Test void negativeWagesFutureBirthAndInvalidResidentStatusAreRejected(){
        assertThatThrownBy(()->calculator.calculate(OCT,null,money("1000"),money("1000"))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->calculator.calculate(OCT,citizen("1990-01-01"),money("-1"),money("0"))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->calculator.calculate(OCT,citizen("2030-01-01"),money("1000"),money("1000"))).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->calculator.calculate(OCT,new CpfProfile("unknown",null,null,"GG",""),money("1000"),money("1000"))).isInstanceOf(WorkforceException.class);
    }
}
