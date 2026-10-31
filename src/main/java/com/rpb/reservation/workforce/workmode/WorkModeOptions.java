package com.rpb.reservation.workforce.workmode;

import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;

/** Pure permission selection. Null collections are the historic singleton, never an expansion. */
public final class WorkModeOptions {
 private WorkModeOptions(){}
 public static List<String> modes(String mode,List<String> values){
  WorkModeRules.mode(mode);
  if(values==null)return List.of(mode);
  if(values.isEmpty()||values.size()>3||values.stream().anyMatch(Objects::isNull)||new HashSet<>(values).size()!=values.size()||!values.contains(mode))throw WorkforceException.invalid();
  values.forEach(WorkModeRules::mode);
  if(values.size()>1&&(values.contains("legacy")||values.contains("free")))throw WorkforceException.invalid();
  return values.stream().sorted().toList();
 }
 public static List<String> updated(String mode,List<String> requested,String previousMode,List<String> previous){
  return requested==null?(Objects.equals(mode,previousMode)?previous:null):modes(mode,requested);
 }
 public static ClockPolicy select(ClockPolicy policy,String requested){
  String mode=requested==null?(policy==null?"legacy":policy.mode()):WorkModeRules.mode(requested);
  if(policy==null){if(!mode.equals("legacy"))throw forbidden();return null;}
  if(policy.options()==null){if(!policy.mode().equals(mode))throw forbidden();return policy;}
  var selected=policy.options().stream().filter(p->p.mode().equals(mode)).findFirst().orElseThrow(WorkModeOptions::forbidden);
  return new ClockPolicy(mode,policy.assignmentId(),policy.assignmentVersion(),selected.point(),selected.addressId(),selected.addressVersion(),selected.siteVersion());
 }
 public static ClockPolicy geometryFree(ClockPolicy p){
  return p==null?null:new ClockPolicy(p.mode(),p.assignmentId(),p.assignmentVersion(),null,null,0,null,p.options()==null?null:p.options().stream().map(o->new ClockOption(o.mode(),null,null,0,null)).toList());
 }
 private static WorkforceException forbidden(){return new WorkforceException("WORKFORCE_CLOCK_MODE_FORBIDDEN",403);}
}
