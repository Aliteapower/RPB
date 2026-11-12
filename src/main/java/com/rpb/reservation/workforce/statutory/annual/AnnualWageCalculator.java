package com.rpb.reservation.workforce.statutory.annual;

import static com.rpb.reservation.workforce.statutory.annual.AnnualWageDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.statutory.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Pure chronological allocation and original-month integer liability reconstruction. */
public final class AnnualWageCalculator {
 private static final BigDecimal ZERO=BigDecimal.ZERO;
 public static String money(BigDecimal n){return n==null?null:n.setScale(2,RoundingMode.HALF_UP).toPlainString();}
 public static BigDecimal amount(String s){if(s==null||!s.matches("[0-9]{1,8}(\\.[0-9]{1,2})?"))throw WorkforceException.invalid();return new BigDecimal(s);}
 public Assessment assess(Context context){
  var issues=new LinkedHashSet<>(context.issues());var months=new TreeMap<String,MonthFact>();var events=new ArrayList<WageEvent>();var ids=new HashSet<UUID>();BigDecimal annualOW=ZERO,ceilingLimit=null;
  for(var f:context.months()){
   if(months.put(f.month(),f)!=null)throw WorkforceException.invalid();issues.addAll(f.issues());
   if(f.state().equals("unknown")||f.cpfOW()==null)issues.add("ANNUAL_WAGES_INCOMPLETE");else annualOW=annualOW.add(amount(f.cpfOW()));
   if(f.policy()!=null&&f.policy().config().aw()!=null){var cap=amount(f.policy().config().aw().annualWageCeiling());if(ceilingLimit!=null&&ceilingLimit.compareTo(cap)!=0)issues.add("ANNUAL_POLICY_CONFLICT");ceilingLimit=cap;}
   for(var event:f.events()){if(!ids.add(event.evidence().eventId()))issues.add("WAGE_EVENT_DUPLICATE");if(event.evidence().classification().equals("AW"))events.add(event);}
  }
  if(months.size()!=12)issues.add("ANNUAL_WAGES_INCOMPLETE");
  boolean foreign=context.months().stream().allMatch(f->f.cpf()!=null&&"foreign".equals(f.cpf().citizenship()));
  if(foreign)return new Assessment("not_applicable",context.fingerprint(),null,null,null,null,List.of(),List.of(),"0.00","0.00","0.00",false,List.of());
  boolean owOnlyWithoutAwCapability=ceilingLimit==null&&events.isEmpty()&&issues.isEmpty();
  if(ceilingLimit==null)issues.add("STATUTORY_CAPABILITY_UNSUPPORTED");
  if(!issues.isEmpty()&&!owOnlyWithoutAwCapability)return new Assessment("incomplete",context.fingerprint(),null,null,null,null,List.of(),List.of(),null,null,null,true,List.copyOf(issues));
  BigDecimal ceiling=ceilingLimit==null?ZERO:ceilingLimit.subtract(annualOW).max(ZERO),remaining=ceiling;
  events.sort(Comparator.comparing((WageEvent e)->e.evidence().contractualPayableDate()).thenComparing(e->e.evidence().eventId()));
  var allocations=new ArrayList<Allocation>();var raw=new HashMap<String,BigDecimal>();var eligible=new HashMap<String,BigDecimal>();
  for(var e:events){var a=amount(e.amount());var f=months.get(e.evidence().contributionMonth());if(f==null)throw WorkforceException.invalid();boolean exempt=f.cpf()!=null&&"foreign".equals(f.cpf().citizenship());var used=exempt?ZERO:a.min(remaining);remaining=remaining.subtract(used);allocations.add(new Allocation(e.evidence().eventId(),e.evidence().contributionMonth(),money(a),money(used),money(remaining),e.source()));raw.merge(f.month(),a,BigDecimal::add);eligible.merge(f.month(),used,BigDecimal::add);}
  var liabilities=new ArrayList<MonthLiability>();BigDecimal totalEe=ZERO,totalEr=ZERO;boolean deltasKnown=true,review=false;
  for(var f:months.values()){
   var rowIssues=new LinkedHashSet<String>();BigDecimal ee=null,er=null,eeDelta=null,erDelta=null;String rate=null;var aw=raw.getOrDefault(f.month(),ZERO);var awBase=eligible.getOrDefault(f.month(),ZERO);
   if(f.state().equals("known-zero")&&amount(f.rawOW()).signum()==0&&aw.signum()==0){ee=ZERO;er=ZERO;rate="NO_WAGES";}
   else if(f.policy()==null||f.cpf()==null)rowIssues.add("ORIGINAL_PERIOD_POLICY_MISSING");
   else try{var r=new StatutoryCalculator().calculateMonthly(YearMonth.parse(f.month()),f.cpf(),amount(f.rawOW()),aw,awBase,f.policy().config());ee=r.employeeCpf();er=r.employerCpf();rate=r.rateCode();}catch(WorkforceException ex){rowIssues.add(ex.code());}
   if(ee!=null&&aw.signum()==0){eeDelta=ZERO;erDelta=ZERO;}
   else if(ee!=null&&f.recognisedEmployeeCpf()!=null&&f.recognisedEmployerCpf()!=null){eeDelta=ee.subtract(amount(f.recognisedEmployeeCpf()));erDelta=er.subtract(amount(f.recognisedEmployerCpf()));totalEe=totalEe.add(eeDelta);totalEr=totalEr.add(erDelta);review|=eeDelta.signum()!=0||erDelta.signum()!=0;}else{deltasKnown=false;rowIssues.add("ORIGINAL_LIABILITY_AMBIGUOUS");}
   liabilities.add(new MonthLiability(f.month(),f.rawOW(),money(aw),money(amount(f.rawOW()).add(aw)),f.cpfOW(),money(awBase),money(ee),money(er),money(eeDelta),money(erDelta),rate,List.copyOf(rowIssues)));issues.addAll(rowIssues);
  }
  return new Assessment(issues.isEmpty()?"ready":"incomplete",context.fingerprint(),money(annualOW),owOnlyWithoutAwCapability?null:money(ceiling),money(ceiling.subtract(remaining)),owOnlyWithoutAwCapability?null:money(remaining),List.copyOf(allocations),List.copyOf(liabilities),deltasKnown?money(totalEe):null,deltasKnown?money(totalEr):null,deltasKnown?money(totalEe.add(totalEr)):null,review||!issues.isEmpty(),List.copyOf(issues));
 }
}
