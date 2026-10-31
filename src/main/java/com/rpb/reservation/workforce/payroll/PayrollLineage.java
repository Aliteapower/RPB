package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Deterministic, minimal evidence over captured facts. Never reads live data or changes calculator rounding. */
final class PayrollLineage {
 private PayrollLineage(){}
 static List<String> issues(PayrollSources.CapturedLine line){
  var result=new LinkedHashSet<String>();var schedules=new HashSet<UUID>();var refs=line.sources();
  for(var ref:refs)if(!schedules.add(ref.sourceId()))result.add("SOURCE_DUPLICATE");
  for(int i=0;i<refs.size();i++)for(int j=i+1;j<refs.size();j++){
   var a=refs.get(i);var b=refs.get(j);if(a.sourceId().equals(b.sourceId()))continue;
   var x=a.evidence();var y=b.evidence();
   if(x!=null&&y!=null&&x.startAt()!=null&&x.endAt()!=null&&y.startAt()!=null&&y.endAt()!=null&&x.startAt().isBefore(y.endAt())&&y.startAt().isBefore(x.endAt()))result.add("SOURCE_ACTUAL_OVERLAP");
  }
  var consumed=new HashSet<String>();if(line.profile()!=null){checkItems(line.profile().rule().items(),"profile",line.profile().id().toString(),line.profile().version(),refs,result,consumed);checkItems(line.input().items(),"monthly_input",line.employee().id().toString(),line.input().version(),refs,result,consumed);}
  return List.copyOf(result);
 }
 private static void checkItems(List<Item> items,String origin,String source,long version,List<SourceRef> refs,Set<String> issues,Set<String> seen){
  for(int index=0;index<items.size();index++){
   var i=items.get(index);String id=i.itemId()==null?"legacy:"+index:i.itemId().toString();var s=i.source();
   String key=s==null?origin+":"+source+":"+version+":"+id:s.origin()+":"+s.sourceId()+":"+s.version()+":"+(s.eventId()==null?id:s.eventId())+":"+i.kind();
   if(!seen.add(key))issues.add("ITEM_SOURCE_DUPLICATE");
   if(s==null)continue;
   if(Set.of("approved_work","paid_leave").contains(s.origin())){
    var ref=refs.stream().filter(r->r.payable()&&r.sourceId().toString().equals(s.sourceId())&&r.sourceVersion()==s.version()).findFirst().orElse(null);
    if(ref==null||ref.evidence()==null||!"current".equals(ref.evidence().approvalState())||(s.eventId()!=null&&!ref.evidence().eventIds().contains(s.eventId())))issues.add("ITEM_SOURCE_INVALID");
    if((s.origin().equals("paid_leave")&&(ref==null||ref.paidLeaveSeconds()<=0||s.eventId()!=null))||(s.origin().equals("approved_work")&&i.kind().equals("deduction")&&s.eventId()==null))issues.add("ITEM_SOURCE_INVALID");
    if(i.kind().equals("deduction")&&s.eventId()!=null&&ref!=null&&ref.evidence()!=null&&ref.evidence().eventIds().contains(s.eventId())&&(s.independentReason()==null||s.independentReason().isBlank()))issues.add("ATTENDANCE_DEDUCTION_EXPLANATION_REQUIRED");
   }else if(!s.origin().equals(origin)||!s.sourceId().equals(source)||s.version()!=version)issues.add("ITEM_SOURCE_INVALID");
  }
 }
 static Hours hours(PayrollSources.CapturedLine s,PayCalculator.Calculation c){
  if(s.profile()!=null&&PayrollGeneration.fixed(s.profile().rule()))return new Hours(null,null,"not_applicable","fixed",null,null,null,null,null);
  if(s.profile()!=null&&PayrollGeneration.contract(s.profile().rule()))return new Hours(null,null,"not_applicable","contract",null,null,c==null?null:c.overtimeSeconds(),s.input().workingDays(),s.input().payableDays());
  var payable=s.sources().stream().filter(SourceRef::payable).toList();
  boolean evidence=payable.stream().allMatch(r->r.evidence()!=null),current=evidence&&payable.stream().allMatch(r->"current".equals(r.evidence().approvalState()));
  String state=!evidence||payable.isEmpty()?"unavailable":current?"current":payable.stream().anyMatch(r->"stale".equals(r.evidence().approvalState()))?"stale":"missing";
  Long actual=evidence&&!payable.isEmpty()&&payable.stream().allMatch(r->r.evidence().actualSeconds()!=null)?payable.stream().mapToLong(r->r.evidence().actualSeconds()).sum():null;
  Long approved=current&&!payable.isEmpty()&&payable.stream().allMatch(r->r.evidence().approvedSeconds()!=null)?payable.stream().mapToLong(r->r.evidence().approvedSeconds()).sum():null;
  String basis=s.profile()==null?"unknown":s.profile().rule().basis();boolean hourly=basis.equals("hourly");
  return new Hours(actual,approved,state,basis,hourly&&c!=null?c.normalSeconds():null,hourly&&c!=null?c.paidLeaveSeconds():null,hourly&&c!=null?c.overtimeSeconds():null,hourly?null:s.input().workingDays(),hourly?null:s.input().payableDays());
 }
 static List<Component> components(PayrollSources.CapturedLine s,YearMonth month,PayCalculator.Calculation c){
  if(c==null)return List.of();var out=new ArrayList<Component>();var r=s.profile().rule();var input=s.input();var raw=c.components();
  var sourceKeys=s.sources().stream().map(x->x.sourceId()+"/v"+x.sourceVersion()+":"+(x.payable()?"payable":"context")).toList();
  var rate=new BigDecimal(r.rate());var basicQuantity=r.basis().equals("hourly")?Long.toString(c.normalSeconds()+c.paidLeaveSeconds()):input.payableDays();String unit=r.basis().equals("hourly")?"seconds":"days";
  boolean fixed=PayrollGeneration.fixed(r),contract=PayrollGeneration.contract(r),manualUnits=!fixed&&!r.basis().equals("hourly")&&!PayrollGeneration.automatic(r);var basicKeys=manualUnits?List.of("profile:"+s.profile().id()+"/v"+s.profile().version()+":rate","monthly_input:"+s.employee().id()+"/v"+input.version()+":units"):sourceKeys;String basicFormula=fixed?"fixed_full_v1":contract?"contract_"+r.basis()+"_v1":r.basis()+"_basic_v1";String prorate=r.basis().equals("monthly")?input.payableDays()+"/"+input.workingDays():"none";
  BigDecimal basicNumerator=r.basis().equals("hourly")?rate.multiply(BigDecimal.valueOf(c.normalSeconds()+c.paidLeaveSeconds())):rate.multiply(new BigDecimal(input.payableDays()));BigDecimal basicDenominator=r.basis().equals("hourly")?new BigDecimal("3600"):r.basis().equals("monthly")?new BigDecimal(input.workingDays()):BigDecimal.ONE;
  out.add(component(raw.get(0),s,fixed?"profile":contract?"contract":manualUnits?"monthly_input":s.sources().stream().anyMatch(x->"arrangement".equals(x.sourceType()))?"arrangement":"approved_work","basic",month,basicFormula,fixed?"1":basicQuantity,fixed?"fixed_salary":unit,r.rate(),"1",fixed?"full":prorate,(contract?c.basicPay().toPlainString():ratio(basicNumerator,basicDenominator)),fixed?List.of(s.profile().id()+"/v"+s.profile().version()+":basic"):basicKeys));
  var otNum=rate.multiply(BigDecimal.valueOf(c.overtimeSeconds())).multiply(new BigDecimal(r.overtimeMultiplier()));var otDen=new BigDecimal("3600");
  if(r.basis().equals("monthly")){otNum=otNum.multiply(new BigDecimal("12"));otDen=otDen.multiply(new BigDecimal("52")).multiply(new BigDecimal("44"));}else if(r.basis().equals("daily"))otDen=otDen.multiply(new BigDecimal(r.dayHours()));
  out.add(component(raw.get(1),s,fixed?"profile":contract?"contract":"approved_work","overtime",month,(contract?"contract_":"")+r.basis()+"_overtime_v1",Long.toString(c.overtimeSeconds()),fixed?"not_applicable":"seconds",r.rate(),r.overtimeMultiplier(),"none",contract||s.compliance()!=null?c.overtimePay().toPlainString():ratio(otNum,otDen),sourceKeys));
  int offset=raw.size()-r.items().size()-input.items().size();
  for(int index=2;index<offset;index++){
   var extra=raw.get(index);out.add(component(extra,s,"contract","additional_regular",month,"part_time_additional_regular_v1",null,"seconds",r.rate(),"1","none",extra.amount().toPlainString(),sourceKeys));
  }
  for(boolean recurring:new boolean[]{true,false}){var items=recurring?r.items():input.items();for(int index=0;index<items.size();index++){
   var item=items.get(index);var value=raw.get(offset++);var source=item.source();String origin=source==null?(recurring?"profile":"monthly_input"):source.origin();String id=item.itemId()==null?"legacy:"+index:item.itemId().toString();
   String sourceId=source==null?(recurring?s.profile().id().toString():s.employee().id().toString()):source.sourceId();long version=source==null?(recurring?s.profile().version():input.version()):source.version();
   boolean prorated=recurring&&!PayrollGeneration.fixed(r)&&r.basis().equals("monthly")&&PayItemKinds.INCOME.contains(item.kind())&&!"full".equals(item.proration());
   String amount=prorated?ratio(new BigDecimal(item.amount()).multiply(new BigDecimal(input.payableDays())),new BigDecimal(input.workingDays())):item.amount();
   var keys=List.of(origin+":"+sourceId+"/v"+version+":"+id+(source==null||source.eventId()==null?"":":"+source.eventId()));
   if(item.kind().equals("ow_attendance"))amount=value.amount().toPlainString();
   var lineage=new Lineage(origin,sourceId,version,id,month.toString(),item.kind().equals("ow_attendance")?"attendance_"+Objects.toString(s.attendanceBonusStatus(),"pending")+"_v1":prorated?"recurring_payable_v1":"fixed_amount_v1","1","item",item.amount(),"1",prorated?input.payableDays()+"/"+input.workingDays():"full",amount,"HALF_UP_2",PayrollValues.money(value.amount()),keys);
   out.add(new Component(value.label(),value.kind(),PayrollValues.money(value.amount()),lineage));
  }}return List.copyOf(out);
 }
 private static String ratio(BigDecimal n,BigDecimal d){try{return n.divide(d).toPlainString();}catch(ArithmeticException e){return n.toPlainString()+"/"+d.toPlainString();}}
 private static Component component(PayCalculator.Component c,PayrollSources.CapturedLine s,String origin,String id,YearMonth month,String formula,String quantity,String unit,String rate,String multiplier,String proration,String raw,List<String> sources){
  return new Component(c.label(),c.kind(),PayrollValues.money(c.amount()),new Lineage(origin,origin.equals("monthly_input")?s.employee().id().toString():s.profile().id().toString(),origin.equals("monthly_input")?s.input().version():s.profile().version(),id,month.toString(),formula,quantity,unit,rate,multiplier,proration,raw,"HALF_UP_2",PayrollValues.money(c.amount()),sources));
 }
 static Component statutory(String label,String kind,String amount,String source,long version,YearMonth month,String formula,String quantity,String rate){return new Component(label,kind,amount,new Lineage("statutory",source,version,kind,month.toString(),formula,quantity,"SGD",rate,"1","none",amount,"policy",amount,List.of(source+"/v"+version+":"+kind)));}
 static List<Component> statutory(YearMonth month,com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy policy,com.rpb.reservation.workforce.statutory.StatutoryDtos.Result cpf,String gross){
  if(cpf==null||policy==null)return List.of();var trace=cpf.trace();var source=policy.id().toString();long version=policy.version();
  var ee=new Lineage("statutory",source,version,"employee_cpf",month.toString(),"cpf_"+(trace==null?"unknown":trace.method())+"_employee_v1",gross,"SGD",trace==null?null:trace.employeeRate(),"1","ow_ceiling:"+policy.config().owCeiling(),trace==null?null:trace.employeeRaw().toPlainString(),trace==null?"policy":trace.employeeRounding(),PayrollValues.money(cpf.employeeCpf()),List.of(cpf.rateCode(),source+"/v"+version));
  var er=new Lineage("statutory",source,version,"employer_cpf",month.toString(),"rounded_total_minus_rounded_employee_v1",gross,"SGD",trace==null?null:trace.employerRate(),"1","ow_ceiling:"+policy.config().owCeiling(),trace==null?null:trace.totalRaw().subtract(trace.employeeRaw()).toPlainString(),trace==null?"policy":trace.totalRounding()+"(total)-"+trace.employeeRounding()+"(employee)",PayrollValues.money(cpf.employerCpf()),List.of(cpf.rateCode(),source+"/v"+version));
  var sdl=new Lineage("statutory",source,version,"sdl",month.toString(),"sdl_clamped_individual_v1",gross,"SGD",policy.config().sdl().rate(),"1","min:"+policy.config().sdl().minimum()+";max:"+policy.config().sdl().maximum(),cpf.sdl().toPlainString(),"individual_precision;company:"+policy.config().companySdlRounding(),cpf.sdl().toPlainString(),List.of(source+"/v"+version));
  return List.of(new Component("Employee CPF","employee_cpf",PayrollValues.money(cpf.employeeCpf()),ee),new Component("Employer CPF","employer_cpf",PayrollValues.money(cpf.employerCpf()),er),new Component("SDL","sdl",cpf.sdl().toPlainString(),sdl));
 }
 static Item publicItem(Item item){return new Item(item.label(),item.kind(),item.amount(),item.proration(),item.itemId(),item.source()==null?null:new ItemSource(item.source().origin(),item.source().sourceId(),item.source().version(),item.source().eventId(),null));}
}
