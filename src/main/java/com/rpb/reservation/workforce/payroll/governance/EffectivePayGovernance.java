package com.rpb.reservation.workforce.payroll.governance;

import static com.rpb.reservation.workforce.payroll.PayrollDtos.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.payroll.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Exact server evidence at both write and capture boundaries; no actor/boolean bypass. */
public final class EffectivePayGovernance {
 private final JdbcTemplate jdbc;private final WorkforceJson json;
 private static final ObjectMapper MAPPER=new ObjectMapper().findAndRegisterModules();
 public EffectivePayGovernance(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=Objects.requireNonNull(jdbc);this.json=Objects.requireNonNull(json);}
 public record Evidence(String kind,UUID subjectId,long version,UUID requestId,Long revision,String hash,UUID evidenceId,UUID sourceId,Long sourceVersion){public Evidence(String kind,UUID subjectId,long version,UUID requestId,Long revision,String hash){this(kind,subjectId,version,requestId,revision,hash,null,null,null);}}
 public record ResolvedInput(LineInput input,Evidence evidence,String issue){}
 public PayChangeDtos.Request assumption(UUID tenant,UUID company,YearMonth month,PayrollSources.Assumptions a){
  var r=new PayChangeRepository(jdbc,json).find(tenant,a.requestId()).orElseThrow(WorkforceException::missing);
  if(!r.companyId().equals(company)||r.revision()!=a.revision()||!r.hash().equals(a.hash())||r.employeeId()==null)throw WorkforceException.conflict();
  var expected=r.content().profile()==null?List.<PayProfile>of():List.of(r.content().profile());var overrides=new HashMap<UUID,LineInput>();for(var m:r.content().months())if(m.month().equals(month.toString()))overrides.put(r.employeeId(),m.after());
  if(!expected.equals(a.profiles())||!overrides.equals(a.monthlyOverrides()))throw WorkforceException.conflict();return r;
 }
 private WorkforceException required(){return new WorkforceException("WORKFORCE_PAYROLL_APPROVAL_REQUIRED",409);}
 private boolean exact(String raw,String kind,Object value){return switch(kind){case "template"->Objects.equals(json.read(raw,SalaryTemplate.class),value);case "profile"->Objects.equals(json.read(raw,PayProfile.class),value);default->Objects.equals(json.read(raw,LineInput.class),value);};}
 private record Candidate(PayChangeDtos.Proof proof,String result,PayChangeDtos.Content content,PayChangeDtos.Request request){}
 private Candidate candidate(java.sql.ResultSet r)throws java.sql.SQLException{return new Candidate(json.read(r.getString(1),PayChangeDtos.Proof.class),r.getString(2),json.read(r.getString(3),PayChangeDtos.Content.class),json.read(r.getString(4),PayChangeDtos.Request.class));}
 private record RetainedEvidence(Evidence evidence,String result){}
 /** One company/month read context: capture never performs one proof query per employee. */
 public final class Context {
  private final List<Candidate> candidates;private final List<RetainedEvidence> retained;private final Map<UUID,PayChangeDtos.ApprovedMonth> approved;private final Map<UUID,ResolvedInput> legacy;
  private Context(List<Candidate> candidates,List<RetainedEvidence> retained,Map<UUID,PayChangeDtos.ApprovedMonth> approved,Map<UUID,ResolvedInput> legacy){this.candidates=candidates;this.retained=retained;this.approved=approved;this.legacy=legacy;}
  private Evidence find(String kind,UUID id,String month,long version,Object value){for(var c:candidates){var p=c.proof();if(p.kind().equals(kind)&&p.subjectId().equals(id)&&Objects.equals(p.month(),month)&&p.version()==version&&exact(c.result(),kind,value)&&approvedResult(c,kind,month,value))return new Evidence("approved",id,version,p.requestId(),p.revision(),p.hash());}for(var r:retained)if(kind.equals("profile")&&r.evidence().subjectId().equals(id)&&r.evidence().version()==version&&exact(r.result(),kind,value))return r.evidence();return null;}
  public Evidence profile(PayProfile p){return find("profile",p.id(),null,p.version(),p);}
  public ResolvedInput monthly(UUID employee){var a=approved.get(employee);if(a!=null){var e=find("monthly",employee,a.month(),a.version(),a.input());return new ResolvedInput(a.input(),e,e==null?"WORKFORCE_PAYROLL_GOVERNANCE_PROOF_REQUIRED":null);}return legacy.getOrDefault(employee,new ResolvedInput(PayrollSources.defaultInput(employee),new Evidence("default",employee,0,null,null,null),null));}
  public ResolvedInput input(UUID employee,LineInput raw){return overlay(monthly(employee),raw);}
 }
 public Context context(UUID tenant,UUID company,YearMonth month){
  var candidates=jdbc.query("""
   select p.snapshot,p.result,v.content,r.snapshot from workforce_pay_change_proofs p
   join workforce_pay_change_requests r on r.id=p.request_id and r.tenant_id=p.tenant_id and r.company_id=p.company_id
   join workforce_pay_change_revisions v on v.request_id=p.request_id and v.revision=p.revision and v.tenant_id=p.tenant_id and v.company_id=p.company_id
   where p.tenant_id=? and p.company_id=? and r.status='approved' and r.revision=p.revision and r.hash=p.hash and v.hash=p.hash
   and ((p.kind='monthly' and p.period_month=?) or (p.kind='profile' and exists(select 1 from workforce_pay_profiles f where f.tenant_id=p.tenant_id and f.company_id=p.company_id and f.id=p.subject_id and f.version=p.version)))
   """,(r,n)->candidate(r),tenant,company,month.toString());
  var retained=jdbc.query("""
   select e.kind,e.subject_id,e.version,e.id,e.source_id,e.result,e.source_version from workforce_pay_effective_evidence e
   join workforce_pay_profiles f on f.tenant_id=e.tenant_id and f.company_id=e.company_id and f.id=e.subject_id and f.version=e.version
   where e.tenant_id=? and e.company_id=? and e.subject_kind='profile'
   """,(r,n)->new RetainedEvidence(new Evidence(r.getString(1),r.getObject(2,UUID.class),r.getLong(3),null,null,null,r.getObject(4,UUID.class),r.getObject(5,UUID.class),r.getObject(7,Long.class)),r.getString(6)),tenant,company);
  var approved=new HashMap<UUID,PayChangeDtos.ApprovedMonth>();jdbc.query("select distinct on(employee_id) snapshot from workforce_pay_approved_months where tenant_id=? and company_id=? and period_month=? order by employee_id,version desc",(r,n)->json.read(r.getString(1),PayChangeDtos.ApprovedMonth.class),tenant,company,month.atDay(1)).forEach(a->approved.put(a.employeeId(),a));
  var legacy=new HashMap<UUID,ResolvedInput>();jdbc.query("select employee_id,state,result,batch_id from workforce_pay_legacy_months where tenant_id=? and company_id=? and period_month=?",r->{var employee=r.getObject(1,UUID.class);if(r.getString(2).equals("ambiguous"))legacy.put(employee,new ResolvedInput(PayrollSources.defaultInput(employee),null,"WORKFORCE_PAYROLL_INPUT_AMBIGUOUS"));else{var input=json.read(r.getString(3),LineInput.class);legacy.put(employee,new ResolvedInput(input,new Evidence("legacy-valid",employee,input.version(),null,null,null,null,r.getObject(4,UUID.class),null),null));}},tenant,company,month.atDay(1));return new Context(candidates,retained,approved,legacy);
 }
 public Evidence evidence(UUID tenant,UUID company,String kind,UUID id,String month,long version,Object value){
  var approved=jdbc.query("""
   select p.snapshot,p.result,v.content,r.snapshot request_snapshot from workforce_pay_change_proofs p
   join workforce_pay_change_requests r on r.id=p.request_id and r.tenant_id=p.tenant_id and r.company_id=p.company_id
   join workforce_pay_change_revisions v on v.request_id=p.request_id and v.revision=p.revision and v.tenant_id=p.tenant_id and v.company_id=p.company_id
   where p.tenant_id=? and p.company_id=? and p.kind=? and p.subject_id=? and p.period_month=? and p.version=?
   and r.status='approved' and r.revision=p.revision and r.hash=p.hash and v.hash=p.hash
   order by p.created_at desc
   """,(r,n)->new Candidate(json.read(r.getString(1),PayChangeDtos.Proof.class),r.getString(2),json.read(r.getString(3),PayChangeDtos.Content.class),json.read(r.getString(4),PayChangeDtos.Request.class)),tenant,company,kind,id,month==null?"":month,version);
  for(var candidate:approved){if(!exact(candidate.result(),kind,value)||!approvedResult(candidate,kind,month,value))continue;var p=candidate.proof();return new Evidence("approved",id,version,p.requestId(),p.revision(),p.hash());}
  record Retained(String kind,UUID id,UUID source,String result,Long sourceVersion){}
  var retained=jdbc.query("select kind,id,source_id,result,source_version from workforce_pay_effective_evidence where tenant_id=? and company_id=? and subject_kind=? and subject_id=? and period_month=? and version=? order by created_at desc",(r,n)->new Retained(r.getString(1),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4),r.getObject(5,Long.class)),tenant,company,kind,id,month==null?"":month,version);
  for(var r:retained)if(exact(r.result(),kind,value))return new Evidence(r.kind(),id,version,null,null,null,r.id(),r.source(),r.sourceVersion());return null;
 }
 private boolean approvedResult(Candidate c,String kind,String month,Object value){
  if(kind.equals("profile"))return Objects.equals(c.content().profile(),value);
  if(kind.equals("monthly"))return c.content().months().stream().anyMatch(m->m.month().equals(month)&&m.after().equals(value));
  if(!kind.equals("template")||c.content().adoption()==null||!(value instanceof SalaryTemplate t))return false;
  var base=c.content().adoption();var target=c.content().upgrade()==null?null:c.content().upgrade().targetRelease();var source=t.governanceSource();return source!=null&&source.masterId().equals(base.masterId())&&source.releaseVersion()==(target==null?base.releaseVersion():target.version())&&source.storeId().equals(base.storeId())&&t.companyId().equals(base.companyId())&&t.name().equals(target==null?base.name():target.name())&&t.rule().equals(c.content().derivedRule())&&t.enabled()&&t.version()==1;
 }
 private void record(UUID tenant,UUID company,String kind,String subjectKind,UUID id,String month,long version,UUID source,Object result){record(tenant,company,kind,subjectKind,id,month,version,source,null,null,result);}
 private void record(UUID tenant,UUID company,String kind,String subjectKind,UUID id,String month,long version,UUID source,Long sourceVersion,Object sourceSnapshot,Object result){
  jdbc.update("insert into workforce_pay_effective_evidence(id,tenant_id,company_id,kind,subject_kind,subject_id,period_month,version,source_id,source_version,source_snapshot,result) values(?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb) on conflict(tenant_id,company_id,kind,subject_kind,subject_id,period_month,version) do nothing",UUID.randomUUID(),tenant,company,kind,subjectKind,id,month==null?"":month,version,source,sourceVersion,sourceSnapshot==null?null:json.write(sourceSnapshot),json.write(result));
 }
 public void assertTemplate(UUID tenant,SalaryTemplate t){if(evidence(tenant,t.companyId(),"template",t.id(),null,t.version(),t)==null)throw required();}
 public void assertProfile(UUID tenant,PayProfile p){if(evidence(tenant,p.companyId(),"profile",p.id(),null,p.version(),p)==null)throw required();assertDeclaration(tenant,p);var templates=jdbc.query("select snapshot from workforce_pay_templates where tenant_id=? and id=?",(r,n)->json.read(r.getString(1),SalaryTemplate.class),tenant,p.templateId());if(templates.isEmpty())throw required();assertEconomicDate(tenant,templates.getFirst(),p.effectiveFrom());}
 /** Called only by the publication enable service after its permission/scope and command checks. */
 public void adoption(UUID tenant,SalaryTemplate template,TemplatePublicationDtos.Adoption adoption){
  var source=template.governanceSource();if(source==null||!source.adoptionId().equals(adoption.id())||!template.id().equals(adoption.materializedTemplateId())||!template.companyId().equals(adoption.companyId())||!source.storeId().equals(adoption.storeId())||!source.masterId().equals(adoption.masterId())||source.releaseVersion()!=adoption.releaseVersion()||!template.rule().equals(adoption.rule()))throw required();
  var releases=jdbc.query("select snapshot from workforce_pay_template_releases where tenant_id=? and master_id=? and version=?",(r,n)->json.read(r.getString(1),TemplatePublicationDtos.Release.class),tenant,source.masterId(),source.releaseVersion());
  if(releases.isEmpty()||!releases.getFirst().rule().equals(template.rule())||!releases.getFirst().companyIds().contains(template.companyId()))throw required();
  record(tenant,template.companyId(),"direct-adoption","template",template.id(),null,template.version(),adoption.id(),adoption.storeVersion(),adoption,template);
 }
 public void directProfile(UUID tenant,UUID store,PayProfile next){
  if(evidence(tenant,next.companyId(),"profile",next.id(),null,next.version(),next)!=null)return;
  var history=jdbc.query("select snapshot from workforce_pay_profiles where tenant_id=? and company_id=? and employee_id=? order by effective_from,id",(r,n)->json.read(r.getString(1),PayProfile.class),tenant,next.companyId(),next.employeeId());
  PayProfile before=history.stream().filter(p->!p.effectiveFrom().isAfter(next.effectiveFrom())).max(Comparator.comparing(PayProfile::effectiveFrom)).orElse(history.isEmpty()?null:history.getFirst());
  if(before!=null){if(evidence(tenant,before.companyId(),"profile",before.id(),null,before.version(),before)==null||!economic(before).equals(economic(next)))throw required();record(tenant,next.companyId(),"economic-unchanged","profile",next.id(),null,next.version(),before.id(),before.version(),before,next);return;}
  var templates=jdbc.query("select snapshot from workforce_pay_templates where tenant_id=? and id=? and company_id=?",(r,n)->json.read(r.getString(1),SalaryTemplate.class),tenant,next.templateId(),next.companyId());
  if(templates.isEmpty())throw required();var t=templates.getFirst();var source=t.governanceSource();
  if(source==null||!store.equals(source.storeId())||!t.enabled()||t.version()!=next.templateVersion()||!ruleEconomic(t.rule()).equals(ruleEconomic(next.rule())))throw required();
  var adoptions=jdbc.query("select snapshot from workforce_pay_template_adoptions a where a.tenant_id=? and a.id=? and a.store_id=? and a.company_id=? and a.materialized_template_id=?",(r,n)->json.read(r.getString(1),TemplatePublicationDtos.Adoption.class),tenant,source.adoptionId(),store,next.companyId(),t.id());
  if(adoptions.isEmpty())throw required();assertTemplate(tenant,t);assertEconomicDate(tenant,t,next.effectiveFrom());
  if(next.cpf().citizenship().equals("spr")&&!next.cpf().prMode().equals("GG"))throw required();
  if(next.cdac()!=null&&(next.cdac().status().equals("exempt")||next.cdac().status().equals("manual")&&t.rule().items().stream().noneMatch(i->i.kind().equals("cdac"))))throw required();
  assertDeclaration(tenant,next);
  record(tenant,next.companyId(),"direct-adoption","profile",next.id(),null,next.version(),source.adoptionId(),adoptions.getFirst().storeVersion(),adoptions.getFirst(),next);
 }
 private void assertDeclaration(UUID tenant,PayProfile p){
  if(p.cpf().citizenship().equals("foreign")||p.cdac()!=null||p.rule().items().stream().anyMatch(i->i.kind().equals("cdac")))return;
  // Only an exact installation record can retain the old null-CDAC semantics.
  boolean legacy=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workforce_pay_effective_evidence e where e.tenant_id=? and e.company_id=? and e.kind='legacy-valid' and e.subject_kind='profile' and e.result->>'employeeId'=? and coalesce(e.result->'cdac','null'::jsonb)='null'::jsonb and e.result->'cpf'->>'citizenship' in ('citizen','spr'))",Boolean.class,tenant,p.companyId(),p.employeeId().toString()));
  if(!legacy)throw new WorkforceException("WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED",409);
 }
 public String declarationIssue(UUID tenant,PayProfile p){try{assertDeclaration(tenant,p);return null;}catch(WorkforceException e){if(e.code().equals("WORKFORCE_PAYROLL_CDAC_DECLARATION_REQUIRED"))return e.code();throw e;}}
 public void assertEconomicDate(UUID tenant,SalaryTemplate t,LocalDate date){
  var dates=jdbc.query("select r.effective_date from workforce_pay_change_proofs p join workforce_pay_change_requests r on r.id=p.request_id and r.tenant_id=p.tenant_id where p.tenant_id=? and p.kind='template' and p.subject_id=? and r.status='approved'",(r,n)->r.getObject(1,LocalDate.class),tenant,t.id());
  if(dates.stream().anyMatch(d->date.isBefore(d)))throw new WorkforceException("WORKFORCE_PAYROLL_TEMPLATE_NOT_EFFECTIVE",409);
 }
 public ResolvedInput monthly(UUID tenant,UUID company,UUID employee,YearMonth month){
  var approved=new PayChangeRepository(jdbc,json).approvedMonthly(tenant,company,employee,month);
  if(approved.isPresent()){var a=approved.get();var e=evidence(tenant,company,"monthly",employee,month.toString(),a.version(),a.input());return new ResolvedInput(a.input(),e,e==null?"WORKFORCE_PAYROLL_GOVERNANCE_PROOF_REQUIRED":null);}
  var legacy=jdbc.query("select state,result,batch_id from workforce_pay_legacy_months where tenant_id=? and company_id=? and employee_id=? and period_month=?",(r,n)->r.getString(1).equals("ambiguous")?new ResolvedInput(PayrollSources.defaultInput(employee),null,"WORKFORCE_PAYROLL_INPUT_AMBIGUOUS"):new ResolvedInput(json.read(r.getString(2),LineInput.class),new Evidence("legacy-valid",employee,json.read(r.getString(2),LineInput.class).version(),null,null,null,null,r.getObject(3,UUID.class),null),null),tenant,company,employee,month.atDay(1));
  return legacy.isEmpty()?new ResolvedInput(PayrollSources.defaultInput(employee),new Evidence("default",employee,0,null,null,null),null):legacy.getFirst();
 }
 public void assertInput(UUID tenant,UUID company,UUID employee,YearMonth month,LineInput input){var effective=monthly(tenant,company,employee,month);if(effective.issue()!=null||!inputEconomic(effective.input()).equals(inputEconomic(input)))throw required();}
 /** Only remarks and review flags may overlay effective economic input, never raw amounts. */
 public ResolvedInput input(UUID tenant,UUID company,UUID employee,YearMonth month,LineInput raw){
  return overlay(monthly(tenant,company,employee,month),raw);
 }
 private ResolvedInput overlay(ResolvedInput effective,LineInput raw){if(raw==null)return effective;
  if(inputEconomic(raw).equals(inputEconomic(effective.input()))){if(raw.version()>effective.input().version()||raw.equals(effective.input()))return new ResolvedInput(raw,effective.evidence(),effective.issue());return effective;}
  // Approved/stable effective input wins over obsolete batch amounts; an unproved new raw amount blocks.
  if(effective.evidence()==null||!effective.evidence().kind().equals("default"))return effective;
  return new ResolvedInput(effective.input(),effective.evidence(),"WORKFORCE_PAYROLL_GOVERNANCE_PROOF_REQUIRED");
 }
 public void unchangedTemplate(UUID tenant,SalaryTemplate next){var prior=jdbc.query("select snapshot from workforce_pay_templates where tenant_id=? and id=?",(r,n)->json.read(r.getString(1),SalaryTemplate.class),tenant,next.id());if(prior.isEmpty()||!ruleEconomic(prior.getFirst().rule()).equals(ruleEconomic(next.rule()))||evidence(tenant,next.companyId(),"template",next.id(),null,prior.getFirst().version(),prior.getFirst())==null)throw required();record(tenant,next.companyId(),"economic-unchanged","template",next.id(),null,next.version(),next.id(),prior.getFirst().version(),prior.getFirst(),next);}
 public static JsonNode economic(PayProfile p){var n=MAPPER.valueToTree(p);((ObjectNode)n).remove(List.of("id","companyId","employeeId","employeeName","effectiveFrom","templateId","templateVersion","version","payment","origin"));stripItemIdentities(n);return n;}
 public static JsonNode ruleEconomic(Rule rule){var n=MAPPER.valueToTree(rule);stripItemIdentities(n);return n;}
 public static JsonNode inputEconomic(LineInput input){var n=MAPPER.valueToTree(input);((ObjectNode)n).remove(List.of("employeeId","version","note","unitsReviewed","specialDaysReviewed","generation"));stripItemIdentities(n);return n;}
 private static void stripItemIdentities(JsonNode node){if(node.isObject()){var items=node.get("items");if(items!=null&&items.isArray())for(var item:items)if(item.isObject())((ObjectNode)item).remove(List.of("itemId","label"));for(var key:List.of("rate","dayHours","weekHours","overtimeMultiplier","amount","workingDays","payableDays")){var value=node.get(key);if(value!=null&&value.isTextual())try{((ObjectNode)node).put(key,new java.math.BigDecimal(value.asText()).stripTrailingZeros().toPlainString());}catch(NumberFormatException ignored){/* full validation remains at the boundary */}}node.elements().forEachRemaining(EffectivePayGovernance::stripItemIdentities);}else if(node.isArray())node.elements().forEachRemaining(EffectivePayGovernance::stripItemIdentities);}
}
