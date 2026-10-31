package com.rpb.reservation.workforce.payroll;

import com.rpb.reservation.workforce.domain.WorkforceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** Contract-rule arithmetic over one employee and one paying company. No persistence or policy inference. */
public final class PayCalculator {
    public record Rule(String basis,BigDecimal rate,BigDecimal dayHours,BigDecimal weekHours,boolean overtimeEnabled,BigDecimal overtimeMultiplier) {}
    public record WorkDay(LocalDate date,long workSeconds,long paidLeaveSeconds,boolean payable) {}
    public record Item(String label,String kind,BigDecimal amount,String proration) {public Item(String label,String kind,BigDecimal amount){this(label,kind,amount,null);}}
    public record Units(BigDecimal workingDays,BigDecimal payableDays,boolean generated) {public Units(BigDecimal workingDays,BigDecimal payableDays){this(workingDays,payableDays,false);}}
    public record Component(String label,String kind,BigDecimal amount) {}
    public record Calculation(BigDecimal basicPay,BigDecimal overtimePay,long normalSeconds,long overtimeSeconds,long paidLeaveSeconds,BigDecimal allowances,BigDecimal reimbursements,BigDecimal deductions,BigDecimal employerCharges,BigDecimal grossPay,BigDecimal netBeforeCpf,List<Component> components) {}
    private static final BigDecimal ZERO=BigDecimal.ZERO,TWO=new BigDecimal("2"),HOUR=new BigDecimal("3600");

