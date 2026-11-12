package com.rpb.reservation.workforce.scheduling.rules;
import com.fasterxml.jackson.annotation.JsonInclude;import com.rpb.reservation.workforce.domain.WorkforceException;import java.util.*;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttendanceRules(int version,List<BreakRule> breaks,int lateGraceMinutes,int earlyGraceMinutes,int breakGraceMinutes,String graceMode,List<AnomalyRule> anomalies,UUID sourceTemplateId,Long sourceTemplateVersion) {
 public record BreakRule(String id,String name,String mode,Integer startOffset,Integer endOffset,Integer quotaMinutes,String punchMode,boolean paid){}
 public record AnomalyRule(String code,String name,boolean enabled,boolean affectsBonus){}
 public static final Set<String> CODES=Set.of("LATE","EARLY","MISSING","ABSENCE","BREAK_OVER","BREAK_SHORT","BREAK_MISSING","LEAVE");
 public AnomalyRule rule(String code){return anomalies.stream().filter(a->a.code().equals(code)&&a.enabled()).findFirst().orElse(null);}
 public static void validate(AttendanceRules r,long duration){
  if(r==null||r.version()!=1||r.breaks()==null||r.breaks().size()>12||r.anomalies()==null||r.anomalies().size()>CODES.size()||!Set.of("full","excess").contains(r.graceMode()==null?"":r.graceMode())||r.lateGraceMinutes()<0||r.lateGraceMinutes()>1440||r.earlyGraceMinutes()<0||r.earlyGraceMinutes()>1440||r.breakGraceMinutes()<0||r.breakGraceMinutes()>1440||duration<1||duration>1440||(r.sourceTemplateId()==null)!=(r.sourceTemplateVersion()==null)||r.sourceTemplateVersion()!=null&&r.sourceTemplateVersion()<1)throw WorkforceException.invalid();
  var ids=new HashSet<String>();var windows=new ArrayList<BreakRule>();for(var b:r.breaks()){
   if(b==null||b.id()==null||!b.id().matches("[A-Za-z0-9_-]{1,64}")||!ids.add(b.id())||b.name()==null||b.name().isBlank()||b.name().length()>80||!Set.of("window","actual","quota").contains(b.mode()==null?"":b.mode())||!Set.of("none","optional","required").contains(b.punchMode()==null?"":b.punchMode())||(b.startOffset()==null)!=(b.endOffset()==null)||b.startOffset()!=null&&(b.startOffset()<0||b.endOffset()<=b.startOffset()||b.endOffset()>duration)||b.mode().equals("window")&&b.startOffset()==null||!b.mode().equals("window")&&b.punchMode().equals("none")||b.mode().equals("quota")&&(b.quotaMinutes()==null||b.quotaMinutes()<1||b.quotaMinutes()>duration)||b.quotaMinutes()!=null&&(b.quotaMinutes()<1||b.quotaMinutes()>1440))throw WorkforceException.invalid();if(b.startOffset()!=null)windows.add(b);
  }windows.sort(Comparator.comparing(BreakRule::startOffset));for(int i=1;i<windows.size();i++)if(windows.get(i).startOffset()<windows.get(i-1).endOffset())throw WorkforceException.invalid();var codes=new HashSet<String>();for(var a:r.anomalies())if(a==null||a.code()==null||!CODES.contains(a.code())||!codes.add(a.code())||a.name()==null||a.name().isBlank()||a.name().length()>80)throw WorkforceException.invalid();
 }
}

