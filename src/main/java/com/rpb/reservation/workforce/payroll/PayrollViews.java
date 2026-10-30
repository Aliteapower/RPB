package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import java.util.*;
final class PayrollViews {
 private PayrollViews(){}
 static BatchSummary summary(PayrollRepository.State b,int employees){var c=b.calculation();return new BatchSummary(b.id(),b.company(),c==null?b.companyName():c.companyName(),b.month().toString(),b.status(),b.version(),c==null?employees:c.lines().size(),c==null?"0.00":c.totalGross(),c==null?"0.00":c.totalNet(),c==null?"0.00":c.totalEmployerCpf(),c==null?"0.00":c.totalSdl(),c==null?"0.00":c.totalEmployerCost(),b.createdAt());}
 static Set<UUID> stores(PayrollSnapshot c){if(c==null)return Set.of();var result=new TreeSet<UUID>();c.lines().forEach(l->l.sources().forEach(s->result.add(s.storeId())));return result;}
 static Map<String,Object> audit(PayrollRepository.State b){return Map.of("companyId",b.company(),"month",b.month().toString(),"status",b.status(),"version",b.version(),"sourceFingerprint",b.calculation()==null?"":b.calculation().sourceFingerprint());}
 static List<Option> employees(PayrollSnapshot c){return c.lines().stream().map(l->new Option(l.employeeId(),l.employeeName()+" · "+l.employeeNo())).toList();}
}
