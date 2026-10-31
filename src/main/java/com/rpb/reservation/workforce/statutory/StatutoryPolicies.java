package com.rpb.reservation.workforce.statutory;
import java.time.LocalDate;import java.util.*;
public final class StatutoryPolicies {
 private StatutoryPolicies(){}
 public record Policy(UUID id,String name,int year,String currency,LocalDate effectiveFrom,LocalDate effectiveTo,boolean enabled,long version,StatutoryPolicyConfig config){}
 public record Write(Long expectedVersion,String name,Integer year,String currency,LocalDate effectiveFrom,LocalDate effectiveTo,Boolean enabled,StatutoryPolicyConfig config){}
 public record Template(String name,int year,String currency,LocalDate effectiveFrom,LocalDate effectiveTo,StatutoryPolicyConfig config){}
 public record Page(List<Policy> items,boolean headOffice){}
}
