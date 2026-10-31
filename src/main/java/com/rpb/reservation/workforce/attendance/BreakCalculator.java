package com.rpb.reservation.workforce.attendance;
import com.rpb.reservation.workforce.scheduling.rules.AttendanceRules;import com.rpb.reservation.workforce.scheduling.SchedulingDtos.Shift;import static com.rpb.reservation.workforce.attendance.AttendanceDtos.*;import com.rpb.reservation.workforce.domain.WorkforceException;import java.time.*;import java.util.*;
/** Interval arithmetic only. Rules, recorded facts and quota comparisons stay separate. */
public final class BreakCalculator {
 public record Summary(String ruleId,String name,String mode,String punchMode,boolean paid,long plannedSeconds,Long actualSeconds,long remainingSeconds,long overSeconds,long shortSeconds,boolean missing){}
 public record Result(long breakSeconds,long workSeconds,List<Summary> summary,List<String> issues){}
 public Result calculate(AttendanceRules rules,Instant plannedStart,Instant in,Instant out,List<Interval> actual){return calculate(rules,plannedStart,in,out,actual,true);}
 public List<Summary> summary(Shift shift,Instant plannedStart,Session session){if(shift.rules()==null||session==null)return List.of();Instant to=session.outAt();if(to==null)to=session.breaks().stream().map(Interval::end).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(session.inAt()).plusNanos(1);return calculate(shift.rules(),plannedStart,session.inAt(),to,session.breaks(),session.outAt()!=null).summary();}
 private Result calculate(AttendanceRules rules,Instant plannedStart,Instant in,Instant out,List<Interval> actual,boolean complete){
  if(in==null||out==null||!out.isAfter(in)||actual==null||actual.size()>100||actual.stream().anyMatch(b->b==null||b.start()==null||b.end()==null))throw WorkforceException.invalid();var sorted=new ArrayList<>(actual);sorted.sort(Comparator.comparing(b->b.start()==null?Instant.MIN:b.start()));Instant previous=in;var byRule=new HashMap<String,List<Interval>>();var punchable=rules.breaks().stream().filter(b->!b.punchMode().equals("none")).toList();
  for(var b:sorted){if(b==null||b.start()==null||b.end()==null||b.start().isBefore(previous)||b.end().isAfter(out)||!b.end().isAfter(b.start()))throw WorkforceException.invalid();String id=b.ruleId();if(id==null&&punchable.size()==1)id=punchable.getFirst().id();final String key=id;if(key==null||punchable.stream().noneMatch(r->r.id().equals(key)))throw WorkforceException.invalid();byRule.computeIfAbsent(key,k->new ArrayList<>()).add(b);previous=b.end();}
  var allRest=new ArrayList<Interval>();var unpaid=new ArrayList<Interval>();var summaries=new ArrayList<Summary>();var issues=new LinkedHashSet<String>();
  for(var b:rules.breaks()){
   var records=byRule.getOrDefault(b.id(),List.of());long actualSeconds=records.stream().mapToLong(i->seconds(i.start(),i.end())).sum();long planned=b.mode().equals("quota")?b.quotaMinutes()*60L:b.mode().equals("window")?(b.endOffset()-b.startOffset())*60L:0;long difference=actualSeconds-planned;boolean intersects=b.startOffset()==null||overlap(in,out,plannedStart.plusSeconds(b.startOffset()*60L),plannedStart.plusSeconds(b.endOffset()*60L))>0;boolean missing=complete&&b.punchMode().equals("required")&&records.isEmpty()&&intersects;
   if(missing)issues.add("BREAK_RECORD_MISSING");summaries.add(new Summary(b.id(),b.name(),b.mode(),b.punchMode(),b.paid(),planned,records.isEmpty()?null:actualSeconds,b.mode().equals("quota")?Math.max(0,planned-actualSeconds):0,planned>0&&!records.isEmpty()?Math.max(0,difference):0,complete&&planned>0&&!records.isEmpty()?Math.max(0,-difference):0,missing));
   List<Interval> effective;if(b.mode().equals("window")){Instant a=plannedStart.plusSeconds(b.startOffset()*60L),z=plannedStart.plusSeconds(b.endOffset()*60L);Instant x=in.isAfter(a)?in:a,y=out.isBefore(z)?out:z;effective=y.isAfter(x)?List.of(new Interval(x,y,b.id())):List.of();}else effective=records;
   allRest.addAll(effective);if(!b.paid())unpaid.addAll(effective);
  }
  return new Result(unionSeconds(allRest),Math.max(0,seconds(in,out)-unionSeconds(unpaid)),List.copyOf(summaries),List.copyOf(issues));
 }
 public static long seconds(Instant a,Instant b){return Math.max(0,Duration.between(a,b).getSeconds());}
 public static long overlap(Instant a,Instant b,Instant x,Instant y){return seconds(a.isAfter(x)?a:x,b.isBefore(y)?b:y);}
 public static long unionSeconds(List<Interval> input){if(input.isEmpty())return 0;var rows=input.stream().filter(i->i.end().isAfter(i.start())).sorted(Comparator.comparing(Interval::start)).toList();if(rows.isEmpty())return 0;Instant a=rows.getFirst().start(),b=rows.getFirst().end();long result=0;for(int i=1;i<rows.size();i++){var row=rows.get(i);if(!row.start().isAfter(b)){if(row.end().isAfter(b))b=row.end();}else{result+=seconds(a,b);a=row.start();b=row.end();}}return result+seconds(a,b);}
 /** Subtract a union of exclusions from a working range, retaining exact seconds. */
 public static List<Interval> subtract(Instant from,Instant to,List<Interval> excluded){var result=new ArrayList<Interval>();Instant cursor=from;for(var row:excluded.stream().sorted(Comparator.comparing(Interval::start)).toList()){Instant a=row.start().isAfter(from)?row.start():from,b=row.end().isBefore(to)?row.end():to;if(!b.isAfter(a)||!b.isAfter(cursor))continue;if(a.isAfter(cursor))result.add(new Interval(cursor,a));if(b.isAfter(cursor))cursor=b;}if(cursor.isBefore(to))result.add(new Interval(cursor,to));return result;}
}


