package com.rpb.reservation.workforce.statutory;

import static com.rpb.reservation.workforce.statutory.StatutoryDtos.*;
import static com.rpb.reservation.workforce.statutory.StatutoryPolicyConfig.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.BigDecimal;
import java.time.*;
import java.util.Set;

/**
 * Singapore private-sector ordinary wages, evaluated from a validated declarative policy.
 * SDL retains individual precision; company aggregation applies the policy's company rounding.
 * The legacy entry point remains restricted to the official 2026 tables.
 */
public final class StatutoryCalculator {
    public static final int SUPPORTED_YEAR=2026;
    public static final String VERSION="SG-CPF-2026-OW-v1";
    public static final BigDecimal OW_CEILING=new BigDecimal("8000");
    private static final BigDecimal ZERO=BigDecimal.ZERO;
    private static final StatutoryPolicyConfig LEGACY_CONFIG=OfficialStatutoryTemplates.forYear(SUPPORTED_YEAR).config();

    public Result calculate(YearMonth month,CpfProfile profile,BigDecimal ordinaryWages,BigDecimal totalWages){
        validateInput(month,profile,ordinaryWages,totalWages);
        if(month.getYear()!=SUPPORTED_YEAR)throw new WorkforceException("WORKFORCE_PAYROLL_UNSUPPORTED",400);
        return calculate(month,profile,ordinaryWages,totalWages,LEGACY_CONFIG);
    }

    public Result calculate(YearMonth month,CpfProfile profile,BigDecimal ordinaryWages,BigDecimal totalWages,StatutoryPolicyConfig config){
        validateInput(month,profile,ordinaryWages,totalWages);
        StatutoryPolicyConfig.validate(config);
        if(profile.citizenship()==null||!Set.of("citizen","spr","foreign").contains(profile.citizenship()))throw WorkforceException.invalid();
        BigDecimal sdl=totalWages.signum()==0?ZERO:totalWages.multiply(number(config.sdl().rate())).max(number(config.sdl().minimum())).min(number(config.sdl().maximum()));
        String prefix="CPF"+month.getYear()+"-";
        if(profile.citizenship().equals("foreign"))return new Result(ZERO,ZERO,ZERO,prefix+"FOREIGN",sdl);
        if(profile.birthDate()==null||profile.birthDate().isAfter(month.atEndOfMonth()))throw WorkforceException.invalid();
        AgeBand age=ageBand(month,profile.birthDate(),config);
        String tableCode=tableCode(month,profile);
        var table=config.tables().stream().filter(t->t.code().equals(tableCode)).findFirst().orElseThrow(WorkforceException::invalid);
        var rate=table.rates().stream().filter(r->r.ageCode().equals(age.code())).findFirst().orElseThrow(WorkforceException::invalid);
        BigDecimal employerRate=number(rate.employerRate()),employeeRate=number(rate.employeeRate());
        BigDecimal base=ordinaryWages.min(number(config.owCeiling())),employeeRaw=ZERO,totalRaw=ZERO,previousUpper=ZERO;
        for(var band:config.wageBands()){
            if(band.upperWage()!=null&&ordinaryWages.compareTo(number(band.upperWage()))>0){previousUpper=number(band.upperWage());continue;}
            switch(band.method()){
                case "none" -> { }
                case "employer_only" -> totalRaw=ordinaryWages.multiply(employerRate);
                case "ramp" -> {employeeRaw=ordinaryWages.subtract(previousUpper).multiply(employeeRate).multiply(number(band.rampMultiplier()));totalRaw=ordinaryWages.multiply(employerRate).add(employeeRaw);}
                case "normal" -> {employeeRaw=base.multiply(employeeRate);totalRaw=base.multiply(employerRate.add(employeeRate));}
                default -> throw WorkforceException.invalid();
            }
            break;
        }
        BigDecimal employee=employeeRaw.setScale(0,rounding(config.employeeCpfRounding())),total=totalRaw.setScale(0,rounding(config.totalCpfRounding()));
        if(employee.compareTo(total)>0)throw WorkforceException.invalid();
        return new Result(employee,total.subtract(employee),base,prefix+tableCode+"-"+age.code(),sdl);
    }

    private static void validateInput(YearMonth month,CpfProfile profile,BigDecimal ordinaryWages,BigDecimal totalWages){
        if(month==null||profile==null||ordinaryWages==null||totalWages==null||ordinaryWages.signum()<0||totalWages.signum()<0||ordinaryWages.compareTo(totalWages)>0)throw WorkforceException.invalid();
    }
    private static AgeBand ageBand(YearMonth month,LocalDate birthDate,StatutoryPolicyConfig config){
        for(var band:config.ageBands())if(band.upperAge()==null||!birthDate.plusYears(band.upperAge()).isBefore(month.atDay(1)))return band;
        throw WorkforceException.invalid();
    }
    private static String tableCode(YearMonth month,CpfProfile profile){
        if(!profile.citizenship().equals("spr"))return "T1";
        if(profile.prDate()==null||profile.prDate().isAfter(month.atEndOfMonth())||profile.prDate().isBefore(profile.birthDate())||profile.prMode()==null||!Set.of("GG","FG","FF").contains(profile.prMode()))throw WorkforceException.invalid();
        if(!profile.prMode().equals("GG")&&(profile.electionReference()==null||profile.electionReference().isBlank()))throw WorkforceException.invalid();
        if(YearMonth.from(profile.prDate()).equals(month)&&profile.prDate().getDayOfMonth()!=1)throw new WorkforceException("WORKFORCE_CPF_PARTIAL_MONTH",400);
        YearMonth secondYear=YearMonth.from(profile.prDate()).plusYears(1).plusMonths(1),thirdYear=secondYear.plusYears(1);
        int year=month.isBefore(secondYear)?1:month.isBefore(thirdYear)?2:3;
        if(year==3||profile.prMode().equals("FF"))return "T1";
        if(profile.prMode().equals("FG"))return year==1?"T4":"T5";
        return year==1?"T2":"T3";
    }
}
