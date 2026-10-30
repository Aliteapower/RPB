package com.rpb.reservation.workforce.statutory;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Payroll-only CPF details; never reuse this object as an employee payslip response. */
public final class StatutoryDtos {
    private StatutoryDtos() {}
    public record CpfProfile(String citizenship,LocalDate birthDate,LocalDate prDate,String prMode,String electionReference,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) java.util.UUID prDocumentId) {
 public CpfProfile(String citizenship,LocalDate birthDate,LocalDate prDate,String prMode,String electionReference){this(citizenship,birthDate,prDate,prMode,electionReference,null);}
 }
    public record Result(BigDecimal employeeCpf,BigDecimal employerCpf,BigDecimal cpfWages,String rateCode,BigDecimal sdl) {}
}
