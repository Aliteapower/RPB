package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.statutory.*;
import com.rpb.reservation.workforce.statutory.StatutoryDtos.CpfProfile;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfiguredStatutoryCalculatorTest {
    final StatutoryCalculator calculator=new StatutoryCalculator();
    final YearMonth october=YearMonth.of(2026,10);
    final StatutoryPolicyConfig official=OfficialStatutoryTemplates.forYear(2026).config();
    final CpfProfile citizen=new CpfProfile("citizen",LocalDate.of(1990,1,1),null,"GG","");
    BigDecimal money(String value){return new BigDecimal(value);}
    CpfProfile spr(String date,String mode){return new CpfProfile("spr",citizen.birthDate(),LocalDate.parse(date),mode,mode.equals("GG")?"":"approved");}
    StatutoryDtos.Result calculate(CpfProfile profile,String wage,StatutoryPolicyConfig config){return calculator.calculate(october,profile,money(wage),money(wage),config);}
    StatutoryPolicyConfig changed(Consumer<ObjectNode> change)throws Exception{var mapper=new ObjectMapper().findAndRegisterModules();ObjectNode json=mapper.valueToTree(official);change.accept(json);return mapper.treeToValue(json,StatutoryPolicyConfig.class);}

    @Test void official2026ConfigurationMatchesLegacyForFiveTablesEveryAgeAndAllWageBoundaries(){
        for(String birth:List.of("1990-01-01","1968-01-01","1963-01-01","1958-01-01","1950-01-01")){
            var dob=LocalDate.parse(birth);
            var profiles=List.of(new CpfProfile("citizen",dob,null,"GG",""),new CpfProfile("spr",dob,LocalDate.of(2026,1,1),"GG",""),new CpfProfile("spr",dob,LocalDate.of(2025,1,1),"GG",""),new CpfProfile("spr",dob,LocalDate.of(2026,1,1),"FG","approved"),new CpfProfile("spr",dob,LocalDate.of(2025,1,1),"FG","approved"));
            for(var profile:profiles)for(String wage:List.of("0","50","50.01","500","500.01","600","750","750.01","1003.27","8000","10000")){
                var legacy=calculator.calculate(october,profile,money(wage),money(wage));
                assertThat(calculate(profile,wage,official)).as("%s %s %s",birth,profile.prMode(),wage).isEqualTo(legacy);
            }
        }
    }
    @Test void official2027UsesItsOwnSeniorRatesWhileLegacyEntryPointRemains2026Only(){
        var senior=new CpfProfile("citizen",LocalDate.of(1968,1,1),null,"GG","");
        var config=OfficialStatutoryTemplates.forYear(2027).config();
        var result=calculator.calculate(YearMonth.of(2027,1),senior,money("1200"),money("1200"),config);
        assertThat(result.employeeCpf()).isEqualByComparingTo("228");
        assertThat(result.employerCpf()).isEqualByComparingTo("198");
        assertThat(result.rateCode()).isEqualTo("CPF2027-T1-ABOVE_55_TO_60");
        assertThatThrownBy(()->calculator.calculate(YearMonth.of(2027,1),senior,money("1200"),money("1200"))).hasMessage("WORKFORCE_PAYROLL_UNSUPPORTED");
    }
    @Test void configurationControlsCeilingAgeThresholdAndEachSelectedTableRate()throws Exception{
        assertThat(calculate(citizen,"1200",official.withOwCeiling("1000")).employeeCpf()).isEqualByComparingTo("200");
        var age=changed(json->((ObjectNode)json.withArray("ageBands").get(0)).put("upperAge",30));
        assertThat(calculate(citizen,"1200",age).employeeCpf()).isEqualByComparingTo("216");
        var profiles=List.of(citizen,spr("2026-01-01","GG"),spr("2025-01-01","GG"),spr("2026-01-01","FG"),spr("2025-01-01","FG"));
        for(int i=0;i<5;i++){
            final int table=i;
            var config=changed(json->{var rate=(ObjectNode)json.withArray("tables").get(table).get("rates").get(0);rate.put("employeeRate","0.11");rate.put("employerRate","0.13");});
            var result=calculate(profiles.get(i),"1200",config);
            assertThat(result.employeeCpf()).isEqualByComparingTo("132");assertThat(result.employerCpf()).isEqualByComparingTo("156");assertThat(result.rateCode()).contains("-T"+(i+1)+"-");
        }
    }
    @Test void wageThresholdsRampOriginAndMultiplierComeFromConfiguration()throws Exception{
        var config=changed(json->{var wages=json.withArray("wageBands");((ObjectNode)wages.get(0)).put("upperWage","100");((ObjectNode)wages.get(1)).put("upperWage","400");((ObjectNode)wages.get(2)).put("upperWage","900").put("rampMultiplier","2");});
        assertThat(calculate(citizen,"100",config).employerCpf()).isZero();assertThat(calculate(citizen,"100.01",config).employerCpf()).isEqualByComparingTo("17");
        assertThat(calculate(citizen,"400",config).employeeCpf()).isZero();
        var ramp=calculate(citizen,"600",config);assertThat(ramp.employeeCpf()).isEqualByComparingTo("80");assertThat(ramp.employerCpf()).isEqualByComparingTo("102");
        assertThat(calculate(citizen,"900",config).employeeCpf()).isEqualByComparingTo("200");assertThat(calculate(citizen,"900.01",config).employeeCpf()).isEqualByComparingTo("180");
    }
    @Test void cpfRoundingAndSdlParametersAreConfiguredWithoutRoundingIndividualSdl()throws Exception{
        var rounding=changed(json->json.put("totalCpfRounding","FLOOR").put("employeeCpfRounding","CEILING"));
        var result=calculate(citizen,"1003.27",rounding);assertThat(result.employeeCpf()).isEqualByComparingTo("201");assertThat(result.employerCpf()).isEqualByComparingTo("170");
        var sdl=changed(json->{var value=(ObjectNode)json.get("sdl");value.put("rate","0.003").put("minimum","3").put("maximum","12");});
        var foreign=new CpfProfile("foreign",null,null,"GG","");
        assertThat(calculate(foreign,"0",sdl).sdl()).isZero();assertThat(calculate(foreign,"50",sdl).sdl()).isEqualByComparingTo("3");
        assertThat(calculate(foreign,"1000.01",sdl).sdl()).isEqualByComparingTo("3.00003");assertThat(calculate(foreign,"5000",sdl).sdl()).isEqualByComparingTo("12");assertThat(calculate(foreign,"1000",sdl).employeeCpf()).isZero();
    }
    @Test void configuredTimingPreservesBirthdayAndSprAnniversaryMonthsAndRejectsPartialFirstMonth(){
        var birthday=new CpfProfile("citizen",LocalDate.of(1971,10,1),null,"GG","");
        assertThat(calculate(birthday,"1200",official).employeeCpf()).isEqualByComparingTo("240");assertThat(calculator.calculate(october.plusMonths(1),birthday,money("1200"),money("1200"),official).employeeCpf()).isEqualByComparingTo("216");
        var first=spr("2025-10-15","GG");assertThat(calculate(first,"1200",official).rateCode()).contains("-T2-");assertThat(calculator.calculate(october.plusMonths(1),first,money("1200"),money("1200"),official).rateCode()).contains("-T3-");
        assertThat(calculator.calculate(october.plusMonths(1),spr("2024-10-15","GG"),money("1200"),money("1200"),official).rateCode()).contains("-T1-");assertThat(calculate(spr("2026-01-01","FF"),"1200",official).rateCode()).contains("-T1-");
        assertThatThrownBy(()->calculate(spr("2026-10-15","GG"),"1200",official)).hasMessage("WORKFORCE_CPF_PARTIAL_MONTH");
    }
    @Test void independentRoundingCannotProduceNegativeEmployerContribution()throws Exception{
        var config=changed(json->{
            json.put("totalCpfRounding","FLOOR").put("employeeCpfRounding","CEILING");
            ((ObjectNode)json.withArray("tables").get(0).get("rates").get(0)).put("employerRate","0");
        });
        assertThatThrownBy(()->calculate(citizen,"1003.27",config)).isInstanceOf(WorkforceException.class).hasMessage("WORKFORCE_INVALID");
    }
    @Test void invalidConfigurationsFailClosedInsteadOfUsingLegacyDefaults()throws Exception{
        assertThatThrownBy(()->calculate(citizen,"1200",null)).isInstanceOf(WorkforceException.class);
        var missing=changed(json->json.withArray("tables").remove(4));assertThatThrownBy(()->calculate(citizen,"1200",missing)).isInstanceOf(WorkforceException.class);
        var overlapping=changed(json->((ObjectNode)json.withArray("wageBands").get(1)).put("upperWage","50"));assertThatThrownBy(()->calculate(citizen,"1200",overlapping)).isInstanceOf(WorkforceException.class);
        var expression=changed(json->((ObjectNode)json.withArray("wageBands").get(2)).put("method","eval"));assertThatThrownBy(()->calculate(citizen,"1200",expression)).isInstanceOf(WorkforceException.class);
        assertThatThrownBy(()->calculate(citizen,"1200",official.withOwCeiling("0"))).isInstanceOf(WorkforceException.class);
    }
}
