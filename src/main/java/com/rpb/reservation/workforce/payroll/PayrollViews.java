package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.application.WorkforceDtos.Option;
import java.util.*;
public final class PayrollViews {
 private PayrollViews(){}
 static BatchSummary summary(PayrollRepository.State b,int employees){var c=b.calculation();return new BatchSummary(b.id(),b.company(),c==null?b.companyName():c.companyName(),b.month().toString(),b.status(),b.version(),c==null?employees:c.lines().size(),c==null?null:c.totalGross(),c==null?null:c.totalNet(),c==null?null:c.totalEmployerCpf(),c==null?null:c.totalSdl(),c==null?null:c.totalEmployerCost(),b.createdAt());}
 /** Only concrete source stores participate in authorization. Keep storeless days in the snapshot. */
 public static Set<UUID> stores(PayrollSnapshot snapshot){
  if(snapshot==null)return Set.of();
  var stores=new TreeSet<UUID>();
  for(var line:snapshot.lines()){
   for(var source:line.sources()){
    if(source.storeId()!=null)stores.add(source.storeId());
   }
  }
  return stores;
 }
 static Map<String,Object> audit(PayrollRepository.State b){return Map.of("companyId",b.company(),"month",b.month().toString(),"status",b.status(),"version",b.version(),"sourceFingerprint",b.calculation()==null?"":b.calculation().sourceFingerprint());}
 static List<Option> employees(PayrollSnapshot c){return c.lines().stream().map(l->new Option(l.employeeId(),l.employeeName()+" · "+l.employeeNo())).toList();}
}
