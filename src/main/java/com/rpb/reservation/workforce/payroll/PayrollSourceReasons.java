package com.rpb.reservation.workforce.payroll;
import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import static com.rpb.reservation.workforce.payroll.PayrollWorkbenchDtos.*;
import java.util.*;
/** Only differences that retained snapshots can prove receive a precise label. */
public final class PayrollSourceReasons {
 private PayrollSourceReasons(){}
 public static List<Reason> compare(PayrollSnapshot baseline,PayrollSources.Capture capture,PayrollIncremental.Cache cache,boolean changed){
  var reasons=new ArrayList<Reason>();
  if(baseline==null){reasons.add(reason(null,"baseline_unavailable",null,null,null));return List.copyOf(reasons);}
  if(cache!=null&&!PayrollIncremental.ENGINE_KEY.equals(cache.engineKey()))reasons.add(reason(null,"engine",null,cache.engineKey(),PayrollIncremental.ENGINE_KEY));
  if(!changed)return List.copyOf(reasons);
  if(policyChanged(baseline,capture))reasons.add(reason(null,"statutory_policy",capture.statutoryPolicy()==null?null:capture.statutoryPolicy().id().toString(),baseline.policyVersion(),capture.statutoryPolicy()==null?null:Long.toString(capture.statutoryPolicy().version())));
  var old=new LinkedHashMap<UUID,PayrollLine>();baseline.lines().forEach(l->old.put(l.employeeId(),l));
  for(var line:capture.lines()){
   var id=line.employee().id();var before=old.remove(id);
   if(before==null){reasons.add(reason(id,"added",id.toString(),null,null));continue;}
   if(!Objects.equals(before.employeeName(),line.employee().name())||!Objects.equals(before.employeeNo(),line.employee().employeeNo()))reasons.add(reason(id,"employee",id.toString(),null,null));
   if(!Objects.equals(before.profile(),line.profile()))reasons.add(reason(id,"profile",line.profile()==null?null:line.profile().id().toString(),before.profile()==null?null:Long.toString(before.profile().version()),line.profile()==null?null:Long.toString(line.profile().version())));
   if(!Objects.equals(before.governance(),line.governance()))reasons.add(reason(id,"governance",id.toString(),before.governance()==null?null:before.governance().toString(),line.governance()==null?null:line.governance().toString()));
   if(inputChanged(before.input(),line.input()))reasons.add(reason(id,"monthly_input",id.toString(),Long.toString(before.input().version()),Long.toString(line.input().version())));
   var refs=new HashMap<UUID,SourceRef>();before.sources().forEach(s->refs.put(s.sourceId(),s));
   for(var source:line.sources()){
    var prev=refs.remove(source.sourceId());if(Objects.equals(prev,source))continue;
    if(prev==null){reasons.add(sourceReason(id,"source",source,null,source.sourceFingerprint()));continue;}
    for(String kind:sourceKinds(prev,source))reasons.add(sourceReason(id,kind,source,"approval".equals(kind)?Long.toString(prev.sourceVersion()):prev.sourceFingerprint(),"approval".equals(kind)?Long.toString(source.sourceVersion()):source.sourceFingerprint()));
   }
   refs.values().forEach(s->reasons.add(reason(id,"source",s.sourceId().toString(),s.sourceFingerprint(),null)));
  }
  old.keySet().forEach(id->reasons.add(reason(id,"removed",id.toString(),null,null)));
  if(reasons.isEmpty())reasons.add(reason(null,"source",null,baseline.sourceFingerprint(),capture.fingerprint()));
  return List.copyOf(reasons);
 }
 private static boolean policyChanged(PayrollSnapshot baseline,PayrollSources.Capture capture){
  var current=capture.statutoryPolicy();return policyChanged(baseline.policyVersion(),baseline.statutoryPolicy(),current==null?null:current.id()+"/v"+current.version(),current);
 }
 static boolean policyChanged(PayrollSnapshot before,PayrollSnapshot after){return policyChanged(before.policyVersion(),before.statutoryPolicy(),after.policyVersion(),after.statutoryPolicy());}
 private static boolean policyChanged(String old,com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy before,String current,com.rpb.reservation.workforce.statutory.StatutoryPolicies.Policy after){
  if(before!=null&&after!=null)return !Objects.equals(before,after);
  // Missing full evidence is not a change. Known UUID/version identities remain comparable.
  String oldIdentity=before==null?old:before.id()+"/v"+before.version();
  String currentIdentity=after==null?current:after.id()+"/v"+after.version();
  return knownPolicy(oldIdentity)&&knownPolicy(currentIdentity)&&!oldIdentity.equals(currentIdentity);
 }
 private static boolean knownPolicy(String value){return value!=null&&value.matches("[a-fA-F0-9-]{36}/v[0-9]+");}
 static boolean inputChanged(LineInput before,LineInput after){return before!=null&&after!=null&&before.version()!=after.version();}
 static List<String> sourceKinds(SourceRef before,SourceRef after){
  if(Objects.equals(before,after))return List.of();var kinds=new ArrayList<String>();
  if(before.sourceVersion()!=after.sourceVersion()||(before.evidence()!=null&&after.evidence()!=null&&!Objects.equals(state(before),state(after))))kinds.add("approval");
  if(before.evidence()!=null&&after.evidence()!=null&&(!Objects.equals(before.evidence().actualSeconds(),after.evidence().actualSeconds())||!Objects.equals(before.evidence().startAt(),after.evidence().startAt())||!Objects.equals(before.evidence().endAt(),after.evidence().endAt())))kinds.add("attendance");
  if("current".equals(state(before))&&"current".equals(state(after))&&(before.paidLeaveSeconds()!=after.paidLeaveSeconds()||before.unpaidLeaveSeconds()!=after.unpaidLeaveSeconds()))kinds.add("leave");
  return kinds.isEmpty()?List.of("source"):List.copyOf(kinds);
 }
 private static Reason sourceReason(UUID employee,String kind,SourceRef ref,String before,String after){return new Reason(employee,kind,ref.sourceId().toString(),shorten(before),shorten(after),ref.payable()?"employee":"week_context","attendance",ref.storeId(),ref.workDate());}
 private static String state(SourceRef ref){return ref.evidence()==null?null:ref.evidence().approvalState();}
 private static Reason reason(UUID employee,String kind,String source,String before,String after){return new Reason(employee,kind,source,shorten(before),shorten(after),employee==null?"company":"employee",switch(kind){case "profile"->"profile";case "monthly_input"->"input";case "approval","attendance","leave"->"approval";case "statutory_policy"->"policy";default->"review";},null,null);}
 private static String shorten(String s){return s!=null&&s.length()>64?s.substring(0,16):s;}
}