    public Calculation calculate(Rule rule,List<WorkDay> workDays,Units units,List<Item> recurring,List<Item> adjustments){
        validate(rule,workDays,units,recurring,adjustments);
        try{return compute(rule,workDays,units,recurring,adjustments,null,null);}catch(ArithmeticException e){throw WorkforceException.invalid();}
    }
    public Calculation calculateContract(Rule rule,ContractPayCalculator.Result contract,Units units,List<Item> recurring,List<Item> adjustments){
        if(contract==null)throw WorkforceException.invalid();validate(rule,List.of(),units,recurring,adjustments);
        try{return compute(rule,List.of(),units,recurring,adjustments,contract,null);}catch(ArithmeticException e){throw WorkforceException.invalid();}
    }
    public Calculation calculate(Rule rule,List<WorkDay> workDays,Units units,List<Item> recurring,List<Item> adjustments,com.rpb.reservation.workforce.compliance.ComplianceEvidence.Terms terms){
        validate(rule,workDays,units,recurring,adjustments);
        try{return compute(rule,workDays,units,recurring,adjustments,null,terms);}catch(ArithmeticException e){throw WorkforceException.invalid();}
    }
    private Calculation compute(Rule rule,List<WorkDay> workDays,Units units,List<Item> recurring,List<Item> adjustments,ContractPayCalculator.Result contract,com.rpb.reservation.workforce.compliance.ComplianceEvidence.Terms terms){
        long normal=0,overtime=0,paidLeave=0,additional=0;
        var overtimeConfiguration=OvertimeConfiguration.resolve(rule,terms);
        boolean premium=overtimeConfiguration.premium();
        for(var day:overtimeConfiguration.allocate(workDays))if(day.payable()){
            if(terms!=null&&terms.contract().partTime())additional=Math.addExact(additional,Math.max(0,day.normalSeconds()-terms.contract().dailyHours().multiply(HOUR).longValueExact()));
            normal=Math.addExact(normal,day.normalSeconds());
            overtime=Math.addExact(overtime,day.overtimeSeconds());
            paidLeave=Math.addExact(paidLeave,day.paidLeaveSeconds());
        }
        if(contract!=null){normal=contract.normalPaySeconds();overtime=contract.overtimeSeconds();}
        BigDecimal basic=contract!=null?contract.basicPay():switch(rule.basis()){
            case "monthly" -> rule.rate().multiply(units.payableDays()).divide(units.workingDays(),2,RoundingMode.HALF_UP);
            case "daily" -> cents(rule.rate().multiply(units.payableDays()));
            case "hourly" -> BigDecimal.valueOf(Math.addExact(normal,paidLeave)).multiply(rule.rate()).divide(HOUR,2,RoundingMode.HALF_UP);
            default -> throw WorkforceException.invalid();
        };
        BigDecimal otNumerator=rule.rate().multiply(BigDecimal.valueOf(overtime)).multiply((terms!=null&&premium?rule.overtimeMultiplier().max(terms.policy().rules().overtimeMinimum()):rule.overtimeMultiplier()));
        BigDecimal otDenominator=HOUR;
        if(rule.basis().equals("monthly")){otNumerator=otNumerator.multiply(new BigDecimal("12"));otDenominator=otDenominator.multiply(new BigDecimal("52")).multiply(terms!=null&&terms.contract().partTime()?terms.contract().weeklyHours():new BigDecimal("44"));}
        else if(rule.basis().equals("daily"))otDenominator=otDenominator.multiply(terms==null?rule.dayHours():terms.contract().dailyHours());
        BigDecimal overtimePay=contract==null?otNumerator.divide(otDenominator,2,RoundingMode.HALF_UP):contract.overtimePay();
        var components=new ArrayList<Component>();components.add(new Component("Basic salary","basic",basic));components.add(new Component("Overtime","overtime",overtimePay));
        BigDecimal allowances=ZERO,reimbursements=ZERO,deductions=ZERO,employer=ZERO;
        if(terms!=null&&terms.contract().partTime()&&!rule.basis().equals("hourly")&&additional>0){var denominator=HOUR.multiply(rule.basis().equals("monthly")?terms.contract().weeklyHours().multiply(new BigDecimal("52")):terms.contract().dailyHours());var numerator=rule.rate().multiply(BigDecimal.valueOf(additional)).multiply(rule.basis().equals("monthly")?new BigDecimal("12"):BigDecimal.ONE);var extra=numerator.divide(denominator,2,RoundingMode.HALF_UP);allowances=allowances.add(extra);components.add(new Component("兼职额外普通工时","ow_additional",extra));}
        for(boolean fixed:new boolean[]{true,false})for(var item:fixed?recurring:adjustments){
            if(item==null||item.label()==null||item.label().isBlank()||item.label().length()>100||item.kind()==null||item.amount()==null||item.amount().signum()<0)throw WorkforceException.invalid();
            if(item.proration()!=null&&(!Set.of("full","payable").contains(item.proration())||!PayItemKinds.INCOME.contains(item.kind())))throw WorkforceException.invalid();
            BigDecimal amount=fixed&&rule.basis().equals("monthly")&&PayItemKinds.INCOME.contains(item.kind())&&!"full".equals(item.proration())?item.amount().multiply(units.payableDays()).divide(units.workingDays(),2,RoundingMode.HALF_UP):cents(item.amount());
            switch(item.kind()){
                case "ow_attendance","ow_allowance","ow_additional","ow_accommodation","wage_event" -> allowances=allowances.add(amount);
                case "reimbursement" -> reimbursements=reimbursements.add(amount);
                case "deduction","cdac" -> deductions=deductions.add(amount);
                case "employer_charge" -> employer=employer.add(amount);
                default -> throw WorkforceException.invalid();
            }
            components.add(new Component(item.label(),item.kind(),amount));
        }
        BigDecimal gross=cents(basic.add(overtimePay).add(allowances)),net=cents(gross.add(reimbursements).subtract(deductions));
        if(net.signum()<0)throw WorkforceException.invalid();
        return new Calculation(basic,overtimePay,normal,overtime,paidLeave,cents(allowances),cents(reimbursements),cents(deductions),cents(employer),gross,net,List.copyOf(components));
    }
    private void validate(Rule r,List<WorkDay> workDays,Units u,List<Item> recurring,List<Item> adjustments){
        validateRule(r);
        if(workDays==null||u==null||recurring==null||adjustments==null||recurring.size()>20||adjustments.size()>20)throw WorkforceException.invalid();
        if(!r.basis().equals("hourly")){
            if(u.payableDays()==null||u.payableDays().signum()<0||u.payableDays().compareTo(new BigDecimal("31"))>0||(!u.generated()&&u.payableDays().multiply(TWO).stripTrailingZeros().scale()>0))throw WorkforceException.invalid();
            if(r.basis().equals("monthly")&&(u.workingDays()==null||u.workingDays().signum()<=0||u.workingDays().compareTo(new BigDecimal("31"))>0||u.workingDays().stripTrailingZeros().scale()>0||u.payableDays().compareTo(u.workingDays())>0))throw WorkforceException.invalid();
        }
    }
    static void validateRule(Rule r){
        if(r==null||r.basis()==null||!Set.of("monthly","daily","hourly").contains(r.basis())||r.rate()==null||r.rate().signum()<0||r.dayHours()==null||r.dayHours().signum()<=0||r.dayHours().compareTo(new BigDecimal("24"))>0||r.weekHours()==null||r.weekHours().signum()<=0||r.weekHours().compareTo(new BigDecimal("168"))>0||r.overtimeMultiplier()==null||r.overtimeMultiplier().signum()<0)throw WorkforceException.invalid();
        if(r.overtimeEnabled()&&(r.overtimeMultiplier().compareTo(new BigDecimal("1.5"))<0||r.dayHours().compareTo(new BigDecimal("12"))>0||r.weekHours().compareTo(new BigDecimal("44"))>0))throw WorkforceException.invalid();
    }
    private BigDecimal cents(BigDecimal value){return value.setScale(2,RoundingMode.HALF_UP);}
}
